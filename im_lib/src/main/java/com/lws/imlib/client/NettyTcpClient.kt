package com.lws.imlib.client

import com.lws.imlib.ExecutorServiceFactory
import com.lws.imlib.IMSConfig
import com.lws.imlib.MsgDispatcher
import com.lws.imlib.MsgTimeoutTimerManager
import com.lws.imlib.handler.HeartbeatHandler
import com.lws.imlib.handler.TCPChannelInitializerHandler
import com.lws.imlib.handler.TCPReadHandler
import com.lws.imlib.interf.IMSClientInterface
import com.lws.imlib.listener.IMSConnectStatusCallback
import com.lws.imlib.listener.OnEventListener
import com.lws.imlib.protobuf.MessageProtobuf
import io.netty.bootstrap.Bootstrap
import io.netty.channel.Channel
import io.netty.channel.ChannelOption
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.timeout.IdleStateHandler
import java.util.concurrent.TimeUnit

/**
 * 基于netty实现的tcp ims
 */
class NettyTcpClient private constructor() : IMSClientInterface {

    companion object {
        // 单例模式
        val instance: NettyTcpClient by lazy(mode = LazyThreadSafetyMode.SYNCHRONIZED) { NettyTcpClient() }
    }

    private var bootstrap: Bootstrap? = null
    private var channel: Channel? = null

    private var isClosed: Boolean = false // 标识ims是否已关闭
    private var serverUrlList: MutableList<String?>? = null // ims服务器地址组
    private var mOnEventListener: OnEventListener? = null // 与应用层交互的listener
    private var mIMSConnectStatusCallback: IMSConnectStatusCallback? = null // ims连接状态回调监听器
    private lateinit var msgDispatcher: MsgDispatcher // 消息转发器
    private var loopGroup: ExecutorServiceFactory? = null // 线程池工厂

    // 是否正在进行重连
    private var isReconnecting = false
    // ims连接状态，初始化为连接失败
    private var connectStatus = IMSConfig.CONNECT_STATE_FAILURE
    // 重连间隔时长
    private var reconnectInterval = IMSConfig.DEFAULT_RECONNECT_BASE_DELAY_TIME
    // 连接超时时长
    private var connectTimeout = IMSConfig.DEFAULT_CONNECT_TIMEOUT
    // 心跳间隔时间
    private var heartbeatInterval = IMSConfig.DEFAULT_HEARTBEAT_INTERVAL_FOREGROUND
    // 应用在后台时心跳间隔时间
    private var foregroundHeartbeatInterval = IMSConfig.DEFAULT_HEARTBEAT_INTERVAL_FOREGROUND
    // 应用在前台时心跳间隔时间
    private var backgroundHeartbeatInterval = IMSConfig.DEFAULT_HEARTBEAT_INTERVAL_BACKGROUND
    // app前后台状态
    private var appStatus = IMSConfig.APP_STATUS_FOREGROUND
    // 消息发送超时重发次数
    private var resendCount = IMSConfig.DEFAULT_RESEND_COUNT
    // 消息发送失败重发间隔时长
    private var resendInterval = IMSConfig.DEFAULT_RESEND_INTERVAL

    // 当前连接host
    private var currentHost: String? = null
    // 当前连接port
    private var currentPort = -1

    // 消息发送超时定时器管理
    private lateinit var msgTimeoutTimerManager: MsgTimeoutTimerManager

    /**
     * 初始化
     *
     * @param serverUrlList 服务器地址列表
     * @param listener      与应用层交互的listener
     * @param callback      ims连接状态回调
     */
    override fun init(serverUrlList: MutableList<String?>?, listener: OnEventListener, callback: IMSConnectStatusCallback) {
        close()
        isClosed = false
        this.serverUrlList = serverUrlList
        this.mOnEventListener = listener
        this.mIMSConnectStatusCallback = callback
        msgDispatcher = MsgDispatcher()
        msgDispatcher.setOnEventListener(listener)
        loopGroup = ExecutorServiceFactory()
        // 初始化重连线程组
        loopGroup?.initBossLoopGroup()
        msgTimeoutTimerManager = MsgTimeoutTimerManager(this)

        // 进行第一次连接
        resetConnect(true)
    }

    override fun resetConnect() {
        resetConnect(false)
    }

