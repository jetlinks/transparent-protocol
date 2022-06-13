package org.jetlinks.protocol.transparent;

import org.jetlinks.core.ProtocolSupport;
import org.jetlinks.core.Value;
import org.jetlinks.core.defaults.Authenticator;
import org.jetlinks.core.defaults.CompositeProtocolSupport;
import org.jetlinks.core.device.AuthenticationRequest;
import org.jetlinks.core.device.AuthenticationResponse;
import org.jetlinks.core.device.DeviceOperator;
import org.jetlinks.core.device.MqttAuthenticationRequest;
import org.jetlinks.core.message.codec.CodecFeature;
import org.jetlinks.core.message.codec.DefaultTransport;
import org.jetlinks.core.metadata.DefaultConfigMetadata;
import org.jetlinks.core.metadata.types.PasswordType;
import org.jetlinks.core.metadata.types.StringType;
import org.jetlinks.core.spi.ProtocolSupportProvider;
import org.jetlinks.core.spi.ServiceContext;
import org.jetlinks.protocol.transparent.mqtt.TransparentMqttMessageCodec;
import reactor.core.publisher.Mono;

public class TransparentProtocolProvider implements ProtocolSupportProvider {
    private static final DefaultConfigMetadata mqttConfig = new DefaultConfigMetadata(
            "MQTT认证配置"
            , "mqtt接入时使用的认证配置")
            .add("username", "username", "MQTT用户名", StringType.GLOBAL)
            .add("password", "password", "MQTT密码", PasswordType.GLOBAL);


    @Override
    public Mono<? extends ProtocolSupport> create(ServiceContext context) {
        CompositeProtocolSupport support = new CompositeProtocolSupport();
        support.setId("transparent");
        support.setName("透传");
        support.setDocument(DefaultTransport.MQTT, "doc.md", TransparentProtocolProvider.class.getClassLoader());
        //标记为支持透传
        support.addFeature(CodecFeature.transparentCodec);

        //配置描述
        support.addConfigMetadata(DefaultTransport.MQTT, mqttConfig);

        //MQTT Codec
        support.addMessageCodecSupport(new TransparentMqttMessageCodec());

        //MQTT 认证
        support.addAuthenticator(DefaultTransport.MQTT, (request, device) -> {
            MqttAuthenticationRequest mqttRequest = ((MqttAuthenticationRequest) request);
            return device
                    .getSelfConfig("password").map(Value::asString) //密码
                    .flatMap(password -> {
                        if (password.equals(mqttRequest.getPassword())) {
                            //认证成功，需要返回设备ID
                            return Mono.just(AuthenticationResponse.success(mqttRequest.getUsername()));
                        } else {
                            return Mono.just(AuthenticationResponse.error(400, "密码错误"));
                        }
                    });
        });

        return Mono.just(support);
    }
}
