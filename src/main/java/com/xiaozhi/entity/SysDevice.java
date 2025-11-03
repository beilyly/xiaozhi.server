package com.xiaozhi.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 设备表
 * 
 * @author Joey
 * 
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties({ "startTime", "endTime", "start", "limit", "userId", "code" })
public class SysDevice extends SysRole {
    public static final String DEVICE_STATE_ONLINE = "1";
    public static final String DEVICE_STATE_OFFLINE = "0";

    private String deviceId;

    private String sessionId;

    /**
     * 设备名称
     */
    private String deviceName;

    /**
     * 设备状态
     */
    private String state;

    /**
     * 设备对话次数
     */
    private Integer totalMessage;

    /**
     * 验证码
     */
    private String code;

    /**
     * 音频文件
     */
    private String audioPath;

    /**
     * 最后在线时间
     */
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private String lastLogin;

    /**
     * WiFi名称
     */
    private String wifiName;

    /**
     * IP
     */
    private String ip;

    /**
     * 芯片型号
     */
    private String chipModelName;

    /**
     * 芯片类型
     */
    private String type;

    /**
     * 固件版本
     */
    private String version;

    /**
     * 可用全局function的名称列表(逗号分割)，为空则使用所有全局function
     */
    private String functionNames;

}