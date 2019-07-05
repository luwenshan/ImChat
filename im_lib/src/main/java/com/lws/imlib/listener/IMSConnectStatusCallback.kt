package com.lws.imlib.listener

/**
 * IMS连接状态回调
 */
interface IMSConnectStatusCallback {
    /**
     * ims连接中
     */
    fun onConnecting()

    /**
     * ims连接成功
     */
    fun onConnected()

    /**
     * ims连接失败
     */
    fun onConnectFailed()
}