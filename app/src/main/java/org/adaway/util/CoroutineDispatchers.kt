package org.adaway.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import java.util.concurrent.Executor

object CoroutineDispatchers {
    private val ioExecutor = Dispatchers.IO.asExecutor()
    private val networkExecutor = Dispatchers.IO.asExecutor()
    private val mainExecutor = Dispatchers.Main.immediate.asExecutor()

    @JvmStatic
    fun ioExecutor(): Executor = ioExecutor

    @JvmStatic
    fun networkExecutor(): Executor = networkExecutor

    @JvmStatic
    fun mainExecutor(): Executor = mainExecutor
}
