package com.xiaozhi.event;

import org.springframework.context.ApplicationEvent;

/**
 * 设备重连事件
 * 当设备重新连接时发布此事件，用于处理待发送消息
 * 
 * @author Joey
 */
public class DeviceReconnectedEvent extends ApplicationEvent {
    private final String deviceId;
    private final String sessionId;

    public DeviceReconnectedEvent(Object source, String deviceId, String sessionId) {
        super(source);
        this.deviceId = deviceId;
        this.sessionId = sessionId;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public String getSessionId() {
        return sessionId;
    }
}
