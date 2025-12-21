package com.xiaozhi.controller;

import com.xiaozhi.common.web.AjaxResult;
import com.xiaozhi.communication.common.SessionManager;
import com.xiaozhi.communication.service.DeviceMessageQueueService;
import com.xiaozhi.entity.SysDevice;
import com.xiaozhi.service.SysDeviceService;
import com.xiaozhi.utils.CmsUtils;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 设备消息管理控制器
 * 
 * @author Joey
 */
@RestController
@RequestMapping("/api/device/message")
public class DeviceMessageController extends BaseController {

    @Resource
    private SysDeviceService deviceService;

    @Resource
    private SessionManager sessionManager;

    @Resource
    private DeviceMessageQueueService deviceMessageQueueService;

    /**
     * 发送消息到指定设备
     * 
     * @param request 消息请求
     * @return 发送结果
     */
    @PostMapping("/send")
    @ResponseBody
    public AjaxResult sendMessage(@RequestBody DeviceMessageRequest request) {
        try {
            // 验证设备是否存在
            SysDevice device = deviceService.selectDeviceById(request.getDeviceId());
            if (device == null) {
                return AjaxResult.error("设备不存在");
            }

            // 记录消息发送日志
            logger.info("发送消息到设备: {} - 内容: {}", request.getDeviceId(), request.getContent());
            
            // 尝试通过WebSocket发送消息
            var chatSession = sessionManager.getSessionByDeviceId(request.getDeviceId());
            if (chatSession != null && chatSession.isOpen()) {
                // 设备在线，直接发送消息
                try {
                    // 构造发送给设备的消息格式
                    String messageJson = String.format(
                        "{\"type\":\"alert\",\"status\":\"%s\",\"message\":\"%s\",\"emotion\":\"%s\"}",
                        (request.getType() != null ? request.getType() : "info").replace("\"", "\\\""),
                        request.getContent() != null ? request.getContent().replace("\"", "\\\"") : "",
                        "neutral"
                    );
                    
                    chatSession.sendTextMessage(messageJson);
                    
                    // 更新设备状态为在线
                    device.setState("1");
                    deviceService.update(device);
                    
                    Map<String, Object> result = new HashMap<>();
                    result.put("messageId", "msg_" + System.currentTimeMillis());
                    result.put("deviceId", request.getDeviceId());
                    result.put("status", "sent");
                    result.put("timestamp", LocalDateTime.now());
                    
                    logger.info("消息已通过WebSocket发送到设备: {}", request.getDeviceId());
                    return AjaxResult.success("消息发送成功", result);
                    
                } catch (Exception e) {
                    logger.error("通过WebSocket发送消息失败，将消息加入等待队列", e);
                    // WebSocket发送失败，将消息加入等待队列
                    return handleOfflineDevice(request);
                }
            } else {
                // 设备离线，将消息加入等待队列
                return handleOfflineDevice(request);
            }
            
        } catch (Exception e) {
            logger.error("发送消息到设备失败", e);
            return AjaxResult.error("消息发送失败");
        }
    }

    /**
     * 处理离线设备的消息发送
     * 
     * @param request 消息请求
     * @return 发送结果
     */
    private AjaxResult handleOfflineDevice(DeviceMessageRequest request) {
        try {
            // 将消息添加到等待队列
            boolean added = deviceMessageQueueService.addMessageToQueue(
                request.getDeviceId(), 
                request.getContent(), 
                request.getType() != null ? request.getType() : "text"
            );
            
            if (added) {
                // 更新设备状态为离线
                SysDevice device = deviceService.selectDeviceById(request.getDeviceId());
                if (device != null) {
                    device.setState("0");
                    deviceService.update(device);
                }
                
                Map<String, Object> result = new HashMap<>();
                result.put("messageId", "msg_" + System.currentTimeMillis());
                result.put("deviceId", request.getDeviceId());
                result.put("status", "queued");
                result.put("timestamp", LocalDateTime.now());
                result.put("message", "设备离线，消息已加入等待队列，设备重新连接时将自动发送");
                
                logger.info("消息已加入等待队列 - DeviceId: {}", request.getDeviceId());
                return AjaxResult.success("消息已加入等待队列", result);
            } else {
                return AjaxResult.error("消息加入等待队列失败");
            }
        } catch (Exception e) {
            logger.error("处理离线设备消息失败", e);
            return AjaxResult.error("处理离线设备消息失败");
        }
    }

    /**
     * 查询设备消息历史
     * 
     * @param deviceId 设备ID
     * @return 消息历史
     */
    @GetMapping("/query")
    @ResponseBody
    public AjaxResult queryMessages(@RequestParam String deviceId) {
        try {
            // 验证设备是否存在
            SysDevice device = deviceService.selectDeviceById(deviceId);
            if (device == null) {
                return AjaxResult.error("设备不存在");
            }

            // 这里可以添加查询消息历史的逻辑
            // 例如：从数据库查询该设备的消息记录
            
            Map<String, Object> result = new HashMap<>();
            result.put("deviceId", deviceId);
            result.put("messages", new Object[0]); // 暂时返回空数组
            result.put("total", 0);
            
            return AjaxResult.success(result);
            
        } catch (Exception e) {
            logger.error("查询设备消息历史失败", e);
            return AjaxResult.error("查询消息历史失败");
        }
    }

