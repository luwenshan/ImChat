package com.lws.imlib

import com.lws.imlib.listener.OnEventListener
import com.lws.imlib.protobuf.MessageProtobuf

/**
 * 消息转发器，负责将接收到的消息转发到应用层
 */
class MsgDispatcher {
    private var mOnEventListener: OnEventListener? = null

    fun setOnEventListener(listener: OnEventListener) {
        this.mOnEventListener = listener
    }

    /**
     * 接收消息，并通过OnEventListener转发消息到应用层
     */
    fun receivedMsg(msg: MessageProtobuf.Msg) {
        mOnEventListener?.dispatchMsg(msg)
    }
}