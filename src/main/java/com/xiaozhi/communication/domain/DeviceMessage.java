package com.xiaozhi.communication.domain;

import java.time.LocalDateTime;

/**
 * 设备消息实体类
 * 用于存储发送给设备的消息信息
 * 
 * @author Joey
 */
public class DeviceMessage {
    private String messageId;
    private String deviceId;
    private String content;
    private String type;
    private LocalDateTime timestamp;

    public DeviceMessage() {
    }

    public DeviceMessage(String deviceId, String content, String type) {
        this.deviceId = deviceId;
        this.content = content;
        this.type = type;
        this.timestamp = LocalDateTime.now();
    }

    // Getters and Setters
    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

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

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return "DeviceMessage{" +
                "messageId='" + messageId + '\'' +
                ", deviceId='" + deviceId + '\'' +
                ", content='" + content + '\'' +
                ", type='" + type + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
}
