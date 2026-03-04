package org.jetlinks.protocol.transparent.tcp;

import org.jetlinks.core.device.DeviceOperator;
import org.jetlinks.core.message.DeviceMessage;
import org.jetlinks.core.message.DeviceOnlineMessage;
import org.jetlinks.core.message.DirectDeviceMessage;
import org.jetlinks.core.message.codec.*;
import org.jetlinks.core.principal.AuthenticationPrincipal;
import org.jetlinks.core.principal.Identity;
import org.jetlinks.core.principal.TokenCredential;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.annotation.Nonnull;

public class TransparentTcpMessageCodec implements DeviceMessageCodec {

    public static final String identityType = "tp_tcp";

    @Override
    public Transport getSupportTransport() {
        return DefaultTransport.TCP;
    }

    @Nonnull
    @Override
    public Publisher<? extends DeviceMessage> decode(@Nonnull MessageDecodeContext context) {
        EncodedMessage message = (context.getMessage());
        FromDeviceMessageContext ctx = ((FromDeviceMessageContext) context);
        DeviceOperator device = context.getDevice();
        if (device == null) {
            //首帧为注册帧: id&token
            String[] idAndToken = message.payloadAsString().split("&");
            if (idAndToken.length != 2) {
                ctx.getConnection().disconnect();
                return Flux.empty();
            }
            // 交给平台认证
            return context
                .resolveDevice(
                    AuthenticationPrincipal.create(
                        Identity.create(identityType, idAndToken[0]),
                        TokenCredential.create(idAndToken[1])
                    )
                )
                .mapNotNull(principal -> {
                    if (principal.isVerified()) {
                        return new DeviceOnlineMessage()
                            .thingId("device", principal.getDevice().getDeviceId());
                    }
                    ctx.getConnection().disconnect();
                    return null;
                });
        }

        byte[] payload = message.payloadAsBytes();
        DirectDeviceMessage msg = new DirectDeviceMessage();
        msg.setDeviceId(device.getDeviceId());
        msg.setPayload(payload);
        return Flux.just(msg);
    }

    @Nonnull
    @Override
    public Publisher<? extends EncodedMessage> encode(@Nonnull MessageEncodeContext context) {
        DeviceMessage message = (DeviceMessage) context.getMessage();
        if (message instanceof DirectDeviceMessage msg) {
            return Flux.just(EncodedMessage.simple(msg.asByteBuf()));
        }
        return Mono.empty();
    }
}
