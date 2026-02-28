package com.xiaozhi.dao;

import com.xiaozhi.entity.SysDeviceMessage;

import java.util.List;

/**
 * 设备留言记录 数据层（用户与设备的对话历史）
 *
 * @author Joey
 */
public interface DeviceMessageMapper {

    int insert(SysDeviceMessage record);

    List<SysDeviceMessage> selectByUserIdAndDeviceId(Integer userId, String deviceId);

    int updateReadStatus(Long id, Integer userId, Integer readStatus);

    /**
     * 将指定用户+设备下除 excludeId 外的消息全部标为已读（发送新消息时保留最新一条未读）
     */
    int updateAllToReadExcept(Integer userId, String deviceId, Long excludeId);
}
