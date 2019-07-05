package com.lws.imlib.netty

import com.alibaba.fastjson.JSONObject
import com.lws.imlib.IMSConfig
import com.lws.imlib.protobuf.MessageProtobuf
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import java.util.*

/**
 * 消息接收处理handler
 */
class TCPReadHandler(private val imsClient: NettyTcpClient) : ChannelInboundHandlerAdapter() {
    override fun channelInactive(ctx: ChannelHandlerContext) {
        super.channelInactive(ctx)
        println("TCPReadHandler channelInactive()")
        val channel = ctx.channel()
        channel.close()
        ctx.close()

        // 触发重连
        imsClient.resetConnect(false)
    }

    override fun channelRead(ctx: ChannelHandlerContext, msg: Any?) {
        val message = msg as? MessageProtobuf.Msg
        if (message == null || message.head == null) {
            return
        }

        val msgType = message.head.msgType
        if (msgType == imsClient.getServerSentReportMsgType()) {
            val statusReport = message.head.statusReport
            println(String.format("服务端状态报告：「%d」, 1代表成功，0代表失败", statusReport))
            if (statusReport == IMSConfig.DEFAULT_REPORT_SERVER_SEND_MSG_SUCCESSFUL) {
                println("收到服务端消息发送状态报告，message=$message，从超时管理器移除")
                imsClient.getMsgTimeoutTimerManager().remove(message.head.msgId)
            }
        } else { // 其它消息
            // 收到消息后，立马给服务端回一条消息接收状态报告
            println("收到消息，message=$message")
            val receivedReportMsg = buildReceivedReportMsg(message.head.msgId)
            receivedReportMsg?.also { imsClient.sendMsg(it) }
        }

        // 接收消息，由消息转发器转发到应用层
        imsClient.getMsgDispatcher().receivedMsg(message)
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        super.exceptionCaught(ctx, cause)
        println("TCPReadHandler exceptionCaught()")
        ctx.channel().close()
        ctx.close()

        // 触发重连
        imsClient.resetConnect(false)
    }

    /**
     * 构建客户端消息接收状态报告
     */
    private fun buildReceivedReportMsg(msgId: String?): MessageProtobuf.Msg? {
        if (msgId.isNullOrEmpty()) {
            return null
        }

        val builder = MessageProtobuf.Msg.newBuilder()
        val headBuilder = MessageProtobuf.Head.newBuilder()
        headBuilder.msgId = UUID.randomUUID().toString()
        headBuilder.msgType = imsClient.getClientReceivedReportMsgType()
        headBuilder.timestamp = System.currentTimeMillis()
        val jsonObj = JSONObject()
        jsonObj["msgId"] = msgId
        headBuilder.extend = jsonObj.toString()
        builder.head = headBuilder.build()

        return builder.build()
    }
}