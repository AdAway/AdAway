package org.adaway.vpn

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object VpnStatusRepository {
    private val _status = MutableStateFlow(VpnStatus.STOPPED)

    @JvmStatic
    val status: StateFlow<VpnStatus> = _status.asStateFlow()

    @JvmStatic
    fun update(status: VpnStatus) {
        _status.value = status
    }
}
