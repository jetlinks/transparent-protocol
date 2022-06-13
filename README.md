# 透传协议

直接将设备消息转为透传消息,在平台中通过订阅`/device/*/*/message/direct`来处理.

## MQTT

mqtt透传消息会在`DirectDeviceMessage`中添加header:`topic`,可通过从header中获取topic进行处理.

下发指令目前仅支持`DirectDeviceMessage`.