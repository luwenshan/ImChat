package com.lws.imlib.interf

import com.lws.imlib.MsgDispatcher
import com.lws.imlib.MsgTimeoutTimerManager
import com.lws.imlib.listener.IMSConnectStatusCallback
import com.lws.imlib.listener.OnEventListener
import com.lws.imlib.protobuf.MessageProtobuf

/**
 * ims抽象接口，需要切换到其它方式实现im功能，实现此接口即可
 */
interface IMSClientInterface {
    /**
     * 初始化
     *
     * @param serverUrlList 服务器地址列表
     * @param listener      与应用层交互的listener
     * @param callback      ims连接状态回调
     */
    fun init(serverUrlList: List<String>, listener: OnEventListener, callback: IMSConnectStatusCallback)

    /**
     * 重置连接，也就是重连
     * 首次连接也可认为是重连
     */
    fun resetConnect()

    /**
     * 重置连接，也就是重连
     * 首次连接也可认为是重连
     *
     * @param isFirst 是否首次连接
     */
    fun resetConnect(isFirst: Boolean)

    /**
     * 关闭连接，同时释放资源
     */
    fun close()

    /**
     * 标识ims是否已关闭
     */
    fun isClosed(): Boolean

    /**
     * 发送消息
     */
    fun sendMsg(msg: MessageProtobuf.Msg)

    /**
     * 发送消息
     *
     * @param msg
     * @param isJoinTimeoutManager 是否加入发送超时管理器
     */
    fun sendMsg(msg: MessageProtobuf.Msg, isJoinTimeoutManager: Boolean)

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
     * 设置app前后台状态
     */
    fun setAppStatus(appStatus: Int)

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

    /**
     * 获取消息转发器
     */
    fun getMsgDispatcher(): MsgDispatcher

    /**
     * 获取消息发送超时定时器
     */
    fun getMsgTimeoutTimerManager(): MsgTimeoutTimerManager?
}