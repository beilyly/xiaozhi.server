package com.xiaozhi.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.util.Date;

import org.springframework.format.annotation.DateTimeFormat;

/**
 * 基础实体类，所有实体类的父类
 *
 * @author Joey
 * @param <T> 泛型参数，用于支持链式调用
 * 
 */
@Getter
@Accessors(chain = true)
@JsonIgnoreProperties({ "start", "limit", "userId", "startTime", "endTime" })
public class Base<T extends Base<T>> implements java.io.Serializable {
    /**
     * 创建日期
     */
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    protected Date createTime;

    /**
     * 更新日期
     */
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    protected Date updateTime;

    /**
     * 用户ID
     */
    private Integer userId;

    /**
     * 查询开始时间
     */
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date startTime;

    /**
     * 查询结束时间
     */
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date endTime;

    @SuppressWarnings("unchecked")
    public T setUserId(Integer userId) {
        this.userId = userId;
        return (T) this;
    }

    @SuppressWarnings("unchecked")
    public T setStartTime(Date startTime) {
        this.startTime = startTime;
        return (T) this;
    }

    @SuppressWarnings("unchecked")
    public T setEndTime(Date endTime) {
        this.endTime = endTime;
        return (T) this;
    }

    @SuppressWarnings("unchecked")
    public T setCreateTime(Date createTime) {
        this.createTime = createTime;
        return (T) this;
    }

    @SuppressWarnings("unchecked")
    public T setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
        return (T) this;
    }
}