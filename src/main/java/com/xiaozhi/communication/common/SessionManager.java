package com.xiaozhi.communication.common;

import com.xiaozhi.communication.server.mqtt.MqttSession;
import com.xiaozhi.communication.server.websocket.WebSocketSession;
import com.xiaozhi.dialogue.llm.memory.Conversation;
import com.xiaozhi.dialogue.llm.tool.ToolsSessionHolder;
import com.xiaozhi.dialogue.service.DialogueService;
import com.xiaozhi.entity.SysDevice;
import com.xiaozhi.entity.SysRole;
import com.xiaozhi.enums.ListenMode;
import com.xiaozhi.event.ChatSessionCloseEvent;
import com.xiaozhi.service.SysDeviceService;
import com.xiaozhi.event.ChatSessionOpenEvent;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * WebSocket会话管理服务
 * 负责管理所有WebSocket连接的会话状态
 * 使用JDK 21虚拟线程实现异步处理
 * TODO 重构计划：可能没必要作为Service由Spring管理，而是由Handler处理。
 * TODO 实际底层驱动力来自于Handler，后续服务都是基于Session而不需要SessionManager的。
 */
@Service
public class SessionManager {
    private static final Logger logger = LoggerFactory.getLogger(SessionManager.class);

    // 设置不活跃超时时间为60秒
    private static final long INACTIVITY_TIMEOUT_SECONDS = 60;

    // 用于存储所有连接的会话信息
    private final ConcurrentHashMap<String, ChatSession> sessions = new ConcurrentHashMap<>();

    // 用于存储设备ID到会话的映射，与sessions保持同步
    private final ConcurrentHashMap<String, ChatSession> deviceSessions = new ConcurrentHashMap<>();

    // 用于存储假移除的会话（ESP32/MQTT会话，保持连接但不处理请求）
    private final ConcurrentHashMap<String, ChatSession> removedSessions = new ConcurrentHashMap<>();

    // 用于存储假移除会话的设备ID映射
    private final ConcurrentHashMap<String, ChatSession> removedDeviceSessions = new ConcurrentHashMap<>();

    // 存储验证码生成状态
    private final ConcurrentHashMap<String, Boolean> captchaState = new ConcurrentHashMap<>();

    // 定时任务执行器
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    @Resource
    private ApplicationContext applicationContext;

    @Resource
    @Lazy
    private SysDeviceService deviceService;

    @Value("${check.inactive.session:true}")
    private boolean checkInactiveSession;

    @Value("${inactive.timeout.seconds:60}")
    private int inactiveTimeOutSeconds;

    private DialogueService getDialogueService() {
        return applicationContext.getBean(DialogueService.class);
    }
    private ApplicationEventPublisher eventPublisher;

    /**
     * 初始化方法，启动定时检查不活跃会话的任务
     */
    @PostConstruct
    public void init() {
        if(checkInactiveSession){
            // 项目启动时，将所有设备状态设置为离线
            // 延迟执行设备状态重置，避免循环依赖
            scheduler.schedule(() -> {
                try {
                    SysDevice device = new SysDevice();
                    device.setState(SysDevice.DEVICE_STATE_OFFLINE);
                    // 不设置deviceId，这样会更新所有设备
                    int updatedRows = deviceService.update(device);
                    logger.info("项目启动，重置 {} 个设备状态为离线", updatedRows);
                } catch (Exception e) {
                    logger.error("项目启动时设置设备状态为离线失败", e);
                }
            }, 1, TimeUnit.SECONDS);

            // 定期检查不活跃的会话
            scheduler.scheduleAtFixedRate(this::checkInactiveSessions, 10, 10, TimeUnit.SECONDS);
            logger.info("不活跃会话检查任务已启动，超时时间: {}秒", inactiveTimeOutSeconds);
        }
    }

