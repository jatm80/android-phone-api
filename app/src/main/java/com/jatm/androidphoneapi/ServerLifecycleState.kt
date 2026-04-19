package com.jatm.androidphoneapi

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ServerStatus {
    STOPPED,
    STARTING,
    RUNNING,
    STOPPING,
}

data class ServerLifecycleState(
    val status: ServerStatus = ServerStatus.STOPPED,
) {
    val canStart: Boolean = status == ServerStatus.STOPPED
    val canStop: Boolean = status == ServerStatus.STARTING || status == ServerStatus.RUNNING
    val isRunning: Boolean = status == ServerStatus.RUNNING
}

object ServerLifecycleRepository {
    private val mutableState = MutableStateFlow(ServerLifecycleState())

    val state: StateFlow<ServerLifecycleState> = mutableState.asStateFlow()

    fun markStarting() {
        mutableState.value = ServerLifecycleState(ServerStatus.STARTING)
    }

    fun markRunning() {
        mutableState.value = ServerLifecycleState(ServerStatus.RUNNING)
    }

    fun markStopping() {
        mutableState.value = ServerLifecycleState(ServerStatus.STOPPING)
    }

    fun markStopped() {
        mutableState.value = ServerLifecycleState(ServerStatus.STOPPED)
    }
}
