package com.xiaozhi.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.util.Date;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * 智能体实体类
 * 
 * @author Joey
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class SysAgent extends SysConfig {

    /** 智能体ID */
    private Integer agentId;

    /** 智能体名称 */
    private String agentName;

    /** 平台智能体ID */
    private String botId;

    /** 智能体描述 */
    private String agentDesc;

    /** 图标URL */
    private String iconUrl;

    /** 发布时间 */
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date publishTime;
}