    /**
     * 销毁方法，关闭定时任务执行器
     */
    @PreDestroy
    public void destroy() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        logger.info("不活跃会话检查任务已关闭");
    }

    /**
     * 检查不活跃的会话并关闭它们
     * 使用虚拟线程实现异步处理
     */
    private void checkInactiveSessions() {
        logger.debug("checkInactiveSessions定时任务触发，当前会话数: {}", sessions.size());
        Thread.startVirtualThread(() -> {
            Instant now = Instant.now();
            sessions.values().forEach(session -> {
                // 检查所有会话类型：WebSocket 会话或 MQTT 会话（有音频通道打开的）
                if(session.isAudioChannelOpen()) {
                    Instant lastActivity = session.getLastActivityTime();
                    if (lastActivity != null) {
                        Duration inactiveDuration = Duration.between(lastActivity, now);
                        if (inactiveDuration.getSeconds() > inactiveTimeOutSeconds) {
                            logger.info("会话 {} 已经 {} 秒没有有效活动，自动关闭",
                                    session.getSessionId(), inactiveDuration.getSeconds());
                            DialogueService dialogueService = getDialogueService();
                            dialogueService.sendGoodbyeMessage(session);
                        }

                    }
                }
            });
        });
    }

    /**
     * 更新会话的最后有效活动时间
     * 这个方法应该只在检测到实际的用户活动时调用，如语音输入或明确的交互
     *
     * @param sessionId 会话ID
     */
    public void updateLastActivity(String sessionId) {
        ChatSession session = sessions.get(sessionId);
        if(session != null){
            session.setLastActivityTime(Instant.now());
        }
    }

    /**
     * 注册新的会话
     *
     * @param sessionId 会话ID
     * @param chatSession  会话
     */
    public void registerSession(String sessionId, ChatSession chatSession) {
        sessions.put(sessionId, chatSession);
        logger.info("会话已注册 - SessionId: {}  SessionType: {}", sessionId, chatSession.getClass().getSimpleName());
        applicationContext.publishEvent(new ChatSessionOpenEvent(chatSession));
    }



    public void registerSession(String sessionId, ChatSession chatSession, String deviceId) {
        // 如果该设备之前有假移除的会话，先清理并尝试复用 UDP 通道
        ChatSession removedSession = removedDeviceSessions.remove(deviceId);
        if (removedSession != null) {
            removedSessions.remove(removedSession.getSessionId());
            logger.info("恢复ESP32会话 - DeviceId: {}, 旧SessionId: {}, 新SessionId: {}",
                    deviceId, removedSession.getSessionId(), sessionId);

            // 如果是 MQTT 会话，则尝试从旧会话复用 UDP 通道
            if (removedSession instanceof com.xiaozhi.communication.server.mqtt.MqttSession oldMqttSession
                    && chatSession instanceof com.xiaozhi.communication.server.mqtt.MqttSession newMqttSession) {
                boolean reused = newMqttSession.reuseUdpChannelFrom(oldMqttSession);
                if (reused) {
                    logger.info("复用ESP32 UDP通道 - DeviceId: {}, SessionId: {}",
                            deviceId, newMqttSession.getSessionId());
                }
            }
        }

        // 这里使用会话当前的 sessionId，兼容可能在复用时被旧会话覆盖的场景
        String actualSessionId = chatSession.getSessionId();
        sessions.put(actualSessionId, chatSession);
        deviceSessions.put(deviceId, chatSession);
        logger.info("会话已注册 - SessionId: {}  SessionType: {}", actualSessionId, chatSession.getClass().getSimpleName());
        // 发布设备重连事件，让DeviceMessageQueueService处理待发送消息
        if (chatSession.getSysDevice() != null) {
            // 同时更新设备ID映射
            logger.info("发布设备重连事件 - DeviceId: {}, SessionId: {}", deviceId, sessionId);
            applicationContext.publishEvent(new ChatSessionOpenEvent(chatSession));
        }
    }

    /**
     * 关闭并清理WebSocket会话
     * 对于ESP32/MQTT会话，采用假移除策略：保持连接但不处理请求，直到下次连接
     *
     * @param sessionId 会话ID
     */
    public void removeSession(String sessionId){
        ChatSession removed = sessions.remove(sessionId);
        if (removed != null) {
            // 如果是MQTT会话（ESP32设备），采用假移除策略
            if (removed instanceof MqttSession) {
                // 移到removedSessions，保持连接但不处理请求
                removedSessions.put(sessionId, removed);
                if (removed.getSysDevice() != null) {
                    String deviceId = removed.getSysDevice().getDeviceId();
                    deviceSessions.remove(deviceId, removed);
                    removedDeviceSessions.put(deviceId, removed);
                    logger.info("ESP32会话假移除 - SessionId: {}, DeviceId: {}, 保持连接但不处理请求", sessionId, deviceId);
                }
            } else {
                // 非MQTT会话，正常移除
                if (removed.getSysDevice() != null) {
                    deviceSessions.remove(removed.getSysDevice().getDeviceId(), removed);
                }
            }
        }
    }

    /**
     * 关闭并清理WebSocket会话
     *
     * @param sessionId 会话ID
     */
    public void closeSession(String sessionId){
        ChatSession chatSession = sessions.get(sessionId);
        if(chatSession != null) {
            closeSession(chatSession);
        }
    }

    /**
     * 关闭并清理WebSocket会话
     * 使用虚拟线程实现异步处理
     *
     * @param chatSession 聊天session
     */
    public void closeSession(ChatSession chatSession) {
        if(chatSession == null){
            return;
        }
        try {
            removeSession(chatSession.getSessionId());
            // 关闭会话
            if(chatSession.isAudioChannelOpen()){
                chatSession.close();
                applicationContext.publishEvent(new ChatSessionCloseEvent(chatSession));
                logger.info("会话已关闭 - SessionId: {} SessionType: {}", chatSession.getSessionId(), chatSession.getClass().getSimpleName());
            }

            // 清理音频流
            Sinks.Many<byte[]> sink = chatSession.getAudioSinks();
            if (sink != null) {
                sink.tryEmitComplete();
            }
            // 重置会话状态
            chatSession.setStreamingState(false);
            chatSession.setAudioSinks(null);
            // 清理Conversation缓存的对话历史。
            Conversation conversation = chatSession.getConversation();
            if (conversation != null) {
                conversation.clear();
            }

        } catch (Exception e) {
            logger.error("清理会话资源时发生错误 - SessionId: {}",
                    chatSession.getSessionId(), e);
        }
    }

    /**
     * 注册设备配置
     *
     * @param sessionId 会话ID
     * @param device    设备信息
     */
    public void registerDevice(String sessionId, SysDevice device) {
        // 先检查是否已存在该sessionId的配置
        ChatSession chatSession = sessions.get(sessionId);
        if(chatSession != null){
            chatSession.setSysDevice(device);
            // 同时更新设备ID映射
            deviceSessions.put(device.getDeviceId(), chatSession);
            updateLastActivity(sessionId); // 更新活动时间
            logger.debug("设备配置已注册 - SessionId: {}, DeviceId: {}", sessionId, device.getDeviceId());
        }
    }

    /**
     * 设置会话完成后是否关闭
     *
     * @param sessionId 会话ID
     * @param close     是否关闭
     */
    public void setCloseAfterChat(String sessionId, boolean close) {
        ChatSession chatSession = sessions.get(sessionId);
        if(chatSession != null){
            chatSession.setCloseAfterChat(close);
        }
    }

    /**
     * 获取会话完成后是否关闭
     *
     * @param sessionId 会话ID
     * @return 是否关闭
     */
    public boolean isCloseAfterChat(String sessionId) {
        ChatSession chatSession = sessions.get(sessionId);
        if(chatSession != null){
            return chatSession.isCloseAfterChat();
        }else{
            return true;
        }
    }

