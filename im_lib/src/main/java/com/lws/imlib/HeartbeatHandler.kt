package com.lws.imlib

import com.lws.imlib.netty.NettyTcpClient
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.handler.timeout.IdleState
import io.netty.handler.timeout.IdleStateEvent

/**
 * 心跳任务处理器
 */
class HeartbeatHandler(private val imsClient: NettyTcpClient) : ChannelInboundHandlerAdapter() {

    private var heartbeatTask: HeartbeatTask? = null

    override fun userEventTriggered(ctx: ChannelHandlerContext, evt: Any?) {
        super.userEventTriggered(ctx, evt)
        if (evt is IdleStateEvent) {
            when (evt.state()) {
                IdleState.READER_IDLE -> {
                    // 规定时间内没收到服务端心跳包响应，进行重连操作
                    imsClient.resetConnect(false)
                }
                IdleState.WRITER_IDLE -> {
                    // 规定时间内没向服务端发送心跳包，即发送一个心跳包
                    if (heartbeatTask == null) {
                        heartbeatTask = HeartbeatTask(ctx)
                    }
                    imsClient.getLoopGroup()?.execWorkTask(heartbeatTask!!)
                }
            }
        }
    }

    private inner class HeartbeatTask(private val ctx: ChannelHandlerContext) : Runnable {
        override fun run() {
            if (ctx.channel().isActive) {
                val heartbeatMsg = imsClient.getHeartbeatMsg()
                println("发送心跳消息，message=$heartbeatMsg 当前心跳间隔为：${imsClient.getHeartbeatInterval()}ms\n")
                imsClient.sendMsg(heartbeatMsg, false)
            }
        }
    }
}