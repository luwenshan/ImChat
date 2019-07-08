package com.lws.imlib

import com.alibaba.fastjson.JSON
import com.lws.imlib.netty.NettyTcpClient
import com.lws.imlib.protobuf.MessageProtobuf
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter

/**
 * 握手认证消息响应处理handler
 */
class LoginAuthRespHandler(private val imsClient: NettyTcpClient) : ChannelInboundHandlerAdapter() {
    override fun channelRead(ctx: ChannelHandlerContext, msg: Any?) {
        val handshakeRespMsg = msg as? MessageProtobuf.Msg
        if (handshakeRespMsg == null || handshakeRespMsg.head == null) {
            return
        }

        val handshakeMsg = imsClient.getHandshakeMsg()
        if (handshakeMsg == null || handshakeMsg.head == null) {
            return
        }

        if (handshakeMsg.head.msgType == handshakeRespMsg.head.msgType) {
            println("收到服务端握手响应消息，message=$handshakeRespMsg")
            var status = -1
            try {
                val jsonObject = JSON.parseObject(handshakeRespMsg.head.extend)
                status = jsonObject.getIntValue("status")
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                if (status == 1) {
                    // 握手成功，马上先发送一条心跳消息，至于心跳机制管理，交由HeartbeatHandler
                    val heartbeatMsg = imsClient.getHeartbeatMsg()

                    // 握手成功，检查消息发送超时管理器里是否有发送超时的消息，如果有，则全部重发
                    imsClient.getMsgTimeoutTimerManager().onResetConnected()

                    println("发送心跳消息：" + heartbeatMsg + "当前心跳间隔为：" + imsClient.getHeartbeatInterval() + "ms\n")
                    imsClient.sendMsg(heartbeatMsg)

                    // 添加心跳消息管理handler
                    imsClient.addHeartbeatHandler()
                }
            }
        } else {
            ctx.fireChannelRead(msg)
        }
    }
}