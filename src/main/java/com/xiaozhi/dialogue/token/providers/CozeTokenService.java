package com.xiaozhi.dialogue.token.providers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.xiaozhi.dialogue.token.TokenService;
import com.xiaozhi.dialogue.token.entity.TokenCache;
import com.xiaozhi.entity.SysConfig;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

public class CozeTokenService implements TokenService {

    private static final Logger logger = LoggerFactory.getLogger(CozeTokenService.class);

    private static final String PROVIDER_NAME = "coze";

    private final String oauthAppId;     // OAuth应用ID
    private final String publicKey;      // 公钥
    private final String privateKey;     // 私钥
    private final Integer configId;

    // Token缓存
    private volatile TokenCache tokenCache;
    // 防止并发刷新的锁
    private final ReentrantLock refreshLock = new ReentrantLock();

    // Coze API配置
    private static final String COZE_API_ENDPOINT = "api.coze.cn";
    private static final String TOKEN_URL = "https://api.coze.cn/api/permission/oauth2/token";
    private static final String ALGORITHM = "RS256";
    private static final String TOKEN_TYPE = "JWT";
    private static final int DEFAULT_DURATION_SECONDS = 86399; // 24小时 - 1秒

    private final RestTemplate restTemplate;

    public CozeTokenService(SysConfig config) {
        this.oauthAppId = config.getAppId();    // OAuth应用ID
        this.publicKey = config.getAk();        // 公钥
        this.privateKey = config.getSk();       // 私钥
        this.configId = config.getConfigId();

        this.restTemplate = new RestTemplate();
    }

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    @Override
    public TokenCache getTokenCache() {
        return tokenCache;
    }

    @Override
    public void setTokenCache(TokenCache tokenCache) {
        this.tokenCache = tokenCache;
    }

    @Override
    public Integer getConfigId() {
        return configId;
    }

    @Override
    public String refreshToken() {
        refreshLock.lock();
        try {
            // 双重检查，防止重复刷新
            if (tokenCache != null && isTokenValid() && !tokenCache.needsRefresh()) {
                return tokenCache.getToken();
            }

            // 1. 生成JWT
            String jwt = generateJWT();
            
            // 2. 使用JWT获取访问令牌
            String accessToken = requestAccessToken(jwt);
            
            // 3. 计算过期时间（默认24小时）
            LocalDateTime expireTime = LocalDateTime.now().plusSeconds(DEFAULT_DURATION_SECONDS);
            
            // 4. 更新缓存
            tokenCache = new TokenCache(accessToken, expireTime);
            
            return accessToken;
            
        } catch (Exception e) {
            throw new RuntimeException("刷新Coze Token失败: " + e.getMessage(), e);
        } finally {
            refreshLock.unlock();
        }
    }

    /**
     * 生成JWT
     */
    private String generateJWT() throws Exception {
        long currentTime = System.currentTimeMillis() / 1000;
        
        // 构建Header
        Map<String, Object> headers = new HashMap<>();
        headers.put("alg", ALGORITHM);
        headers.put("typ", TOKEN_TYPE);
        headers.put("kid", publicKey);
        
        // 构建Payload
        Map<String, Object> claims = new HashMap<>();
        claims.put("iss", oauthAppId);               // OAuth应用ID
        claims.put("aud", COZE_API_ENDPOINT);        // Coze API Endpoint
        claims.put("iat", currentTime);              // 开始生效时间
        claims.put("exp", currentTime + 600);        // JWT过期时间（10分钟后）
        claims.put("jti", UUID.randomUUID().toString()); // 随机字符串，防止重放攻击
        
        // 可选参数
        // claims.put("session_name", "user_" + configId);
        
        // 解析私钥
        PrivateKey key = parsePrivateKey(privateKey);
        
        // 生成JWT
        String jwt = Jwts.builder()
                .setHeader(headers)
                .setClaims(claims)
                .signWith(key, SignatureAlgorithm.RS256)
                .compact();
                
        return jwt;
    }

    /**
     * 解析私钥
     */
    private PrivateKey parsePrivateKey(String privateKeyStr) throws Exception {
        // 清理私钥字符串
        String cleanKey = privateKeyStr
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        
        // 解码Base64
        byte[] keyBytes = Base64.getDecoder().decode(cleanKey);
        
        // 创建私钥
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePrivate(spec);
    }

    /**
     * 使用JWT请求访问令牌
     */
    private String requestAccessToken(String jwt) throws Exception {
        // 构建请求头
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(jwt);
        
        // 构建请求体
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("duration_seconds", DEFAULT_DURATION_SECONDS);
        requestBody.put("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer");
        
        // 发送请求
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(TOKEN_URL, request, String.class);
        
        if (response.getStatusCode() == HttpStatus.OK) {
            // 直接使用fastjson解析响应
            JSONObject jsonResponse = JSON.parseObject(response.getBody());
            String accessToken = jsonResponse.getString("access_token");
            
            if (accessToken == null || accessToken.isEmpty()) {
                throw new RuntimeException("响应中未找到access_token字段");
            }
            
            return accessToken;
        } else {
            throw new RuntimeException("Coze API返回错误，HTTP状态码: " + response.getStatusCode() + 
                                     ", 响应: " + response.getBody());
        }
    }
}