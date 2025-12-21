package com.xiaozhi.communication.server.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xiaozhi.communication.common.ChatSession;
import com.xiaozhi.communication.common.SessionManager;
import com.xiaozhi.communication.domain.AudioParams;
import com.xiaozhi.communication.domain.HelloFeatures;
import com.xiaozhi.communication.domain.HelloMessage;
import com.xiaozhi.communication.domain.Message;
import com.xiaozhi.dialogue.llm.tool.mcp.device.DeviceMcpService;
import com.xiaozhi.entity.SysDevice;
import com.xiaozhi.event.ChatAudioOpenEvent;
import com.xiaozhi.utils.CmsUtils;
import com.xiaozhi.utils.JsonUtil;
import io.moquette.interception.messages.InterceptConnectMessage;
import io.moquette.interception.messages.InterceptDisconnectMessage;
import io.moquette.interception.messages.InterceptPublishMessage;
import io.netty.buffer.ByteBuf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.xiaozhi.communication.common.MessageHandler;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ConditionalOnProperty(name = "xiaozhi.mqtt.enabled", havingValue = "true")
public class MqttMessageDispatcher {

    private static final Logger logger = LoggerFactory.getLogger(MqttMessageDispatcher.class);
    private static final ObjectMapper MAPPER = JsonUtil.OBJECT_MAPPER;

    private final SessionManager sessionManager;
    private final MessageHandler messageHandler;
    private final ApplicationContext applicationContext;
    private final CmsUtils cmsUtils;
    private final MqttProperties mqttProperties;
    private final EmbeddedMqttServer mqttServer;
    private final DeviceMcpService deviceMcpService;
    private final Map<String, MqttSession> sessions = new ConcurrentHashMap<>();

    public MqttMessageDispatcher(SessionManager sessionManager,
                                 MessageHandler messageHandler,
                                 ApplicationContext applicationContext,
                                 CmsUtils cmsUtils,
                                 MqttProperties mqttProperties,
                                 @Lazy EmbeddedMqttServer mqttServer,
                                 DeviceMcpService deviceMcpService) {
        this.sessionManager = sessionManager;
        this.messageHandler = messageHandler;
        this.applicationContext = applicationContext;
        this.cmsUtils = cmsUtils;
        this.mqttProperties = mqttProperties;
        this.mqttServer = mqttServer;
        this.deviceMcpService = deviceMcpService;
    }

    public void handleConnect(InterceptConnectMessage message) {
        String clientId = message.getClientID();
        if (!mqttProperties.isEnabled() || !StringUtils.hasText(clientId)) {
            return;
        }
        logger.info("MQTT client connected - clientId: {}", clientId);
        MqttSession existing = sessions.remove(clientId);
        if (existing != null) {
            sessionManager.closeSession(existing);
        }
        String sessionId = UUID.randomUUID().toString();
        String topic = mqttProperties.buildTopic(clientId);
        MqttSession session = new MqttSession(sessionId, clientId, topic, mqttServer);
        sessions.put(clientId, session);
        messageHandler.afterConnection(session, clientId);
        applicationContext.publishEvent(new ChatAudioOpenEvent(session));
    }

    public void handleDisconnect(InterceptDisconnectMessage message) {
        String clientId = message.getClientID();
        if (!StringUtils.hasText(clientId)) {
            return;
        }
        MqttSession session = sessions.remove(clientId);
        if (session == null) {
            return;
        }
        logger.info("MQTT client disconnected - clientId: {}", clientId);
        session.markDisconnected();
        messageHandler.afterConnectionClosed(session.getSessionId());
    }