    /**
     * 查询设备等待队列状态
     * 
     * @param deviceId 设备ID
     * @return 等待队列状态
     */
    @GetMapping("/queue/status")
    @ResponseBody
    public AjaxResult getQueueStatus(@RequestParam String deviceId) {
        try {
            // 验证设备是否存在
            SysDevice device = deviceService.selectDeviceById(deviceId);
            if (device == null) {
                return AjaxResult.error("设备不存在");
            }

            // 检查设备是否有待发送消息
            boolean hasPendingMessages = deviceMessageQueueService.hasPendingMessages(deviceId);
            var pendingMessages = deviceMessageQueueService.getPendingMessages(deviceId);
            
            Map<String, Object> result = new HashMap<>();
            result.put("deviceId", deviceId);
            result.put("hasPendingMessages", hasPendingMessages);
            result.put("pendingCount", pendingMessages.size());
            result.put("pendingMessages", pendingMessages);
            result.put("deviceOnline", sessionManager.getSessionByDeviceId(deviceId) != null);
            
            return AjaxResult.success(result);
            
        } catch (Exception e) {
            logger.error("查询设备等待队列状态失败", e);
            return AjaxResult.error("查询等待队列状态失败");
        }
    }

    /**
     * 清空设备等待队列
     * 
     * @param deviceId 设备ID
     * @return 清空结果
     */
    @PostMapping("/queue/clear")
    @ResponseBody
    public AjaxResult clearQueue(@RequestParam String deviceId) {
        try {
            // 验证设备是否存在
            SysDevice device = deviceService.selectDeviceById(deviceId);
            if (device == null) {
                return AjaxResult.error("设备不存在");
            }

            deviceMessageQueueService.clearPendingMessages(deviceId);
            
            Map<String, Object> result = new HashMap<>();
            result.put("deviceId", deviceId);
            result.put("message", "等待队列已清空");
            
            logger.info("已清空设备等待队列 - DeviceId: {}", deviceId);
            return AjaxResult.success("等待队列已清空", result);
            
        } catch (Exception e) {
            logger.error("清空设备等待队列失败", e);
            return AjaxResult.error("清空等待队列失败");
        }
    }

    /**
     * 发送浇水指令到指定设备
     * 
     * @param request 浇水请求（包含设备ID和持续时间）
     * @return 发送结果
     */
    @PostMapping("/water")
    @ResponseBody
    public AjaxResult sendWaterCommand(@RequestBody WaterCommandRequest request) {
        try {
            // 验证设备是否存在
            SysDevice device = deviceService.selectDeviceById(request.getDeviceId());
            if (device == null) {
                return AjaxResult.error("设备不存在");
            }

            // 默认浇水时长为30秒
            int duration = request.getDuration() != null && request.getDuration() > 0 
                ? request.getDuration() 
                : 30;

            // 记录浇水指令发送日志
            logger.info("发送浇水指令到设备: {} - 持续时间: {}秒", request.getDeviceId(), duration);
            
            // 尝试通过WebSocket发送指令
            var chatSession = sessionManager.getSessionByDeviceId(request.getDeviceId());
            if (chatSession != null && chatSession.isOpen()) {
                // 设备在线，直接发送指令
                try {
                    // 构造发送给设备的浇水指令格式
                    String commandJson = String.format(
                        "{\"type\":\"system\",\"command\":\"water\",\"duration\":%d}",
                        duration
                    );
                    
                    chatSession.sendTextMessage(commandJson);
                    
                    // 更新设备状态为在线
                    device.setState("1");
                    deviceService.update(device);
                    
                    Map<String, Object> result = new HashMap<>();
                    result.put("commandId", "cmd_" + System.currentTimeMillis());
                    result.put("deviceId", request.getDeviceId());
                    result.put("status", "sent");
                    result.put("duration", duration);
                    result.put("timestamp", LocalDateTime.now());
                    
                    logger.info("浇水指令已通过WebSocket发送到设备: {} - 持续时间: {}秒", request.getDeviceId(), duration);
                    return AjaxResult.success("浇水指令发送成功", result);
                    
                } catch (Exception e) {
                    logger.error("通过WebSocket发送浇水指令失败", e);
                    return AjaxResult.error("浇水指令发送失败: " + e.getMessage());
                }
            } else {
                // 设备离线
                logger.warn("设备离线，无法发送浇水指令 - DeviceId: {}", request.getDeviceId());
                return AjaxResult.error("设备离线，无法发送浇水指令");
            }
            
        } catch (Exception e) {
            logger.error("发送浇水指令到设备失败", e);
            return AjaxResult.error("浇水指令发送失败");
        }
    }

    /**
     * 设备消息请求实体
     */
    public static class DeviceMessageRequest {
        private String deviceId;
        private String content;
        private String type;
        private String timestamp;

        // Getters and Setters
        public String getDeviceId() {
            return deviceId;
        }

        public void setDeviceId(String deviceId) {
            this.deviceId = deviceId;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(String timestamp) {
            this.timestamp = timestamp;
        }
    }

    /**
     * 浇水指令请求实体
     */
    public static class WaterCommandRequest {
        private String deviceId;
        private Integer duration;

        // Getters and Setters
        public String getDeviceId() {
            return deviceId;
        }

        public void setDeviceId(String deviceId) {
            this.deviceId = deviceId;
        }

        public Integer getDuration() {
            return duration;
        }

        public void setDuration(Integer duration) {
            this.duration = duration;
        }
    }
}
