package org.jetlinks.protocol.transparent.mqtt;

import org.jetlinks.core.device.DeviceOperator;
import org.jetlinks.core.message.DeviceMessage;
import org.jetlinks.core.message.DirectDeviceMessage;
import org.jetlinks.core.message.Message;
import org.jetlinks.core.message.codec.*;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.annotation.Nonnull;

public class TransparentMqttMessageCodec implements DeviceMessageCodec {
    @Override
    public Transport getSupportTransport() {
        return DefaultTransport.MQTT;
    }

    @Nonnull
    @Override
    public Flux<DeviceMessage> decode(@Nonnull MessageDecodeContext context) {
        MqttMessage message = ((MqttMessage) context.getMessage());

        DeviceOperator device = context.getDevice();
        if (device == null) {
            return Flux.empty();
        }
        DirectDeviceMessage msg = new DirectDeviceMessage();
        msg.setDeviceId(context.getDevice().getDeviceId());
        msg.setPayload(message.payloadAsBytes());
        msg.addHeader("topic", message.getTopic());
        return Flux.just(msg);
    }

    @Nonnull
    @Override
    public Publisher<? extends EncodedMessage> encode(@Nonnull MessageEncodeContext context) {
        DeviceMessage message = (DeviceMessage) context.getMessage();
        if (message instanceof DirectDeviceMessage) {
            String topic = message.getHeader("topic").map(String::valueOf).orElse(null);
            if (topic == null) {
                return Mono.empty();
            }
            return Flux
                    .just(SimpleMqttMessage
                                  .builder()
                                  .topic(topic)
                                  .payload(((DirectDeviceMessage) message).getPayload())
                                  .build());
        }
        return Mono.empty();
    }
}
