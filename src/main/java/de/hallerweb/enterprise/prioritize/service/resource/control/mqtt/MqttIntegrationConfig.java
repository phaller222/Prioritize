/*
 * Copyright 2026 Peter Michael Haller and contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.hallerweb.enterprise.prioritize.service.resource.control.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.log4j.Log4j2;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.integration.mqtt.outbound.MqttPahoMessageHandler;
import org.springframework.integration.mqtt.support.DefaultPahoMessageConverter;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;

/**
 * Declarative Spring Integration wiring for MQTT. Encapsulates the entire
 * Paho lifecycle (connect, reconnect, subscribe) — the rest of the system sees only
 * Spring {@link MessageChannel}s.
 * <p>
 * Outbound: messages on {@code mqttOutboundChannel} are published to the topic
 * specified in the {@code mqtt_topic} header.
 * <p>
 * Inbound: incoming messages of the subscribed topics arrive on
 * {@code mqttInboundChannel} and are processed by the {@link InboundResourceEventHandler}.
 * <p>
 * Only active when {@code prioritize.mqtt.enabled=true}.
 *
 * @author peter haller
 */
@Configuration
@ConditionalOnProperty(name = "prioritize.mqtt.enabled", havingValue = "true")
@Log4j2
public class MqttIntegrationConfig {

    private final MqttProperties props;

    public MqttIntegrationConfig(MqttProperties props) {
        this.props = props;
    }

    @Bean
    public MqttPahoClientFactory mqttClientFactory() {
        DefaultMqttPahoClientFactory factory = new DefaultMqttPahoClientFactory();
        MqttConnectOptions options = new MqttConnectOptions();
        options.setServerURIs(new String[]{props.getBrokerUrl()});
        options.setKeepAliveInterval(60);
        options.setCleanSession(true);
        options.setAutomaticReconnect(true);
        if (props.getUsername() != null && !props.getUsername().isBlank()) {
            options.setUserName(props.getUsername());
            options.setPassword(props.getPassword() == null ? new char[0] : props.getPassword().toCharArray());
        }
        factory.setConnectionOptions(options);
        return factory;
    }

    // ---------------- Outbound (system → device) ----------------

    @Bean
    public MessageChannel mqttOutboundChannel() {
        return new DirectChannel();
    }

    @Bean
    @ServiceActivator(inputChannel = "mqttOutboundChannel")
    public MessageHandler mqttOutboundHandler(MqttPahoClientFactory factory) {
        MqttPahoMessageHandler handler =
                new MqttPahoMessageHandler(props.getClientId() + "-pub", factory);
        handler.setAsync(true);
        handler.setDefaultQos(props.getQos());
        // Topic comes per message from the mqtt_topic header (see adapter).
        return handler;
    }

    // ---------------- Inbound (device → system) ----------------

    @Bean
    public MessageChannel mqttInboundChannel() {
        return new DirectChannel();
    }

    @Bean
    public MqttPahoMessageDrivenChannelAdapter mqttInbound(MqttPahoClientFactory factory) {
        String[] topics = props.getSubscribeTopics().toArray(new String[0]);
        MqttPahoMessageDrivenChannelAdapter adapter =
                new MqttPahoMessageDrivenChannelAdapter(
                        props.getClientId() + "-sub", factory, topics);
        adapter.setCompletionTimeout(5000);
        adapter.setConverter(new DefaultPahoMessageConverter());
        adapter.setQos(props.getQos());
        adapter.setOutputChannel(mqttInboundChannel());
        log.info("MQTT-Inbound-Adapter abonniert Topics: {}", props.getSubscribeTopics());
        return adapter;
    }

    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper mqttObjectMapper() {
        return new ObjectMapper();
    }
}
