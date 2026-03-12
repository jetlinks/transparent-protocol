package org.jetlinks.protocol.transparent.tcp;

import org.jetlinks.core.device.DeviceOperator;
import org.jetlinks.core.message.DeviceMessage;
import org.jetlinks.core.message.DeviceOnlineMessage;
import org.jetlinks.core.message.DirectDeviceMessage;
import org.jetlinks.core.message.codec.*;
import org.jetlinks.core.message.codec.parser.DetectedMessageParser;
import org.jetlinks.core.message.codec.parser.rule.StartEndFrameRule;
import org.jetlinks.core.monitor.Monitor;
import org.jetlinks.core.principal.AuthenticationPrincipal;
import org.jetlinks.core.principal.Identity;
import org.jetlinks.core.principal.TokenCredential;
import org.jetlinks.core.server.ClientConnection;
import org.jetlinks.core.spi.ServiceContext;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TransparentTcpMessageCodec implements DeviceMessageCodec, MessageParserFactory {

    public static final String identityType = "tp_tcp";

    static final StartEndFrameRule startEnd = new StartEndFrameRule("@@".getBytes(), "##".getBytes());

    private final ServiceContext context;

    public TransparentTcpMessageCodec(ServiceContext context) {
        this.context = context;
    }

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

        // device 为 null,说明时首次连接,处理注册帧
        if (device == null) {
            String str = message.payloadAsString();
            // 注册帧以@@开头##结尾
            if (str.startsWith("@@") && str.endsWith("##")) {
                str = str.substring(2, str.length() - 2);
            }
            // 首帧为注册帧: id&token
            String[] idAndToken = str.split("&");
            if (idAndToken.length != 2) {
                this.context
                    .getMonitor()
                    .logger()
                    .warn("注册帧格式错误:{}", message);
                ctx.getConnection().disconnect();
                return Flux.empty();
            }

            // 交给平台认证进行,在TransparentProtocolProvider声明了使用token进行认证.
            return context
                .resolveDevice(
                    AuthenticationPrincipal.create(
                        Identity.create(identityType, idAndToken[0]),
                        TokenCredential.create(idAndToken[1])
                    )
                )
                // 没有提取到设备,设备没添加到平台?
                .switchIfEmpty(
                    Mono.fromRunnable(() -> {
                        this.context
                            .getMonitor()
                            .logger()
                            .error("设备认证失败,注册帧错误或者设备未注册.");
                        ctx.getConnection().disconnect();
                    }))
                .<DeviceMessage>mapNotNull(principal -> {
                    if (principal.isVerified()) {
                        TcpTransparentMessageParser parser = parserBind.get(ctx.getConnection());
                        if (parser != null) {
                            Monitor monitor = this.context.getMonitor(principal.getDevice().getDeviceId());
                            monitor.logger().debug("设备认证成功");
                            // 更新 parser
                            // fixme: 根据设备动态配置来创建不同的粘拆包规则.
                            parser.update(createParser(monitor));
                        }
                        return new DeviceOnlineMessage()
                            .thingId("device", principal.getDevice().getDeviceId());
                    }
                    ctx.getConnection().disconnect();
                    return null;
                })
                .onErrorResume(err -> {
                    this.context
                        .getMonitor()
                        .logger()
                        .error("设备[{}]认证失败:{}", idAndToken[0], err.getLocalizedMessage(), err);
                    ctx.getConnection().disconnect();
                    return Mono.empty();
                });
        }
        // 心跳?
        if (message.getPayload().getByte(0) == '@') {
            return Flux.empty();
        }

        byte[] payload = message.payloadAsBytes();
        // 转为透传消息
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

    final Map<ClientConnection, TcpTransparentMessageParser> parserBind = new ConcurrentHashMap<>();

    class TcpTransparentMessageParser extends DetectedMessageParser {
        final ClientConnection connection;

        public TcpTransparentMessageParser(MessageParser parser, ClientConnection connection) {
            super(parser);
            this.connection = connection;
        }

        @Override
        public void dispose() {
            super.dispose();
            parserBind.remove(connection, this);
        }
    }

    private MessageParser createParser(Monitor monitor) {
        return MessageParser
            .builder()
            .monitor(monitor)
            // 识别modbus rtu报文
            .modbusRtu()
            // 最大空闲时间,超过清空缓冲区
            .maxIdleMs(5000)
            // 认证报文or心跳
            .addRule(startEnd)
            .build();
    }

    @Override
    public Mono<MessageParser> create(ClientConnection connection) {
        TcpTransparentMessageParser messageParser = new TcpTransparentMessageParser(
            createParser(context.getMonitor()),
            connection);
        // 缓存连接和parser
        parserBind.put(connection, messageParser);
        // 根据tcp 连接,创建消息解析器.
        return Mono.just(messageParser);
    }
}
