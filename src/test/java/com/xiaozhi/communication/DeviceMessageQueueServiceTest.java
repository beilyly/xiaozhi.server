package com.xiaozhi.communication;

import com.xiaozhi.communication.service.DeviceMessageQueueService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import jakarta.annotation.Resource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 设备消息队列服务测试
 * 
 * @author Joey
 */
@SpringBootTest
@ActiveProfiles("test")
public class DeviceMessageQueueServiceTest {

    @Resource
    private DeviceMessageQueueService deviceMessageQueueService;

    @Test
    public void testAddMessageToQueue() {
        String deviceId = "test-device-001";
        String message = "测试消息内容";
        String messageType = "text";

        // 添加消息到队列
        boolean result = deviceMessageQueueService.addMessageToQueue(deviceId, message, messageType);
        
        assertTrue(result, "消息应该成功添加到队列");
        assertTrue(deviceMessageQueueService.hasPendingMessages(deviceId), "设备应该有待发送消息");
        
        var pendingMessages = deviceMessageQueueService.getPendingMessages(deviceId);
        assertEquals(1, pendingMessages.size(), "待发送消息数量应该为1");
        assertEquals(message, pendingMessages.get(0).getContent(), "消息内容应该匹配");
        assertEquals(messageType, pendingMessages.get(0).getType(), "消息类型应该匹配");
    }

    @Test
    public void testClearPendingMessages() {
        String deviceId = "test-device-002";
        
        // 添加消息到队列
        deviceMessageQueueService.addMessageToQueue(deviceId, "测试消息1", "text");
        deviceMessageQueueService.addMessageToQueue(deviceId, "测试消息2", "text");
        
        assertTrue(deviceMessageQueueService.hasPendingMessages(deviceId), "设备应该有待发送消息");
        
        // 清空队列
        deviceMessageQueueService.clearPendingMessages(deviceId);
        
        assertFalse(deviceMessageQueueService.hasPendingMessages(deviceId), "设备应该没有待发送消息");
        assertEquals(0, deviceMessageQueueService.getPendingMessages(deviceId).size(), "待发送消息数量应该为0");
    }

    @Test
    public void testGetAllQueueStatus() {
        String deviceId1 = "test-device-003";
        String deviceId2 = "test-device-004";
        
        // 添加消息到不同设备的队列
        deviceMessageQueueService.addMessageToQueue(deviceId1, "消息1", "text");
        deviceMessageQueueService.addMessageToQueue(deviceId1, "消息2", "text");
        deviceMessageQueueService.addMessageToQueue(deviceId2, "消息3", "text");
        
        var status = deviceMessageQueueService.getAllQueueStatus();
        
        assertEquals(2, status.size(), "应该有2个设备有等待队列");
        assertEquals(2, status.get(deviceId1), "设备1应该有2条待发送消息");
        assertEquals(1, status.get(deviceId2), "设备2应该有1条待发送消息");
        
        // 清理测试数据
        deviceMessageQueueService.clearPendingMessages(deviceId1);
        deviceMessageQueueService.clearPendingMessages(deviceId2);
    }
}
