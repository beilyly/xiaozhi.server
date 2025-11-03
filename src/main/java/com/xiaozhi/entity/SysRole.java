package com.xiaozhi.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 角色配置实体类
 *
 * @author Joey
 * 
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties({ "code" })
public class SysRole extends Base<SysRole> {
    /**
     * 角色ID
     */
    private Integer roleId;

    /**
     * 角色头像
     */
    private String avatar;

    /**
     * 角色名称
     */
    private String roleName;

    /**
     * 角色描述
     */
    private String roleDesc;

    /**
     * 语音名称
     */
    private String voiceName;

    /**
     * 状态(1启用 0禁用)
     */
    private String state;

    /**
     * TTS服务ID
     */
    private Integer ttsId;

    /**
     * 模型ID
     */
    private Integer modelId;

    /**
     * 模型名称
     */
    private String modelName;

    /**
     * STT服务ID
     */
    private Integer sttId;

    /**
     * 温度参数，控制输出的随机性
     */
    private Double temperature = 0.7;

    /**
     * Top-P参数，控制输出的多样性
     */
    private Double topP = 0.9;

    /**
     * 语音活动检测-能量阈值
     */
    private Float vadEnergyTh;

    /**
     * 语音活动检测-语音阈值
     */
    private Float vadSpeechTh;

    /**
     * 语音活动检测-静音阈值
     */
    private Float vadSilenceTh;

    /**
     * 语音活动检测-静音毫秒数
     */
    private Integer vadSilenceMs;

    /**
     * 模型提供商
     */
    private String modelProvider;

    /**
     * TTS服务提供商
     */
    private String ttsProvider;

    /**
     * 是否默认角色(1是 0否)
     */
    private String isDefault;

    /**
     * 总设备数
     */
    private Integer totalDevice;
}