package com.lws.imlib

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * 线程池工厂，负责重连和心跳线程调度
 */
class ExecutorServiceFactory {
    // 管理线程组，负责重连
    private var bossPool: ExecutorService? = null
    // 工作线程组，负责心跳
    private var workPool: ExecutorService? = null

    @Synchronized
    fun initBossLoopGroup(size: Int = 1) {
        destroyBossLoopGroup()
        bossPool = Executors.newFixedThreadPool(size)
    }

    @Synchronized
    fun initWorkLoopGroup(size: Int = 1) {
        destroyWorkLoopGroup()
        workPool = Executors.newFixedThreadPool(size)
    }

    fun execBossTask(r: Runnable) {
        bossPool ?: initBossLoopGroup()
        bossPool?.execute(r)
    }

    fun execWorkTask(r: Runnable) {
        workPool ?: initWorkLoopGroup()
        workPool?.execute(r)
    }

    /**
     * 释放boss线程池
     */
    @Synchronized
    fun destroyBossLoopGroup() {
        bossPool?.shutdownNow()
        bossPool = null
    }

    /**
     * 释放work线程池
     */
    @Synchronized
    fun destroyWorkLoopGroup() {
        workPool?.shutdownNow()
        workPool = null
    }

    /**
     * 释放所有线程池
     */
    @Synchronized
    fun destroy() {
        destroyBossLoopGroup()
        destroyWorkLoopGroup()
    }
}