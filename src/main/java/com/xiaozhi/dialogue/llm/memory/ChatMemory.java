package com.xiaozhi.dialogue.llm.memory;

import com.xiaozhi.entity.SysMessage;

import java.util.List;

/**
 * 聊天记忆接口，全局对象，不针对单个会话，而是负责全局记忆的存储策略及针对不同类型数据库的适配。。
 * 不同于SysMessageService，此接口应该是一个更高的抽象层，更多是负责存储策略而并非底层存储的增删改查。
 * 已经参考了spring ai 的ChatMemory接口，暂时放弃spring ai 的ChatMemory。
 * 以后使用ChatClient与Advisor时直接实现一个更本地友好的ChatMemoryAdvisor。
 * Conversation则是参考了 langchain4j 的ChatMemory。
 *
 */
public interface ChatMemory {

    /**
     * 添加消息
     * TODO 参数太多，后续考虑如何简化一些
     */
    void addMessage(String deviceId, String sessionId, String sender, String content, Integer roleId, String messageType, Long timeMillis);

    /**
     * 获取历史对话消息列表
     * TODO messageType参数，后续考虑是否需要。另外可重构为一个枚举类
     *
     * @param deviceId 设备ID
     * @param messageType 消息类型
     * @param limit 限制数量
     * @return 消息列表
     */
    List<SysMessage> getMessages(String deviceId, String messageType, Integer limit);

    /**
     * 清除设备的历史记录
     *
     * @param deviceId 设备ID
     */
    void clearMessages(String deviceId);

}