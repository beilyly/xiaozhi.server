package com.xiaozhi.dialogue.token.entity;

import java.time.LocalDateTime;

public class TokenCache {
    private String token;
    private LocalDateTime expireTime;
    private LocalDateTime lastUsedTime;
    private LocalDateTime createTime;
    
    public TokenCache(String token, LocalDateTime expireTime) {
        this.token = token;
        this.expireTime = expireTime;
        this.createTime = LocalDateTime.now();
        this.lastUsedTime = LocalDateTime.now();
    }
    
    // 更新最后使用时间
    public void updateLastUsedTime() {
        this.lastUsedTime = LocalDateTime.now();
    }
    
    // 检查是否需要刷新（剩余1小时）
    public boolean needsRefresh() {
        return expireTime != null && 
               LocalDateTime.now().plusHours(1).isAfter(expireTime);
    }
    
    // 检查是否过期
    public boolean isExpired() {
        return expireTime != null && 
               LocalDateTime.now().isAfter(expireTime);
    }
    
    // 检查是否需要清除缓存（超过24小时未使用）
    public boolean needsCacheCleanup() {
        return LocalDateTime.now().minusHours(24).isAfter(lastUsedTime);
    }
    
    // Getters and Setters
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    
    public LocalDateTime getExpireTime() { return expireTime; }
    public void setExpireTime(LocalDateTime expireTime) { this.expireTime = expireTime; }
    
    public LocalDateTime getLastUsedTime() { return lastUsedTime; }
    public void setLastUsedTime(LocalDateTime lastUsedTime) { this.lastUsedTime = lastUsedTime; }
    
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
