package com.xiaozhi.dialogue.token;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.xiaozhi.dialogue.token.entity.TokenCache;

/**
 * Token服务接口，提供通用的Token管理逻辑
 */
public interface TokenService {
    
    Logger logger = LoggerFactory.getLogger(TokenService.class);
    
    /**
     * 获取Token缓存对象（由实现类提供）
     */
    TokenCache getTokenCache();
    
    /**
     * 设置Token缓存对象（由实现类提供）
     */
    void setTokenCache(TokenCache tokenCache);
    
    /**
     * 获取配置ID（由实现类提供）
     */
    Integer getConfigId();
    
    /**
     * 获取Token (统一入口)
     */
    default String getToken() {
        // 检查缓存是否存在且有效
        TokenCache cache = getTokenCache();
        if (cache != null) {
            // 更新最后使用时间
            cache.updateLastUsedTime();
            
            // 如果token已过期，清除缓存
            if (cache.isExpired()) {
                clearTokenCache();
            }
            // 如果需要刷新（剩余1小时），异步刷新
            else if (cache.needsRefresh()) {
                refreshTokenAsync();
                return cache.getToken(); // 返回当前还有效的token
            }
            // 如果token仍然有效，直接返回
            else if (isTokenValid()) {
                return cache.getToken();
            }
        }

        // 缓存无效或不存在，获取新token
        return refreshToken();
    }

    /**
     * 获取服务提供商名称
     */
    String getProviderName();
    
    /**
     * 手动刷新Token
     */
    String refreshToken();
    
    /**
     * 检查Token是否有效
     */
    default boolean isTokenValid() {
        TokenCache cache = getTokenCache();
        if (cache == null) {
            return false;
        }
        
        // 检查token是否过期
        return !cache.isExpired();
    }
    
    /**
     * 清理指定配置的Token缓存
     */
    default void clearTokenCache() {
        setTokenCache(null);
    }
    
    /**
     * 使用虚拟线程异步刷新token
     */
    default void refreshTokenAsync() {
        Thread.startVirtualThread(() -> {
            try {
                refreshToken();
            } catch (Exception e) {
                logger.error("虚拟线程异步刷新Token失败，configId: {}: {}", getConfigId(), e.getMessage(), e);
            }
        });
    }
    
    /**
     * 检查是否需要清除缓存（超过24小时未使用）
     */
    default boolean needsCacheCleanup() {
        TokenCache cache = getTokenCache();
        return cache != null && cache.needsCacheCleanup();
    }
}