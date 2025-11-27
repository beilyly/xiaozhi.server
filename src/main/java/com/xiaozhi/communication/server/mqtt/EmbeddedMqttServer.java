package com.xiaozhi.communication.server.mqtt;

import io.moquette.broker.Server;
import io.moquette.broker.config.MemoryConfig;
import io.moquette.BrokerConstants;
import io.moquette.interception.InterceptHandler;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.mqtt.MqttFixedHeader;
import io.netty.handler.codec.mqtt.MqttMessageType;
import io.netty.handler.codec.mqtt.MqttPublishMessage;
import io.netty.handler.codec.mqtt.MqttPublishVariableHeader;
import io.netty.handler.codec.mqtt.MqttQoS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Properties;

@Component
@ConditionalOnProperty(name = "xiaozhi.mqtt.enabled", havingValue = "true")
public class EmbeddedMqttServer implements InitializingBean, DisposableBean {

    private static final Logger logger = LoggerFactory.getLogger(EmbeddedMqttServer.class);

    private final Server broker = new Server();
    private final MqttProperties properties;
    private final MqttMessageDispatcher dispatcher;

    public EmbeddedMqttServer(MqttProperties properties, MqttMessageDispatcher dispatcher) {
        this.properties = properties;
        this.dispatcher = dispatcher;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        Properties config = new Properties();
        config.setProperty(BrokerConstants.HOST_PROPERTY_NAME, properties.getHost());
        config.setProperty(BrokerConstants.PORT_PROPERTY_NAME, String.valueOf(properties.getPort()));
        config.setProperty(BrokerConstants.ALLOW_ANONYMOUS_PROPERTY_NAME, "true");
        MemoryConfig memoryConfig = new MemoryConfig(config);
        List<InterceptHandler> interceptHandlers = List.of(new MqttInterceptHandler(dispatcher));
        broker.startServer(memoryConfig, interceptHandlers);
        logger.info("Embedded MQTT broker started on {}:{}", properties.getHost(), properties.getPort());
    }

    public void publish(String topic, byte[] payload) {
        ByteBuf byteBuf = Unpooled.copiedBuffer(payload);
        MqttFixedHeader header = new MqttFixedHeader(
                MqttMessageType.PUBLISH,
                false,
                MqttQoS.AT_LEAST_ONCE,
                false,
                0);
        MqttPublishVariableHeader variableHeader = new MqttPublishVariableHeader(topic, 0);
        MqttPublishMessage message = new MqttPublishMessage(header, variableHeader, byteBuf);
        broker.internalPublish(message, "server");
    }

    public void disconnect(String clientId) {
        try {
            broker.disconnectClient(clientId);
        } catch (Exception ex) {
            logger.debug("Failed to disconnect MQTT client {}", clientId, ex);
        }
    }

    @Override
    public void destroy() {
        try {
            broker.stopServer();
            logger.info("Embedded MQTT broker stopped");
        } catch (Exception ex) {
            logger.warn("Failed to stop embedded MQTT broker", ex);
        }
    }
}

