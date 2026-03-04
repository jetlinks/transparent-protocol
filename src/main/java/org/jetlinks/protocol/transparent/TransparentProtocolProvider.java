package org.jetlinks.protocol.transparent;

import org.jetlinks.core.ProtocolSupport;
import org.jetlinks.core.Value;
import org.jetlinks.core.defaults.CompositeProtocolSupport;
import org.jetlinks.core.device.AuthenticationResponse;
import org.jetlinks.core.device.DeviceFeatures;
import org.jetlinks.core.device.MqttAuthenticationRequest;
import org.jetlinks.core.message.codec.CodecFeature;
import org.jetlinks.core.message.codec.DefaultTransport;
import org.jetlinks.core.metadata.DefaultConfigMetadata;
import org.jetlinks.core.metadata.types.PasswordType;
import org.jetlinks.core.metadata.types.StringType;
import org.jetlinks.core.principal.CredentialType;
import org.jetlinks.core.principal.PrincipalMetadata;
import org.jetlinks.core.spi.ProtocolSupportProvider;
import org.jetlinks.core.spi.ServiceContext;
import org.jetlinks.protocol.transparent.mqtt.TransparentMqttMessageCodec;
import org.jetlinks.protocol.transparent.tcp.TransparentTcpMessageCodec;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class TransparentProtocolProvider implements ProtocolSupportProvider {

    @Override
    public Mono<? extends ProtocolSupport> create(ServiceContext context) {
        CompositeProtocolSupport support = new CompositeProtocolSupport();
        support.setId("transparent");
        support.setName("透传");

        //标记为支持透传
        support.addFeature(CodecFeature.transparentCodec);
        support.addFeature(DeviceFeatures.supportPrincipal);
        {
            support.setDocument(DefaultTransport.MQTT, "mqtt-document.md",
                                TransparentProtocolProvider.class.getClassLoader());
            //MQTT Codec
            support.addMessageCodecSupport(new TransparentMqttMessageCodec());
            // mqtt认证策略
            support.addPrincipalMetadataResolver(
                DefaultTransport.MQTT,
                device -> {
                    PrincipalMetadata metadata = new PrincipalMetadata();
                    metadata.setName("MQTT");
                    // 平台内置mqtt服务,接入固定为MQTT.
                    metadata.setType(DefaultTransport.MQTT.getId());
                    // 不指定Identifier, 由平台生成.
                    // 密码方式认证
                    metadata.setCredentialType(CredentialType.password);
                    return Flux.just(metadata);
                }
            );
        }

        {
            support.setDocument(DefaultTransport.MQTT, "tcp-document.md",
                                TransparentProtocolProvider.class.getClassLoader());
            // TCP Codec
            support.addMessageCodecSupport(new TransparentTcpMessageCodec());
            // 身份策略
            support.addPrincipalMetadataResolver(
                DefaultTransport.TCP,
                device -> {
                    PrincipalMetadata metadata = new PrincipalMetadata();
                    metadata.setName("TCP");
                    metadata.setType(TransparentTcpMessageCodec.identityType);
                    // 不指定Identifier,使用平台的设备ID.
                    // token 方式认证
                    metadata.setCredentialType(CredentialType.token);
                    return Flux.just(metadata);
                }
            );
        }

        return Mono.just(support);
    }
}
