package com.xiaozhi.dialogue.llm.memory;

import com.xiaozhi.common.web.PageFilter;
import com.xiaozhi.entity.Base;
import com.xiaozhi.entity.SysMessage;
import com.xiaozhi.service.SysMessageService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

/**
 * 基于数据库的聊天记忆实现
 * 全局单例类，负责Conversatin里消息的获取、保存、清理。
 * 后续考虑：DatabaseChatMemory 是对 SysMessageService 的一层薄封装，未来或者有可能考虑合并这两者。
 */
@Service
public class DatabaseChatMemory  implements ChatMemory {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseChatMemory.class);

    private final SysMessageService messageService;

    @Autowired
    public DatabaseChatMemory(SysMessageService messageService) {
        this.messageService = messageService;
    }

    @Override
    public void addMessage(String deviceId, String sessionId, String sender, String content, Integer roleId, String messageType, Long timeMillis) {
        // 异步虚拟线程处理持久化。
        Thread.startVirtualThread(() -> {
            try {
                SysMessage message = new SysMessage();
                message.setDeviceId(deviceId);
                message.setSessionId(sessionId);
                message.setSender(sender);
                message.setMessage(content);
                message.setRoleId(roleId);
                message.setMessageType(messageType);
                Instant instant = Instant.ofEpochMilli(timeMillis).truncatedTo(ChronoUnit.SECONDS);
                message.setCreateTime(Date.from(instant));
                messageService.add(message);
            } catch (Exception e) {
                logger.error("保存消息时出错: {}", e.getMessage(), e);
            }
        });
    }

    @Override
    public List<SysMessage> getMessages(String deviceId, String messageType, Integer limit) {
        try {
            SysMessage queryMessage = new SysMessage();
            queryMessage.setDeviceId(deviceId);
            queryMessage.setMessageType(messageType);
            PageFilter pageFilter = new PageFilter(1, limit);
            List<SysMessage> messages = messageService.query(queryMessage, pageFilter);
            messages = new ArrayList<>(messages);
            messages.sort(Comparator.comparing(Base::getCreateTime));
            return messages;
            // return messages;
        } catch (Exception e) {
            logger.error("获取历史消息时出错: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    @Override
    public void clearMessages(String deviceId) {
        try {
            // 清除设备的历史消息
            SysMessage deleteMessage = new SysMessage();
            deleteMessage.setDeviceId(deviceId);
            // messageService.update(deleteMessage);
        } catch (Exception e) {
            logger.error("清除设备历史记录时出错: {}", e.getMessage(), e);
        }
    }

}