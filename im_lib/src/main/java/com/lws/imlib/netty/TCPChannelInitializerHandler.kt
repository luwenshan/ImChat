package com.lws.imlib.netty

import com.lws.imlib.HeartbeatRespHandler
import com.lws.imlib.LoginAuthRespHandler
import com.lws.imlib.protobuf.MessageProtobuf
import io.netty.channel.Channel
import io.netty.channel.ChannelInitializer
import io.netty.handler.codec.LengthFieldBasedFrameDecoder
import io.netty.handler.codec.LengthFieldPrepender
import io.netty.handler.codec.protobuf.ProtobufDecoder
import io.netty.handler.codec.protobuf.ProtobufEncoder

/**
 * ChannelHandler 初始化配置
 */
class TCPChannelInitializerHandler(private val imsClient: NettyTcpClient) : ChannelInitializer<Channel>() {
    override fun initChannel(ch: Channel) {
        val pipeline = ch.pipeline()

        // netty提供的自定义长度解码器，解决TCP拆包/粘包问题
        pipeline.addLast("frameEncoder", LengthFieldPrepender(2))
        pipeline.addLast("frameDecoder", LengthFieldBasedFrameDecoder(65535, 0, 2, 0, 2))

        // 增加protobuf编解码支持
        pipeline.addLast(ProtobufEncoder())
        pipeline.addLast(ProtobufDecoder(MessageProtobuf.Msg.getDefaultInstance()))

        // 握手认证消息响应处理handler
        pipeline.addLast(LoginAuthRespHandler::class.java.simpleName, LoginAuthRespHandler(imsClient))
        // 心跳消息响应处理handler
        pipeline.addLast(HeartbeatRespHandler::class.java.simpleName, HeartbeatRespHandler(imsClient))
        // 接收消息处理handler
        pipeline.addLast(TCPReadHandler::class.java.simpleName, TCPReadHandler(imsClient))
    }
}