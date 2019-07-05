package com.lws.imlib

import com.lws.imlib.netty.NettyTcpClient
import com.lws.imlib.protobuf.MessageProtobuf
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter

/**
 * 心跳消息响应处理handler
 */
class HeartbeatRespHandler(private val imsClient: NettyTcpClient) : ChannelInboundHandlerAdapter() {
    override fun channelRead(ctx: ChannelHandlerContext, msg: Any?) {
        val heartbeatRespMsg = msg as? MessageProtobuf.Msg
        if (heartbeatRespMsg == null || heartbeatRespMsg.head == null) {
            return
        }
        val heartbeatMsg = imsClient.getHeartbeatMsg()
        if (heartbeatMsg.head == null) {
            return
        }

        if (heartbeatMsg.head.msgType == heartbeatRespMsg.head.msgType) {
            println("收到服务端心跳响应消息，message=$heartbeatRespMsg")
        } else {
            ctx.fireChannelRead(msg)
        }
    }
}