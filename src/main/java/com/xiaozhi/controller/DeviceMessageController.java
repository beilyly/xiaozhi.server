package com.xiaozhi.controller;

import com.xiaozhi.common.web.AjaxResult;
import com.xiaozhi.communication.common.SessionManager;
import com.xiaozhi.communication.service.DeviceMessageQueueService;
import com.xiaozhi.dao.DeviceMessageMapper;
import com.xiaozhi.entity.SysDevice;
import com.xiaozhi.entity.SysDeviceMessage;
import com.xiaozhi.service.SysDeviceService;
import com.xiaozhi.utils.CmsUtils;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

    @Resource
    private DeviceMessageMapper deviceMessageMapper;

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

                    saveDeviceMessageToDb(request, true);
                    Map<String, Object> result = new HashMap<>();
                    result.put("messageId", "msg_" + System.currentTimeMillis());
                    result.put("deviceId", request.getDeviceId());
                    result.put("status", "sent");
                    result.put("timestamp", LocalDateTime.now());

                    logger.info("消息已通过WebSocket发送到设备: {}", request.getDeviceId());
                    return AjaxResult.success("消息发送成功", result);

                } catch (Exception e) {
                    logger.error("通过WebSocket发送消息失败，将消息加入等待队列", e);
                    return handleOfflineDevice(request);
                }
            } else {
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
                SysDevice device = deviceService.selectDeviceById(request.getDeviceId());
                if (device != null) {
                    device.setState("0");
                    deviceService.update(device);
                }
                saveDeviceMessageToDb(request, true);
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
     * 查询当前用户与指定设备的对话历史（按用户隔离，只返回该用户与该设备的记录）
     *
     * @param deviceId 设备ID
     * @return 当前用户与该设备的消息历史
     */
    @GetMapping("/query")
    @ResponseBody
    public AjaxResult queryMessages(@RequestParam String deviceId) {
        try {
            SysDevice device = deviceService.selectDeviceById(deviceId);
            if (device == null) {
                return AjaxResult.error("设备不存在");
            }
            Integer userId = CmsUtils.getUserId();
            if (userId == null) {
                Map<String, Object> empty = new HashMap<>();
                empty.put("deviceId", deviceId);
                empty.put("messages", new Object[0]);
                empty.put("total", 0);
                return AjaxResult.success(empty);
            }
            List<SysDeviceMessage> list = deviceMessageMapper.selectByUserIdAndDeviceId(userId, deviceId);
            List<Map<String, Object>> messages = list.stream().map(m -> {
                Map<String, Object> map = new HashMap<>();
                map.put("id", "msg_" + m.getId());
                map.put("content", m.getContent());
                map.put("type", m.getType() != null ? m.getType() : "text");
                map.put("isUser", Boolean.TRUE.equals(m.getIsUser()));
                map.put("timestamp", m.getCreateTime());
                map.put("read", m.getReadStatus() != null && m.getReadStatus() == 1);
                return map;
            }).collect(Collectors.toList());
            Map<String, Object> result = new HashMap<>();
            result.put("deviceId", deviceId);
            result.put("messages", messages);
            result.put("total", messages.size());
            return AjaxResult.success(result);
        } catch (Exception e) {
            logger.error("查询设备消息历史失败", e);
            return AjaxResult.error("查询消息历史失败");
        }
    }

    /**
     * 标记一条设备消息为已读（按当前用户校验）
     * 请求体：{ "deviceId": "xxx", "messageId": "msg_123" }，messageId 为 query 返回的 id
     */
    @PostMapping("/read")
    @ResponseBody
    public AjaxResult markMessageRead(@RequestBody MarkReadRequest request) {
        try {
            SysDevice device = deviceService.selectDeviceById(request.getDeviceId());
            if (device == null) {
                return AjaxResult.error("设备不存在");
            }
            Integer userId = CmsUtils.getUserId();
            if (userId == null) {
                return AjaxResult.error("请先登录");
            }
            Long id = parseMessageId(request.getMessageId());
            if (id == null) {
                return AjaxResult.success("无需更新");
            }
            int updated = deviceMessageMapper.updateReadStatus(id, userId, 1);
            if (updated > 0) {
                logger.debug("消息已标为已读 - id: {}, userId: {}", id, userId);
            }
            return AjaxResult.success("已标为已读");
        } catch (Exception e) {
            logger.error("标记消息已读失败", e);
            return AjaxResult.error("标记已读失败");
        }
    }

    /** 解析前端 messageId，如 "msg_123" -> 123 */
    private static Long parseMessageId(String messageId) {
        if (messageId == null || messageId.isEmpty()) return null;
        if (messageId.startsWith("msg_")) {
            try {
                return Long.parseLong(messageId.substring(4));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        try {
            return Long.parseLong(messageId);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 将发送到设备的消息写入数据库（按当前用户+设备保留历史）
     */
    private void saveDeviceMessageToDb(DeviceMessageRequest request, boolean isUser) {
        try {
            Integer userId = CmsUtils.getUserId();
            if (userId == null) return;
            SysDeviceMessage msg = new SysDeviceMessage()
                .setUserId(userId)
                .setDeviceId(request.getDeviceId())
                .setContent(request.getContent() != null ? request.getContent() : "")
                .setType(request.getType() != null ? request.getType() : "text")
                .setIsUser(isUser)
                .setReadStatus(0);
            deviceMessageMapper.insert(msg);
            if (msg.getId() != null) {
                deviceMessageMapper.updateAllToReadExcept(userId, request.getDeviceId(), msg.getId());
            }
        } catch (Exception e) {
            logger.warn("保存设备留言记录失败: {}", e.getMessage());
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
     * 向设备发送待命指令（仅此一种指令，避免任意 command 的权限风险）
     * 固定下发：{"type":"system","command":"standby"}
     *
     * @param request 仅需 deviceId
     * @return 发送结果
     */
    @PostMapping("/standby")
    @ResponseBody
    public AjaxResult sendStandby(@RequestBody DeviceStandbyRequest request) {
        try {
            SysDevice device = deviceService.selectDeviceById(request.getDeviceId());
            if (device == null) {
                return AjaxResult.error("设备不存在");
            }
            var chatSession = sessionManager.getSessionByDeviceId(request.getDeviceId());
            if (chatSession == null || !chatSession.isOpen()) {
                return AjaxResult.error("设备不在线，无法发送指令");
            }
            String messageJson = "{\"type\":\"system\",\"command\":\"standby\"}";
            chatSession.sendTextMessage(messageJson);
            Map<String, Object> result = new HashMap<>();
            result.put("deviceId", request.getDeviceId());
            result.put("status", "sent");
            result.put("timestamp", LocalDateTime.now());
            logger.info("已向设备发送待命指令 - DeviceId: {}", request.getDeviceId());
            return AjaxResult.success("待命指令已发送", result);
        } catch (Exception e) {
            logger.error("发送待命指令失败", e);
            return AjaxResult.error("发送指令失败");
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
                : 10;

            // 记录浇水指令发送日志
            logger.info("发送浇水指令到设备: {} - 持续时间: {}秒", request.getDeviceId(), duration);

            // 尝试通过WebSocket发送指令
            var chatSession = sessionManager.getSessionByDeviceId(request.getDeviceId());
            if (chatSession != null && chatSession.isOpen()) {
                // 设备在线，直接发送指令
                try {
                    // 1. 保持原有 system 指令，兼容旧固件逻辑
                    String legacyCommandJson = String.format(
                        "{\"type\":\"system\",\"command\":\"water\",\"duration\":%d}",
                        duration
                    );
                    chatSession.sendTextMessage(legacyCommandJson);

                    // 2. 额外发送一条 MCP 指令，使用 self.pump.turn_on 工具（带 sessionId 与语音控制一致，设备端才处理）
                    long mcpId = System.currentTimeMillis();
                    String mcpCommandJson = String.format(
                        "{\"type\":\"mcp\",\"sessionId\":\"%s\",\"payload\":{\"jsonrpc\":\"2.0\",\"id\":%d,\"method\":\"tools/call\",\"params\":{\"name\":\"self.pump.turn_on\",\"arguments\":{}}}}",
                        chatSession.getSessionId(), mcpId
                    );
                    chatSession.sendTextMessage(mcpCommandJson);

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
     * 待命指令请求实体（仅 deviceId，无其它参数）
     */
    public static class DeviceStandbyRequest {
        private String deviceId;

        public String getDeviceId() { return deviceId; }
        public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    }

    /**
     * 标记已读请求实体
     */
    public static class MarkReadRequest {
        private String deviceId;
        private String messageId;

        public String getDeviceId() { return deviceId; }
        public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
        public String getMessageId() { return messageId; }
        public void setMessageId(String messageId) { this.messageId = messageId; }
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

    /**
     * 灯光控制指令请求实体
     */
    public static class LightCommandRequest {
        private String deviceId;
        /**
         * true=亮灯，false=关灯
         */
        private Boolean on;

        public String getDeviceId() {
            return deviceId;
        }

        public void setDeviceId(String deviceId) {
            this.deviceId = deviceId;
        }

        public Boolean getOn() {
            return on;
        }

        public void setOn(Boolean on) {
            this.on = on;
        }
    }

    /**
     * 发送灯光控制指令到指定设备（通过 MCP）
     *
     * @param request 灯光请求（包含设备ID和开关状态）
     * @return 发送结果
     */
    @PostMapping("/light")
    @ResponseBody
    public AjaxResult sendLightCommand(@RequestBody LightCommandRequest request) {
        try {
            // 验证设备是否存在
            SysDevice device = deviceService.selectDeviceById(request.getDeviceId());
            if (device == null) {
                return AjaxResult.error("设备不存在");
            }

            if (request.getOn() == null) {
                return AjaxResult.error("缺少灯光开关参数");
            }

            logger.info("发送灯光指令到设备: {} - 操作: {}", request.getDeviceId(), request.getOn() ? "亮灯" : "关灯");

            var chatSession = sessionManager.getSessionByDeviceId(request.getDeviceId());
            if (chatSession != null && chatSession.isOpen()) {
                try {
                    // 统一使用 MCP 工具 self.led_strip.*（带 sessionId 与语音控制一致，设备端才处理）
                    String sessionId = chatSession.getSessionId();
                    long baseId = System.currentTimeMillis();
                    if (Boolean.TRUE.equals(request.getOn())) {
                        // 亮灯：设置亮度为 5，并设为白色
                        String brightnessCmd = String.format(
                            "{\"type\":\"mcp\",\"sessionId\":\"%s\",\"payload\":{\"jsonrpc\":\"2.0\",\"id\":%d,\"method\":\"tools/call\",\"params\":{\"name\":\"self.led_strip.set_brightness\",\"arguments\":{\"level\":5}}}}",
                            sessionId, baseId
                        );
                        String colorCmd = String.format(
                            "{\"type\":\"mcp\",\"sessionId\":\"%s\",\"payload\":{\"jsonrpc\":\"2.0\",\"id\":%d,\"method\":\"tools/call\",\"params\":{\"name\":\"self.led_strip.set_all_color\",\"arguments\":{\"red\":255,\"green\":255,\"blue\":255}}}}",
                            sessionId, baseId + 1
                        );
                        chatSession.sendTextMessage(brightnessCmd);
                        chatSession.sendTextMessage(colorCmd);
                    } else {
                        // 关灯：为了兼容设备实现，同时将亮度设为 0 且颜色设为全黑
                        String offBrightnessCmd = String.format(
                            "{\"type\":\"mcp\",\"sessionId\":\"%s\",\"payload\":{\"jsonrpc\":\"2.0\",\"id\":%d,\"method\":\"tools/call\",\"params\":{\"name\":\"self.led_strip.set_brightness\",\"arguments\":{\"level\":0}}}}",
                            sessionId, baseId
                        );
                        String offColorCmd = String.format(
                            "{\"type\":\"mcp\",\"sessionId\":\"%s\",\"payload\":{\"jsonrpc\":\"2.0\",\"id\":%d,\"method\":\"tools/call\",\"params\":{\"name\":\"self.led_strip.set_all_color\",\"arguments\":{\"red\":0,\"green\":0,\"blue\":0}}}}",
                            sessionId, baseId + 1
                        );
                        chatSession.sendTextMessage(offBrightnessCmd);
                        chatSession.sendTextMessage(offColorCmd);
                    }

                    // 更新设备状态为在线
                    device.setState("1");
                    deviceService.update(device);

                    Map<String, Object> result = new HashMap<>();
                    result.put("commandId", "light_" + System.currentTimeMillis());
                    result.put("deviceId", request.getDeviceId());
                    result.put("status", "sent");
                    result.put("on", request.getOn());
                    result.put("timestamp", LocalDateTime.now());

                    logger.info("灯光指令已通过WebSocket发送到设备: {} - 操作: {}", request.getDeviceId(), request.getOn() ? "亮灯" : "关灯");
                    return AjaxResult.success("灯光指令发送成功", result);
                } catch (Exception e) {
                    logger.error("通过WebSocket发送灯光指令失败", e);
                    return AjaxResult.error("灯光指令发送失败: " + e.getMessage());
                }
            } else {
                logger.warn("设备离线，无法发送灯光指令 - DeviceId: {}", request.getDeviceId());
                return AjaxResult.error("设备离线，无法发送灯光指令");
            }
        } catch (Exception e) {
            logger.error("发送灯光指令到设备失败", e);
            return AjaxResult.error("灯光指令发送失败");
        }
    }

    /**
     * 发送停止水泵指令到指定设备（通过 MCP self.pump.turn_off）
     *
     * @param request 仅需 deviceId
     * @return 发送结果
     */
    @PostMapping("/pump/stop")
    @ResponseBody
    public AjaxResult sendPumpStopCommand(@RequestBody PumpStopRequest request) {
        try {
            SysDevice device = deviceService.selectDeviceById(request.getDeviceId());
            if (device == null) {
                return AjaxResult.error("设备不存在");
            }
            var chatSession = sessionManager.getSessionByDeviceId(request.getDeviceId());
            if (chatSession == null || !chatSession.isOpen()) {
                logger.warn("设备离线，无法发送停止水泵指令 - DeviceId: {}", request.getDeviceId());
                return AjaxResult.error("设备离线，无法发送停止水泵指令");
            }
            long mcpId = System.currentTimeMillis();
            String mcpCommandJson = String.format(
                "{\"type\":\"mcp\",\"sessionId\":\"%s\",\"payload\":{\"jsonrpc\":\"2.0\",\"id\":%d,\"method\":\"tools/call\",\"params\":{\"name\":\"self.pump.turn_off\",\"arguments\":{}}}}",
                chatSession.getSessionId(), mcpId
            );
            chatSession.sendTextMessage(mcpCommandJson);
            device.setState("1");
            deviceService.update(device);
            Map<String, Object> result = new HashMap<>();
            result.put("commandId", "pump_stop_" + System.currentTimeMillis());
            result.put("deviceId", request.getDeviceId());
            result.put("status", "sent");
            result.put("timestamp", LocalDateTime.now());
            logger.info("停止水泵指令已发送到设备: {}", request.getDeviceId());
            return AjaxResult.success("停止水泵指令发送成功", result);
        } catch (Exception e) {
            logger.error("发送停止水泵指令到设备失败", e);
            return AjaxResult.error("停止水泵指令发送失败");
        }
    }

    /**
     * 停止水泵请求实体
     */
    public static class PumpStopRequest {
        private String deviceId;

        public String getDeviceId() {
            return deviceId;
        }

        public void setDeviceId(String deviceId) {
            this.deviceId = deviceId;
        }
    }
}
