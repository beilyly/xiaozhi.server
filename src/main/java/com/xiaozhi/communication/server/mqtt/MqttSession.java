package com.xiaozhi.communication.server.mqtt;

import com.xiaozhi.communication.common.ChatSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

public class MqttSession extends ChatSession {

    private static final Logger logger = LoggerFactory.getLogger(MqttSession.class);

    private final String clientId;
    private final String topic;
    private final EmbeddedMqttServer mqttServer;
    private final AtomicBoolean open = new AtomicBoolean(true);
    private volatile UdpAudioChannel udpChannel;

    public MqttSession(String sessionId,
                       String clientId,
                       String topic,
                       EmbeddedMqttServer mqttServer) {
        super(sessionId);
        this.clientId = clientId;
        this.topic = topic;
        this.mqttServer = mqttServer;
    }

    public String getClientId() {
        return clientId;
    }

    public String getTopic() {
        return topic;
    }

    public void attachUdpChannel(UdpAudioChannel channel) {
        this.udpChannel = channel;
    }

    public void detachUdpChannel() {
        if (udpChannel != null) {
            udpChannel.close();
            udpChannel = null;
        }
    }

    /**
     * 获取当前会话绑定的 UDP 通道
     */
    public UdpAudioChannel getUdpChannel() {
        return udpChannel;
    }

    /**
     * 从旧的 MQTT 会话复用 UDP 通道。
     * 仅当旧通道存在且仍然可用时才会复用，并同时继承旧会话的 sessionId，
     * 保证 UDP 通道内的 sessionId 与会话管理中的一致。
     *
     * @param oldSession 之前被假移除的会话
     * @return 是否成功复用 UDP 通道
     */
    public boolean reuseUdpChannelFrom(MqttSession oldSession) {
        if (oldSession == null) {
            return false;
        }
        UdpAudioChannel oldChannel = oldSession.udpChannel;
        if (oldChannel != null && oldChannel.isReady()) {
            // 继承旧会话的 UDP 通道和 sessionId
            this.udpChannel = oldChannel;
            this.sessionId = oldSession.getSessionId();
            return true;
        }
        return false;
    }

    @Override
    public boolean isOpen() {
        return open.get();
    }

    @Override
    public boolean isAudioChannelOpen() {
        return udpChannel != null && udpChannel.isReady();
    }

    @Override
    public void close() {
        markDisconnected();
        //让客户端主动断开
//        if (open.compareAndSet(true, false)) {
//            detachUdpChannel();
//            mqttServer.disconnect(clientId);
//        }
    }

    public void markDisconnected() {

//        if (open.compareAndSet(true, false)) {
//            detachUdpChannel();
//        }
    }

    @Override
    public void sendTextMessage(String message) {
        if (!isOpen()) {
            logger.debug("MQTT session already closed, skip text message");
            return;
        }

        logger.info("sendTextMessage - sessionId: {}, message: {}", getSessionId(), message);
        mqttServer.publish(topic, message.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void sendBinaryMessage(byte[] message) {
        if (!isAudioChannelOpen()) {
            logger.warn("UDP channel is not ready, drop audio frame for session {}", getSessionId());
            return;
        }
        try {
            udpChannel.sendToDevice(message);
        } catch (Exception ex) {
            logger.error("Failed to send UDP audio frame - session {}", getSessionId(), ex);
        }
    }
}

