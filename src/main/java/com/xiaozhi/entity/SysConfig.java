package com.xiaozhi.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * LLM\STT\TTS配置
 * 
 * @author Joey
 * 
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class SysConfig extends Base<SysConfig> {
    private Integer configId;

    private Integer userId;

    private String deviceId;

    private Integer roleId;

    /**
     * 配置名称
     */
    private String configName;

    /**
     * 配置描述
     */
    private String configDesc;

    /**
     * 配置类型（llm\stt\tts）
     */
    private String configType;

    /**
     * 模型类型（chat\vision\intent\embedding）
     */
    private String modelType;

    /**
     * 服务提供商 (openai\quen\vosk\aliyun\tencent等)
     */
    private String provider;

    private String appId;

    private String apiKey;

    private String apiSecret;

    private String ak;

    private String sk;

    private String apiUrl;

    private String state;

    private String isDefault;

}
