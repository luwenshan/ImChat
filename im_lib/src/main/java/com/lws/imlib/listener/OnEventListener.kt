package com.lws.imlib.listener

import com.lws.imlib.protobuf.MessageProtobuf

/**
 * 与应用层交互的listener
 */
interface OnEventListener {
    /**
     * 分发消息到应用层
     */
    fun dispatchMsg(msg: MessageProtobuf.Msg)

    /**
     * 从应用层获取网络是否可用
     */
    fun isNetworkAvailable(): Boolean

    /**
     * 获取重连间隔时长
     */
    fun getReconnectInterval(): Int

    /**
     * 获取连接超时时长
     */
    fun getConnectTimeout(): Int

    /**
     * 获取应用在前台时心跳间隔时间
     */
    fun getForegroundHeartbeatInterval(): Int

    /**
     * 获取应用在后台时心跳间隔时间
     */
    fun getBackgroundHeartbeatInterval(): Int

    /**
     * 获取由应用层构造的握手消息
     */
    fun getHandshakeMsg(): MessageProtobuf.Msg

    /**
     * 获取由应用层构造的心跳消息
     */
    fun getHeartbeatMsg(): MessageProtobuf.Msg

    /**
     * 获取应用层消息发送状态报告消息类型
     */
    fun getServerSentReportMsgType(): Int

    /**
     * 获取应用层消息接收状态报告消息类型
     */
    fun getClientReceivedReportMsgType(): Int

    /**
     * 获取应用层消息发送超时重发次数
     */
    fun getResendCount(): Int

    /**
     * 获取应用层消息发送超时重发间隔
     */
    fun getResendInterval(): Int
}