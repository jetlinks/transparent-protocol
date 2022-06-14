package org.jetlinks.protocol.transparent.mqtt;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.jetlinks.core.device.DeviceOperator;
import org.jetlinks.core.message.DeviceMessage;
import org.jetlinks.core.message.DirectDeviceMessage;
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
        byte[] payload = message.payloadAsBytes();
        if (payload.length > 2) {
            if (payload[0] == '0' && payload[1] == 'x') {
                try {
                    payload = Hex.decodeHex(new String(payload, 2, payload.length-2));
                } catch (DecoderException e) {
                    payload = message.payloadAsBytes();
                }
            }
        }
        DirectDeviceMessage msg = new DirectDeviceMessage();
        msg.setDeviceId(context.getDevice().getDeviceId());
        msg.setPayload(payload);
        msg.addHeader("hex", Hex.encodeHexString(payload));
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
