package com.lws.imlib

/**
 * IMS默认配置，若不使用默认配置，应提供set方法给应用层设置
 */
object IMSConfig {
    // 默认重连一个周期失败间隔时长
    const val DEFAULT_RECONNECT_INTERVAL = 3 * 1000
    // 连接超时时长
    const val DEFAULT_CONNECT_TIMEOUT = 10 * 1000
    // 默认一个周期重连次数
    const val DEFAULT_RECONNECT_COUNT = 3
    // 默认重连起始延时时长，重连规则：最大n次，每次延时n * 起始延时时长，重连次数达到n次后，重置
    const val DEFAULT_RECONNECT_BASE_DELAY_TIME = 3 * 1000
    // 默认消息发送失败重发次数
    const val DEFAULT_RESEND_COUNT = 3
    // 默认消息重发间隔时长
    const val DEFAULT_RESEND_INTERVAL = 8 * 1000
    // 默认应用在前台时心跳消息间隔时长
    const val DEFAULT_HEARTBEAT_INTERVAL_FOREGROUND = 3 * 1000
    // 默认应用在后台时心跳消息间隔时长
    const val DEFAULT_HEARTBEAT_INTERVAL_BACKGROUND = 30 * 1000
    // 应用在前台标识
    const val APP_STATUS_FOREGROUND = 0
    // 应用在后台标识
    const val APP_STATUS_BACKGROUND = -1
    const val KEY_APP_STATUS = "key_app_status"
    // 默认服务端返回的消息发送成功状态报告
    const val DEFAULT_REPORT_SERVER_SEND_MSG_SUCCESSFUL = 1
    // 默认服务端返回的消息发送失败状态报告
    const val DEFAULT_REPORT_SERVER_SEND_MSG_FAILURE = 0
    // ims连接状态：连接中
    const val CONNECT_STATE_CONNECTING = 0
    // ims连接状态：连接成功
    const val CONNECT_STATE_SUCCESSFUL = 1
    // ims连接状态：连接失败
    const val CONNECT_STATE_FAILURE = -1
}