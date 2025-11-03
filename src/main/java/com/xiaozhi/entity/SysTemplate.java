package com.xiaozhi.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 提示词模板实体类
 *
 * @author Joey
 *
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class SysTemplate extends Base<SysTemplate> {

    /**
     * 模板ID
     */
    private Integer templateId;

    /**
     * 模板名称
     */
    private String templateName;

    /**
     * 模板描述
     */
    private String templateDesc;

    /**
     * 模板内容
     */
    private String templateContent;

    /**
     * 模板分类
     */
    private String category;

    /**
     * 是否默认模板(1是 0否)
     */
    private String isDefault;

    /**
     * 状态(1启用 0禁用)
     */
    private String state;
}