package com.vsp.core.domain.coroutine

import kotlinx.coroutines.CoroutineDispatcher

/** Injectable coroutine dispatchers to keep logic testable (Constitution Principle V). */
interface DispatcherProvider {
    val io: CoroutineDispatcher
    val default: CoroutineDispatcher
    val main: CoroutineDispatcher
}
