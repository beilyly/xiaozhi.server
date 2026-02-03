package com.xiaozhi.communication.server.mqtt;

import com.xiaozhi.utils.CmsUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.concurrent.ThreadLocalRandom;

@Component
@ConfigurationProperties(prefix = "xiaozhi.mqtt")
public class MqttProperties {

    private boolean enabled = true;
    private String host = "0.0.0.0";
    private int port = 1883;
    private String externalHost;
    private String username;
    private String password;
    private String topicPrefix = "xiaozhi/device";
    private int keepalive = 240;
    private int udpPortStart = 20000;
    private int udpPortEnd = 20030;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getExternalHost() {
        return externalHost;
    }

    public void setExternalHost(String externalHost) {
        this.externalHost = externalHost;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getTopicPrefix() {
        return topicPrefix;
    }

    public void setTopicPrefix(String topicPrefix) {
        this.topicPrefix = topicPrefix;
    }

    public int getKeepalive() {
        return keepalive;
    }

    public void setKeepalive(int keepalive) {
        this.keepalive = keepalive;
    }

    public int getUdpPortStart() {
        return udpPortStart;
    }

    public void setUdpPortStart(int udpPortStart) {
        this.udpPortStart = udpPortStart;
    }

    public int getUdpPortEnd() {
        return udpPortEnd;
    }

    public void setUdpPortEnd(int udpPortEnd) {
        this.udpPortEnd = udpPortEnd;
    }

    public String buildTopic(String deviceId) {
        if (!StringUtils.hasText(deviceId)) {
            return topicPrefix;
        }
        return topicPrefix.endsWith("/") ? topicPrefix + deviceId : topicPrefix + "/" + deviceId;
    }

    public String resolveExternalEndpoint(CmsUtils cmsUtils) {
        String hostToUse = StringUtils.hasText(externalHost) ? externalHost : cmsUtils.getServerIp();
        if (!hostToUse.contains(":")) {
            return hostToUse + ":" + port;
        }
        return hostToUse;
    }

    public String resolveUdpHost(CmsUtils cmsUtils) {
        return StringUtils.hasText(externalHost) ? externalHost : cmsUtils.getServerIp();
    }

    public int pickRandomUdpPort() {
        if (udpPortEnd <= udpPortStart) {
            return udpPortStart;
        }
        return ThreadLocalRandom.current().nextInt(udpPortStart, udpPortEnd + 1);
    }
}