    public void handlePublish(InterceptPublishMessage message) {
        String clientId = message.getClientID();
        MqttSession session = sessions.get(clientId);
        if (session == null) {
            logger.warn("Received MQTT publish from unknown session {}, topic: {}", clientId, message.getTopicName());
            return;
        }

        String topicName = message.getTopicName();
        if (!session.getTopic().equals(topicName)) {
            logger.debug("Ignore MQTT topic {} for session {} (expected topic: {})",
                    topicName, session.getSessionId(), session.getTopic());
            return;
        }

        ByteBuf payloadBuffer = message.getPayload();
        byte[] payloadBytes = new byte[payloadBuffer.readableBytes()];
        payloadBuffer.getBytes(payloadBuffer.readerIndex(), payloadBytes);
        String payload = new String(payloadBytes, StandardCharsets.UTF_8);
        logger.info("MQTT publish received - clientId: {}, topic: {}, payload: {}", clientId, topicName, payload);

        try {
            Message parsed = JsonUtil.fromJson(payload, Message.class);
            if (parsed instanceof HelloMessage helloMessage) {
                logger.info("MQTT publish parsed as HelloMessage - clientId: {}, sessionId: {}", clientId, session.getSessionId());
                handleHelloMessage(session, helloMessage);
            } else {
                logger.info("MQTT publish parsed as generic Message - clientId: {}, sessionId: {}, messageType: {}",
                        clientId, session.getSessionId(), parsed != null ? parsed.getClass().getSimpleName() : "null");
                messageHandler.handleMessage(parsed, session.getSessionId());
            }
        } catch (Exception ex) {
            logger.error("Failed to parse MQTT payload: {}", payload, ex);
        }
    }

    private void handleHelloMessage(MqttSession session, HelloMessage hello) {
        try {
            logger.info("Handle HelloMessage - sessionId: {}, clientId: {}", session.getSessionId(), session.getClientId());
            sessionManager.registerSession(session.getSessionId(), session, session.getClientId());
            sessionManager.setCloseAfterChat(session.getSessionId(), false);
            // 如果之前有假移除的会话且 UDP 通道仍可用，则在 registerSession 中已复用；
            // 只有在不存在可用 UDP 通道时才创建新的。
            UdpAudioChannel udpChannel = session.getUdpChannel();
            if (udpChannel == null || !udpChannel.isReady()) {
                udpChannel = UdpAudioChannel.create(
                        session.getSessionId(),
                        mqttProperties,
                        sessionManager,
                        (sessionId, data) -> messageHandler.handleBinaryMessage(sessionId, data));
                session.attachUdpChannel(udpChannel);
            }

            String udpHost = mqttProperties.resolveUdpHost(cmsUtils);
            if (!StringUtils.hasText(udpHost)) {
                logger.error("UDP host is not configured for session {}", session.getSessionId());
                throw new IllegalStateException("UDP host is not configured");
            }

            ObjectNode response = MAPPER.createObjectNode();
            response.put("type", "hello");
            response.put("transport", "udp");
            response.put("session_id", session.getSessionId());
            response.set("audio_params", MAPPER.valueToTree(AudioParams.Opus));
            ObjectNode udp = response.putObject("udp");
            udp.put("server", udpHost);
            udp.put("port", udpChannel.getLocalPort());
            udp.put("key", udpChannel.getKeyHex());
            udp.put("nonce", udpChannel.getBaseNonceHex());
            session.sendTextMessage(response.toString());
            logger.info("Server hello sent over MQTT - sessionId: {}, clientId: {}, topic: {}, udp: {}:{}",
                    session.getSessionId(), session.getClientId(), session.getTopic(), udpHost, udpChannel.getLocalPort());

            HelloFeatures features = hello.getFeatures();
            if (features != null && Boolean.TRUE.equals(features.getMcp())) {
                ChatSession chatSession = sessionManager.getSession(session.getSessionId());
                Thread.startVirtualThread(() -> {
                    try {
                        SysDevice device = sessionManager.getDeviceConfig(session.getSessionId());
                        if (device != null && device.getRoleId() != null) {
                            deviceMcpService.initialize(chatSession);
                        }
                    } catch (Exception ex) {
                        logger.error("Initialize MCP over MQTT failed - session {}", session.getSessionId(), ex);
                    }
                });
            }
        } catch (Exception ex) {
            logger.error("Failed to open UDP channel for session {}", session.getSessionId(), ex);
        }
    }
}