//    /**
//     * 缓存配置信息
//     *
//     * @param configId 配置ID
//     * @param config   配置信息
//     */
//    public void cacheConfig(Integer configId, SysConfig config) {
//        if (configId != null && config != null) {
//            configCache.put(configId, config);
//        }
//    }
//
//    /**
//     * 删除配置
//     *
//     * @param configId 配置ID
//     */
//    public void removeConfig(Integer configId) {
//        configCache.remove(configId);
//    }

    /**
     * 获取会话
     *
     * @param sessionId 会话ID
     * @return WebSocket会话
     */
    public ChatSession getSession(String sessionId) {
        return sessions.get(sessionId);
    }

    /**
     * 检查会话是否被假移除
     *
     * @param sessionId 会话ID
     * @return 如果会话被假移除返回true，否则返回false
     */
    public boolean isSessionRemoved(String sessionId) {
        return removedSessions.containsKey(sessionId);
    }
    /**
     * 获取会话（通过设备ID）
     * 同时查找正常会话和假移除的会话，用于服务器主动发送消息
     *
     * @param deviceId 设备ID
     * @return 会话，优先返回正常会话，如果没有则返回假移除的会话
     */
    public ChatSession getSessionByDeviceId(String deviceId) {
        if (deviceId == null){
            return null;
        }
        // 优先查找正常会话
        ChatSession session = deviceSessions.get(deviceId);
        if (session != null) {
            return session;
        }
        // 如果没有正常会话，查找假移除的会话（用于服务器主动发送消息）
        return removedDeviceSessions.get(deviceId);
    }

    /**
     * 获取设备配置
     *
     * @param sessionId 会话ID
     * @return 设备配置
     */
    public SysDevice getDeviceConfig(String sessionId) {
        ChatSession chatSession = sessions.get(sessionId);
        if (chatSession != null) {
            return chatSession.getSysDevice();
        }
        return null;
    }

    /**
     * 获取会话的function holder
     *
     * @param sessionId 会话ID
     * @return FunctionSessionHolder
     */
    public ToolsSessionHolder getFunctionSessionHolder(String sessionId) {
        ChatSession chatSession = sessions.get(sessionId);
        if (chatSession != null) {
            return chatSession.getFunctionSessionHolder();
        }
        return null;
    }

    /**
     * 获取用户的可用角色列表
     *
     * @param sessionId 会话ID
     * @return 角色列表
     */
    public List<SysRole> getAvailableRoles(String sessionId) {
        ChatSession chatSession = sessions.get(sessionId);
        if (chatSession != null) {
            return chatSession.getSysRoleList();
        }
        return null;
    }

    /**
     * 音乐播放状态
     *
     * @param sessionId 会话ID
     * @param isPlaying 是否正在播放音乐
     */
    public void setMusicPlaying(String sessionId, boolean isPlaying) {
        ChatSession chatSession = sessions.get(sessionId);
        if (chatSession != null) {
            chatSession.setMusicPlaying(isPlaying);
        }
    }

    /**
     * 是否在播放音乐
     *
     * @param sessionId 会话ID
     * @return 是否正在播放音乐
     */
    public boolean isMusicPlaying(String sessionId) {
        ChatSession chatSession = sessions.get(sessionId);
        if (chatSession != null) {
            return chatSession.isMusicPlaying();
        }
        return false;
    }

    /**
     * 播放状态
     *
     * @param sessionId 会话ID
     * @param isPlaying 是否正在说话
     */
    public void setPlaying(String sessionId, boolean isPlaying) {
        ChatSession chatSession = sessions.get(sessionId);
        if (chatSession != null) {
            chatSession.setPlaying(isPlaying);
        }
    }

    /**
     * 是否在播放音乐
     *
     * @param sessionId 会话ID
     * @return 是否正在播放音乐
     */
    public boolean isPlaying(String sessionId) {
        ChatSession chatSession = sessions.get(sessionId);
        if (chatSession != null) {
            return chatSession.isPlaying();
        }
        return false;
    }

    /**
     * 设备状态
     *
     * @param sessionId
     * @param mode  设备状态 auto/realTime
     */
    public void setMode(String sessionId, ListenMode mode) {
        ChatSession chatSession = sessions.get(sessionId);
        if (chatSession != null) {
            chatSession.setMode(mode);
        }
    }

    /**
     * 获取设备状态
     *
     * @param sessionId
     */
    public ListenMode getMode(String sessionId) {
        ChatSession chatSession = sessions.get(sessionId);
        if (chatSession != null) {
            return chatSession.getMode();
        }
        return ListenMode.Auto;
    }

    /**
     * 设置流式识别状态
     *
     * @param sessionId   会话ID
     * @param isStreaming 是否正在流式识别
     */
    public void setStreamingState(String sessionId, boolean isStreaming) {
        ChatSession chatSession = sessions.get(sessionId);
        if (chatSession != null) {
            chatSession.setStreamingState(isStreaming);
            logger.info("更新流式识别状态 - SessionId: {}, isStreaming={}", sessionId, isStreaming);
        }
        updateLastActivity(sessionId); // 更新活动时间
    }

    /**
     * 获取流式识别状态
     *
     * @param sessionId 会话ID
     * @return 是否正在流式识别
     */
    public boolean isStreaming(String sessionId) {
        ChatSession chatSession = sessions.get(sessionId);
        if (chatSession != null) {
            return chatSession.isStreamingState();
        }
        return false;
    }

    /**
     * 创建音频数据流
     *
     * @param sessionId 会话ID
     */
    public void createAudioStream(String sessionId) {
        Sinks.Many<byte[]> sink = Sinks.many().multicast().onBackpressureBuffer();
        ChatSession chatSession = sessions.get(sessionId);
        if (chatSession != null) {
            chatSession.setAudioSinks(sink);
            logger.info("创建音频流 - SessionId: {}", sessionId);
        }
    }

    /**
     * 获取音频数据流
     *
     * @param sessionId 会话ID
     * @return 音频数据流
     */
    public Sinks.Many<byte[]> getAudioStream(String sessionId) {
        ChatSession chatSession = sessions.get(sessionId);
        if (chatSession != null) {
            return chatSession.getAudioSinks();
        }
        return null;
    }

    /**
     * 发送音频数据
     *
     * @param sessionId 会话ID
     * @param data 音频数据
     */
    public void sendAudioData(String sessionId, byte[] data) {
        Sinks.Many<byte[]> sink = getAudioStream(sessionId);
        if (sink != null) {
            sink.tryEmitNext(data);
            logger.debug("发送音频数据到音频流 - SessionId: {}, length={}", sessionId, data != null ? data.length : 0);
        }
    }

    /**
     * 完成音频流
     *
     * @param sessionId 会话ID
     */
    public void completeAudioStream(String sessionId) {
        Sinks.Many<byte[]> sink = getAudioStream(sessionId);
        if (sink != null) {
            sink.tryEmitComplete();
            logger.info("完成音频流 - SessionId: {}", sessionId);
        }
    }

    /**
     * 关闭音频流
     *
     * @param sessionId 会话ID
     */
    public void closeAudioStream(String sessionId) {
        Sinks.Many<byte[]> sink = getAudioStream(sessionId);

        ChatSession chatSession = sessions.get(sessionId);
        if (chatSession != null) {
            chatSession.setAudioSinks(null);
            logger.info("关闭音频流 - SessionId: {}", sessionId);
        }
    }

    /**
     * 标记设备正在生成验证码
     *
     * @param deviceId 设备ID
     * @return 如果设备之前没有在生成验证码，返回true；否则返回false
     */
    public boolean markCaptchaGeneration(String deviceId) {
        return captchaState.putIfAbsent(deviceId, Boolean.TRUE) == null;
    }

    /**
     * 取消设备验证码生成标记
     *
     * @param deviceId 设备ID
     */
    public void unmarkCaptchaGeneration(String deviceId) {
        captchaState.remove(deviceId);
    }

    public Optional<Conversation> findConversation(String deviceId) {
        return sessions.values().stream()
                .filter(session -> session.getSysDevice().getDeviceId().equals(deviceId))
                .findFirst()
                .map(ChatSession::getConversation);
    }
}