    override fun resetConnect(isFirst: Boolean) {
        if (!isFirst) {
            try {
                Thread.sleep(IMSConfig.DEFAULT_RECONNECT_INTERVAL.toLong())
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
        }

        // 只有第一个调用者才能赋值并调用重连
        if (!isClosed && !isReconnecting) {
            synchronized(this) {
                if (!isClosed && !isReconnecting) {
                    // 标识正在进行重连
                    isReconnecting = true
                    // 回调ims连接状态
                    onConnectStatusCallback(IMSConfig.CONNECT_STATE_CONNECTING)
                    // 先关闭channel
                    closeChannel()
                    // 执行重连任务
                    loopGroup?.execBossTask(ResetConnectRunnable(isFirst))
                }
            }
        }
    }

    override fun close() {
        if (isClosed) {
            return
        }
        isClosed = true

        // 关闭channel
        try {
            closeChannel()
        } catch (ex: Exception) {
            ex.printStackTrace()
        }

        // 关闭bootstrap
        try {
            bootstrap?.group()?.shutdownGracefully()
        } catch (ex: Exception) {
            ex.printStackTrace()
        }

        try {
            // 释放线程池
            loopGroup?.destroy()
        } catch (ex: Exception) {
            ex.printStackTrace()
        } finally {
            serverUrlList?.clear()
            isReconnecting = false
            channel = null
            bootstrap = null
        }
    }

    override fun isClosed(): Boolean = isClosed

    override fun sendMsg(msg: MessageProtobuf.Msg?) {
        sendMsg(msg, true)
    }

    override fun sendMsg(msg: MessageProtobuf.Msg?, isJoinTimeoutManager: Boolean) {
        if (msg == null || msg.head == null) {
            println("发送消息失败，消息为空\tmessage=$msg")
            return
        }
        if (!msg.head.msgId.isNullOrEmpty()) {
            if (isJoinTimeoutManager) {
                msgTimeoutTimerManager.add(msg)
            }
        }

        if (channel == null) {
            println("发送消息失败，channel为空\tmessage=$msg")
        }

        try {
            channel?.writeAndFlush(msg)
        } catch (ex: Exception) {
            println("发送消息失败，reason:" + ex.message + "\tmessage=" + msg)
        }

    }

    override fun getReconnectInterval(): Int {
        mOnEventListener?.let {
            if (it.getReconnectInterval() > 0) {
                reconnectInterval = it.getReconnectInterval()
            }
        }
        return reconnectInterval
    }

    override fun getConnectTimeout(): Int {
        mOnEventListener?.also {
            if (it.getConnectTimeout() > 0) {
                connectTimeout = it.getConnectTimeout()
            }
        }
        return connectTimeout
    }

    /**
     * 获取应用在前台时心跳间隔时间
     */
    override fun getForegroundHeartbeatInterval(): Int {
        mOnEventListener?.also {
            if (it.getForegroundHeartbeatInterval() > 0) {
                foregroundHeartbeatInterval = it.getForegroundHeartbeatInterval()
            }
        }
        return foregroundHeartbeatInterval
    }

    /**
     * 获取应用在后台时心跳间隔时间
     */
    override fun getBackgroundHeartbeatInterval(): Int {
        mOnEventListener?.also {
            if (it.getBackgroundHeartbeatInterval() > 0) {
                backgroundHeartbeatInterval = it.getBackgroundHeartbeatInterval()
            }
        }
        return backgroundHeartbeatInterval
    }

    /**
     * 设置app前后台状态
     */
    override fun setAppStatus(appStatus: Int) {
        this.appStatus = appStatus
        if (this.appStatus == IMSConfig.APP_STATUS_FOREGROUND) {
            heartbeatInterval = foregroundHeartbeatInterval
        } else if (this.appStatus == IMSConfig.APP_STATUS_BACKGROUND) {
            heartbeatInterval = backgroundHeartbeatInterval
        }

        addHeartbeatHandler()
    }

    override fun getHandshakeMsg(): MessageProtobuf.Msg? = mOnEventListener?.getHandshakeMsg()

    override fun getHeartbeatMsg(): MessageProtobuf.Msg? = mOnEventListener?.getHeartbeatMsg()

    override fun getServerSentReportMsgType(): Int = mOnEventListener?.getServerSentReportMsgType() ?: 0

    override fun getClientReceivedReportMsgType(): Int = mOnEventListener?.getClientReceivedReportMsgType() ?: 0

    /**
     * 获取心跳间隔时间
     */
    fun getHeartbeatInterval(): Int = heartbeatInterval

    override fun getResendCount(): Int {
        mOnEventListener?.also {
            if (it.getResendCount() != 0) {
                resendCount = it.getResendCount()
            }
        }
        return resendCount
    }

    /**
     * 获取应用层消息发送超时重发间隔
     */
    override fun getResendInterval(): Int {
        mOnEventListener?.also {
            if (it.getResendInterval() != 0) {
                resendInterval = it.getResendInterval()
            }
        }
        return resendInterval
    }

    /**
     * 获取线程池
     */
    fun getLoopGroup(): ExecutorServiceFactory? = loopGroup

    override fun getMsgDispatcher(): MsgDispatcher = msgDispatcher

    override fun getMsgTimeoutTimerManager(): MsgTimeoutTimerManager = msgTimeoutTimerManager

    /**
     * 初始化bootstrap
     */
    private fun initBootstrap() {
        val loopGroup = NioEventLoopGroup(4)
        bootstrap = Bootstrap()
        bootstrap?.apply {
            group(loopGroup).channel(NioSocketChannel::class.java)
            // 设置该选项以后，如果在两小时内没有数据的通信时，TCP会自动发送一个活动探测数据报文
            option(ChannelOption.SO_KEEPALIVE, true)
            // 设置禁用nagle算法
            option(ChannelOption.TCP_NODELAY, true)
            // 设置连接超时时长
            option(ChannelOption.CONNECT_TIMEOUT_MILLIS, getConnectTimeout())
            // 设置初始化Channel
            handler(TCPChannelInitializerHandler(this@NettyTcpClient))
        }
    }

    /**
     * 回调ims连接状态
     */
    private fun onConnectStatusCallback(connectStatus: Int) {
        this.connectStatus = connectStatus
        when (connectStatus) {
            IMSConfig.CONNECT_STATE_CONNECTING -> {
                println("ims连接中...")
                mIMSConnectStatusCallback?.onConnecting()
            }
            IMSConfig.CONNECT_STATE_SUCCESSFUL -> {
                println("ims连接成功，host『${currentHost}』, port『${currentPort}』")
                mIMSConnectStatusCallback?.onConnected()
                // 连接成功，发送握手消息
                val handshakeMsg = getHandshakeMsg()
                println("发送握手消息，message=$handshakeMsg")
                sendMsg(handshakeMsg, false)
            }
            else -> {
                println("ims连接失败")
                mIMSConnectStatusCallback?.onConnectFailed()
            }
        }
    }

    /**
     * 添加心跳消息管理handler
     */
    fun addHeartbeatHandler() {
        channel?.also {
            if (channel == null || !it.isActive || it.pipeline() == null) {
                return
            }

            try {
                // 之前存在的读写超时handler，先移除掉，再重新添加
                if (it.pipeline().get(IdleStateHandler::class.java.simpleName) != null) {
                    it.pipeline().remove(IdleStateHandler::class.java.simpleName)
                }
                // 3次心跳没响应，代表连接已断开
                it.pipeline().addFirst(
                    IdleStateHandler::class.java.simpleName, IdleStateHandler(
                        (heartbeatInterval * 3).toLong(), heartbeatInterval.toLong(), 0, TimeUnit.MILLISECONDS
                    )
                )

                // 重新添加HeartbeatHandler
                if (it.pipeline().get(HeartbeatHandler::class.java.simpleName) != null) {
                    it.pipeline().remove(HeartbeatHandler::class.java.simpleName)
                }
                if (it.pipeline().get(TCPReadHandler::class.java.simpleName) != null) {
                    it.pipeline().addBefore(TCPReadHandler::class.java.simpleName, HeartbeatHandler::class.java.simpleName, HeartbeatHandler(this))
                }
            } catch (e: Exception) {
                e.printStackTrace()
                System.err.println("添加心跳消息管理handler失败，reason：" + e.message)
            }
        }
    }

    /**
     * 移除指定handler
     */
    private fun removeHandler(handlerName: String) {
        try {
            channel?.apply {
                if (pipeline().get(handlerName) != null) {
                    pipeline().remove(handlerName)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            System.err.println("移除handler失败，handlerName=$handlerName")
        }
    }

    /**
     * 从应用层获取网络是否可用
     */
    private fun isNetworkAvailable(): Boolean {
        return mOnEventListener?.isNetworkAvailable() ?: false
    }

    /**
     * 关闭channel
     */
    private fun closeChannel() {
        try {
            channel?.apply {
                close()
                eventLoop().shutdownGracefully()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            System.err.println("关闭channel出错，reason:" + e.message)
        } finally {
            channel = null
        }
    }

    /**
     * 真正连接服务器的地方
     */
    private fun toServer() {
        try {
            channel = bootstrap?.connect(currentHost, currentPort)?.sync()?.channel()
        } catch (e: Exception) {
            try {
                Thread.sleep(500)
            } catch (e1: InterruptedException) {
                e1.printStackTrace()
            }

            System.err.println(String.format("连接Server(ip[%s], port[%s])失败", currentHost, currentPort))
            channel = null
        }
    }

    /**
     * 重连任务
     */
    private inner class ResetConnectRunnable(private val isFirst: Boolean) : Runnable {
        override fun run() {
            // 非首次进行重连，执行到这里即代表已经连接失败，回调连接状态到应用层
            if (!isFirst) {
                onConnectStatusCallback(IMSConfig.CONNECT_STATE_FAILURE)
            }

            try {
                // 重连时，释放工作线程组，也就是停止心跳
                loopGroup?.destroyWorkLoopGroup()

                while (!isClosed) {
                    if (!isNetworkAvailable()) {
                        try {
                            Thread.sleep(2000)
                        } catch (e: InterruptedException) {
                            e.printStackTrace()
                        }
                        continue
                    }

                    // 网络可用才进行连接
                    val status: Int = reConnect()
                    if (status == IMSConfig.CONNECT_STATE_SUCCESSFUL) {
                        onConnectStatusCallback(status)
                        // 连接成功，跳出循环
                        break
                    }

                    if (status == IMSConfig.CONNECT_STATE_FAILURE) {
                        onConnectStatusCallback(status)
                        try {
                            Thread.sleep(IMSConfig.DEFAULT_RECONNECT_INTERVAL.toLong())
                        } catch (e: InterruptedException) {
                            e.printStackTrace()
                        }
                    }
                }
            } finally {
                // 标识重连任务停止
                isReconnecting = false
            }
        }

        /**
         * 重连，首次连接也认为是第一次重连
         */
        private fun reConnect(): Int {
            // 未关闭才去连接
            if (!isClosed) {
                try {
                    // 先释放EventLoop线程组
                    bootstrap?.group()?.shutdownGracefully()
                } finally {
                    bootstrap = null
                }

                // 初始化bootstrap
                initBootstrap()
                return connectServer()
            }
            return IMSConfig.CONNECT_STATE_FAILURE
        }

        /**
         * 连接服务器
         *
         * @return
         */
        private fun connectServer(): Int {
            // 如果服务器地址无效，直接回调连接状态，不再进行连接
            // 有效的服务器地址示例：127.0.0.1 8860
            if (serverUrlList == null || serverUrlList!!.size == 0) {
                return IMSConfig.CONNECT_STATE_FAILURE
            }

            var i = 0
            while (!isClosed && i < serverUrlList!!.size) {
                val serverUrl = serverUrlList!![i]
                // 如果服务器地址无效，直接回调连接状态，不再进行连接
                if (serverUrl.isNullOrEmpty()) {
                    return IMSConfig.CONNECT_STATE_FAILURE
                }

                val address = serverUrl.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                for (j in 1..IMSConfig.DEFAULT_RECONNECT_COUNT) {
                    // 如果ims已关闭，或网络不可用，直接回调连接状态，不再进行连接
                    if (isClosed || !isNetworkAvailable()) {
                        return IMSConfig.CONNECT_STATE_FAILURE
                    }

                    // 回调连接状态
                    if (connectStatus != IMSConfig.CONNECT_STATE_CONNECTING) {
                        onConnectStatusCallback(IMSConfig.CONNECT_STATE_CONNECTING)
                    }
                    println(String.format("正在进行『%s』的第『%d』次连接，当前重连延时时长为『%dms』", serverUrl, j, j * getReconnectInterval()))

                    try {
                        currentHost = address[0]// 获取host
                        currentPort = Integer.parseInt(address[1])// 获取port
                        toServer()// 连接服务器

                        // channel不为空，即认为连接已成功
                        if (channel != null) {
                            return IMSConfig.CONNECT_STATE_SUCCESSFUL
                        } else {
                            // 连接失败，则线程休眠n * 重连间隔时长
                            Thread.sleep((j * getReconnectInterval()).toLong())
                        }
                    } catch (e: InterruptedException) {
                        close()
                        break// 线程被中断，则强制关闭
                    }
                }
                i++
            }

            // 执行到这里，代表连接失败
            return IMSConfig.CONNECT_STATE_FAILURE
        }
    }
}