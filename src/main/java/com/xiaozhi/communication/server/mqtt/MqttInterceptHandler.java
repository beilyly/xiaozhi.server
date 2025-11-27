package com.xiaozhi.communication.server.mqtt;

import io.moquette.interception.AbstractInterceptHandler;
import io.moquette.interception.messages.InterceptConnectMessage;
import io.moquette.interception.messages.InterceptDisconnectMessage;
import io.moquette.interception.messages.InterceptPublishMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MqttInterceptHandler extends AbstractInterceptHandler {

    private final MqttMessageDispatcher dispatcher;
    private static final Logger logger = LoggerFactory.getLogger(MqttInterceptHandler.class);

    public MqttInterceptHandler(MqttMessageDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Override
    public String getID() {
        return "xiaozhi-mqtt-interceptor";
    }

    @Override
    public void onConnect(InterceptConnectMessage msg) {
        dispatcher.handleConnect(msg);
    }

    @Override
    public void onDisconnect(InterceptDisconnectMessage msg) {
        dispatcher.handleDisconnect(msg);
    }

    @Override
    public void onPublish(InterceptPublishMessage msg) {
        dispatcher.handlePublish(msg);
    }

    @Override
    public void onSessionLoopError(Throwable cause) {
        logger.error("MQTT session loop error", cause);
    }
}

