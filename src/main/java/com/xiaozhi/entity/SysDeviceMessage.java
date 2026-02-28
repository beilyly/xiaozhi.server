package com.xiaozhi.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 用户与设备的留言记录（Web 端发给设备的消息历史）
 *
 * @author Joey
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@Schema(description = "设备留言记录")
public class SysDeviceMessage extends Base<SysDeviceMessage> {

    @Schema(description = "主键")
    private Long id;

    @Schema(description = "设备ID")
    private String deviceId;

    @Schema(description = "消息内容")
    private String content;

    @Schema(description = "消息类型，如 text、info")
    private String type;

    @Schema(description = "是否为用户发送：1-用户，0-设备/系统")
    private Boolean isUser;

    @Schema(description = "是否已读：0-未读，1-已读")
    private Integer readStatus;

    // 继承 Base: userId, createTime
}
