package com.xiaozhi.service;

import com.xiaozhi.entity.SysTemplate;

import java.util.List;

/**
 * 提示词模板服务接口
 */
public interface SysTemplateService {
    
    /**
     * 添加模板
     * 
     * @param template 模板信息
     * @return 结果
     */
    int add(SysTemplate template);
    
    /**
     * 修改模板
     * 
     * @param template 模板信息
     * @return 结果
     */
    int update(SysTemplate template);
    
    /**
     * 删除模板
     * 
     * @param templateId 模板ID
     * @return 结果
     */
    int delete(Integer templateId);
    
    /**
     * 查询模板列表
     * 
     * @param template 模板信息
     * @return 模板集合
     */
    List<SysTemplate> query(SysTemplate template);
    
    /**
     * 查询模板详情
     * 
     * @param templateId 模板ID
     * @return 模板信息
     */
    SysTemplate selectTemplateById(Integer templateId);

}