package com.lws.imlib

import com.lws.imlib.interf.IMSClientInterface
import com.lws.imlib.protobuf.MessageProtobuf
import java.util.concurrent.ConcurrentHashMap

/**
 * 消息发送超时管理器，用于管理消息定时器的新增、移除等
 */
class MsgTimeoutTimerManager(private val imsClient: IMSClientInterface) {
    private val mMsgTimeoutMap: MutableMap<String, MsgTimeoutTimer> = ConcurrentHashMap()

    /**
     * 添加消息到发送超时管理器
     */
    fun add(msg: MessageProtobuf.Msg?) {
        if (msg == null || msg.head == null) {
            return
        }
        val clientReceivedReportMsgType = imsClient.getClientReceivedReportMsgType()
        val handshakeMsg = imsClient.getHandshakeMsg()
        val heartbeatMsg = imsClient.getHeartbeatMsg()
        val handshakeMsgType = handshakeMsg?.head?.msgType ?: -1
        val heartbeatMsgType = heartbeatMsg?.head?.msgType ?: -1

        val msgType = msg.head.msgType
        // 握手消息、心跳消息、客户端返回的状态报告消息，不用重发
        if (msgType == handshakeMsgType || msgType == heartbeatMsgType || msgType == clientReceivedReportMsgType) {
            return
        }

        val msgId = msg.head.msgId
        if (!mMsgTimeoutMap.containsKey(msgId)) {
            val timer = MsgTimeoutTimer(imsClient, msg)
            mMsgTimeoutMap[msgId] = timer
        }
        println("添加消息超发送超时管理器，message=$msg\t当前管理器消息数：${mMsgTimeoutMap.size}")
    }

    /**
     * 从发送超时管理器中移除消息，并停止定时器
     */
    fun remove(msgId: String?) {
        if (msgId.isNullOrEmpty()) {
            return
        }

        val timer = mMsgTimeoutMap.remove(msgId)
        timer?.cancel()
        println("从发送消息管理器移除消息，message=${timer?.getMsg()}")
    }

    /**
     * 重连成功回调，重连并握手成功时，重发消息发送超时管理器中所有的消息
     */
    @Synchronized
    fun onResetConnected() {
        val it = mMsgTimeoutMap.iterator()
        while (it.hasNext()) {
            it.next().value.sendMsg()
        }
    }
}