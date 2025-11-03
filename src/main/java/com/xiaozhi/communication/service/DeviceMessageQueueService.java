package com.xiaozhi.communication.service;

import com.xiaozhi.communication.common.SessionManager;
import com.xiaozhi.communication.domain.DeviceMessage;
import com.xiaozhi.entity.SysDevice;
import com.xiaozhi.event.DeviceReconnectedEvent;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 设备消息等待队列服务
 * 负责管理离线设备的消息队列，当设备重新连接时发送待发送消息
 * 
 * @author Joey
 */
@Service
public class DeviceMessageQueueService {
    private static final Logger logger = LoggerFactory.getLogger(DeviceMessageQueueService.class);

    @Resource
    private SessionManager sessionManager;

    // 存储每个设备ID对应的待发送消息队列
    private final ConcurrentHashMap<String, List<DeviceMessage>> deviceMessageQueues = new ConcurrentHashMap<>();

    /**
     * 添加消息到设备等待队列
     * 
     * @param deviceId 设备ID
     * @param message 消息内容
     * @param messageType 消息类型
     * @return 是否添加成功
     */
    public boolean addMessageToQueue(String deviceId, String message, String messageType) {
        try {
            DeviceMessage deviceMessage = new DeviceMessage();
            deviceMessage.setDeviceId(deviceId);
            deviceMessage.setContent(message);
            deviceMessage.setType(messageType);
            deviceMessage.setTimestamp(LocalDateTime.now());
            deviceMessage.setMessageId("msg_" + System.currentTimeMillis());

            deviceMessageQueues.computeIfAbsent(deviceId, k -> new CopyOnWriteArrayList<>()).add(deviceMessage);
            
            logger.info("消息已添加到等待队列 - DeviceId: {}, MessageId: {}", deviceId, deviceMessage.getMessageId());
            return true;
        } catch (Exception e) {
            logger.error("添加消息到等待队列失败 - DeviceId: {}", deviceId, e);
            return false;
        }
    }

    /**
     * 检查设备是否有待发送消息
     * 
     * @param deviceId 设备ID
     * @return 是否有待发送消息
     */
    public boolean hasPendingMessages(String deviceId) {
        List<DeviceMessage> messages = deviceMessageQueues.get(deviceId);
        return messages != null && !messages.isEmpty();
    }

    /**
     * 获取设备的所有待发送消息
     * 
     * @param deviceId 设备ID
     * @return 待发送消息列表
     */
    public List<DeviceMessage> getPendingMessages(String deviceId) {
        return deviceMessageQueues.getOrDefault(deviceId, new CopyOnWriteArrayList<>());
    }

    /**
     * 清空设备的待发送消息队列
     * 
     * @param deviceId 设备ID
     */
    public void clearPendingMessages(String deviceId) {
        deviceMessageQueues.remove(deviceId);
        logger.info("已清空设备等待队列 - DeviceId: {}", deviceId);
    }

    /**
     * 监听设备重连事件，处理待发送消息
     * 
     * @param event 设备重连事件
     */
    @EventListener
    public void handleDeviceReconnected(DeviceReconnectedEvent event) {
        String deviceId = event.getDeviceId();
        String sessionId = event.getSessionId();
        
        List<DeviceMessage> messages = deviceMessageQueues.get(deviceId);
        if (messages == null || messages.isEmpty()) {
            logger.debug("设备没有待发送消息 - DeviceId: {}", deviceId);
            return;
        }

        logger.info("开始处理设备待发送消息 - DeviceId: {}, SessionId: {}, 消息数量: {}", deviceId, sessionId, messages.size());

        // 获取设备的会话
        var chatSession = sessionManager.getSession(sessionId);
        if (chatSession == null) {
            logger.warn("设备会话不存在，无法发送待发送消息 - DeviceId: {}, SessionId: {}", deviceId, sessionId);
            return;
        }

        // 发送所有待发送消息
        for (DeviceMessage message : messages) {
            try {
                // 构造发送给设备的消息格式
                String messageJson = String.format(
                    "{\"type\":\"device_message\",\"content\":\"%s\",\"messageType\":\"%s\",\"timestamp\":\"%s\",\"messageId\":\"%s\"}",
                    message.getContent().replace("\"", "\\\""),
                    message.getType(),
                    message.getTimestamp(),
                    message.getMessageId()
                );
                
                chatSession.sendTextMessage(messageJson);
                logger.info("已发送待发送消息 - DeviceId: {}, MessageId: {}", deviceId, message.getMessageId());
            } catch (Exception e) {
                logger.error("发送待发送消息失败 - DeviceId: {}, MessageId: {}", deviceId, message.getMessageId(), e);
            }
        }

        // 清空已发送的消息
        clearPendingMessages(deviceId);
    }

    /**
     * 获取所有设备的等待队列状态
     * 
     * @return 设备等待队列状态映射
     */
    public ConcurrentHashMap<String, Integer> getAllQueueStatus() {
        ConcurrentHashMap<String, Integer> status = new ConcurrentHashMap<>();
        deviceMessageQueues.forEach((deviceId, messages) -> {
            status.put(deviceId, messages.size());
        });
        return status;
    }
}
