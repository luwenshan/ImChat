package com.lws.imlib

import com.lws.imlib.interf.IMSClientInterface
import com.lws.imlib.protobuf.MessageProtobuf
import java.util.*

/**
 * 消息发送超时定时器，每一条消息对应一个定时器
 */
class MsgTimeoutTimer(
    // ims客户端
    private val imsClient: IMSClientInterface,
    // 发送的消息
    private val msg: MessageProtobuf.Msg
) : Timer() {

    // 当前重发次数
    private var currentResendCount: Int = 0
    // 消息发送超时任务
    private var task: MsgTimeoutTask? = MsgTimeoutTask()

    init {
        this.schedule(task, imsClient.getResendInterval().toLong(), imsClient.getResendInterval().toLong())
    }

    fun sendMsg() {
        println("正在重发消息，message=$msg")
        imsClient.sendMsg(msg, false)
    }

    fun getMsg(): MessageProtobuf.Msg {
        return msg
    }

    override fun cancel() {
        task?.cancel()
        task = null
        super.cancel()
    }

    /**
     * 消息发送超时任务
     */
    private inner class MsgTimeoutTask : TimerTask() {
        override fun run() {
            if (imsClient.isClosed()) {
                imsClient.getMsgTimeoutTimerManager()?.remove(msg.head.msgId)
                return
            }
            currentResendCount++
            if (currentResendCount > imsClient.getResendCount()) {
                // 重发次数大于可重发次数，直接标识为发送失败，并通过消息转发器通知应用层
                try {
                    val builder = MessageProtobuf.Msg.newBuilder()
                    val headBuilder = MessageProtobuf.Head.newBuilder()
                    headBuilder.msgId = msg.head.msgId
                    headBuilder.msgType = imsClient.getServerSentReportMsgType()
                    headBuilder.timestamp = System.currentTimeMillis()
                    headBuilder.statusReport = IMSConfig.DEFAULT_REPORT_SERVER_SEND_MSG_FAILURE
                    builder.head = headBuilder.build()

                    // 通知应用层消息发送失败
                    imsClient.getMsgDispatcher().receivedMsg(builder.build())
                } finally {
                    // 从消息发送超时管理器移除该消息
                    imsClient.getMsgTimeoutTimerManager()?.remove(msg.head.msgId)
                    // 执行到这里，认为连接已断开或不稳定，触发重连
                    imsClient.resetConnect()
                    currentResendCount = 0
                }
            } else {
                sendMsg()
            }
        }
    }
}