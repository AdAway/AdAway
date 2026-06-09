package org.adaway.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.adaway.AdAwayApplication
import org.adaway.db.AppDatabase
import org.adaway.db.dao.HostListItemDao
import org.adaway.db.dao.HostsSourceDao
import org.adaway.helper.NotificationHelper
import org.adaway.helper.PreferenceHelper
import org.adaway.model.adblocking.AdBlockMethod
import org.adaway.model.adblocking.AdBlockModel
import org.adaway.model.error.HostError
import org.adaway.model.error.HostErrorException
import org.adaway.model.source.SourceModel
import org.adaway.model.update.Manifest
import org.adaway.model.update.UpdateModel
import org.adaway.vpn.VpnStatusRepository
import timber.log.Timber

/**
 * This class is an [AndroidViewModel] for the [HomeActivity] cards.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val sourceModel: SourceModel
    private val adBlockModel: AdBlockModel
    private val updateModel: UpdateModel

    private val hostsSourceDao: HostsSourceDao
    private val hostListItemDao: HostListItemDao

    private val _pending = MutableStateFlow(false)
    val pending: StateFlow<Boolean> = _pending

    private val _error = MutableSharedFlow<HostError>()
    val error: SharedFlow<HostError> = _error

    init {
        val awayApplication = application as AdAwayApplication
        sourceModel = awayApplication.sourceModel
        adBlockModel = awayApplication.adBlockModel
        updateModel = awayApplication.updateModel

        val database = AppDatabase.getInstance(application)
        hostsSourceDao = database.hostsSourceDao()
        hostListItemDao = database.hostsListItemDao()

        VpnStatusRepository.update(PreferenceHelper.getVpnServiceStatus(application))
    }

    val state: StateFlow<String> = merge(
        sourceModel.state.asFlow(),
        adBlockModel.state.asFlow()
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(FLOW_STOP_TIMEOUT_MILLIS), "")

    val adBlocked: StateFlow<Boolean> = combine(
        adBlockModel.isApplied.asFlow().map { it == true },
        VpnStatusRepository.status
    ) { applied, vpnStatus ->
        if (adBlockModel.method == AdBlockMethod.VPN) {
            vpnStatus.isStarted
        } else {
            applied
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(FLOW_STOP_TIMEOUT_MILLIS),
        adBlockModel.isApplied.value == true
    )

    val updateAvailable: StateFlow<Boolean> = sourceModel.isUpdateAvailable
        .asFlow()
        .map { it == true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(FLOW_STOP_TIMEOUT_MILLIS), false)

    val versionName: String get() = updateModel.versionName

    val appManifest: StateFlow<Manifest?> = updateModel.manifest
        .asFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(FLOW_STOP_TIMEOUT_MILLIS), updateModel.manifest.value)

    val blockedHostCount: StateFlow<Int> = hostListItemDao.getBlockedHostCount()
        .asFlow()
        .map { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(FLOW_STOP_TIMEOUT_MILLIS), 0)

    val allowedHostCount: StateFlow<Int> = hostListItemDao.getAllowedHostCount()
        .asFlow()
        .map { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(FLOW_STOP_TIMEOUT_MILLIS), 0)

    val redirectHostCount: StateFlow<Int> = hostListItemDao.getRedirectHostCount()
        .asFlow()
        .map { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(FLOW_STOP_TIMEOUT_MILLIS), 0)

    val upToDateSourceCount: StateFlow<Int> = hostsSourceDao.countUpToDate()
        .asFlow()
        .map { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(FLOW_STOP_TIMEOUT_MILLIS), 0)

    val outdatedSourceCount: StateFlow<Int> = hostsSourceDao.countOutdated()
        .asFlow()
        .map { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(FLOW_STOP_TIMEOUT_MILLIS), 0)

    fun checkForAppUpdate() {
        viewModelScope.launch(Dispatchers.IO) {
            updateModel.checkForUpdate()
        }
    }

    fun toggleAdBlocking() {
        if (_pending.value) {
            return
        }
        viewModelScope.launch {
            try {
                _pending.value = true
                withContext(Dispatchers.IO) {
                    if (adBlocked.value) {
                        adBlockModel.revert()
                    } else {
                        adBlockModel.apply()
                    }
                }
            } catch (exception: HostErrorException) {
                Timber.w(exception, "Failed to toggle ad blocking.")
                _error.emit(exception.error)
            } finally {
                _pending.value = false
            }
        }
    }

    fun update() {
        if (_pending.value) {
            return
        }
        viewModelScope.launch {
            try {
                _pending.value = true
                withContext(Dispatchers.IO) {
                    sourceModel.checkForUpdate()
                }
            } catch (exception: HostErrorException) {
                Timber.w(exception, "Failed to update.")
                _error.emit(exception.error)
            } finally {
                _pending.value = false
            }
        }
    }

    fun sync() {
        if (_pending.value) {
            return
        }
        viewModelScope.launch {
            val application = getApplication<Application>()
            try {
                _pending.value = true
                NotificationHelper.showUpdateHostsProgressNotification(application)
                withContext(Dispatchers.IO) {
                    sourceModel.retrieveHostsSources()
                    adBlockModel.apply()
                }
            } catch (exception: HostErrorException) {
                Timber.w(exception, "Failed to sync.")
                _error.emit(exception.error)
            } finally {
                NotificationHelper.clearUpdateHostsProgressNotification(application)
                _pending.value = false
            }
        }
    }

    fun enableAllSources() {
        viewModelScope.launch {
            val enabled = withContext(Dispatchers.IO) {
                sourceModel.enableAllSources()
            }
            if (enabled) {
                sync()
            }
        }
    }

    companion object {
        private const val FLOW_STOP_TIMEOUT_MILLIS = 5_000L
    }
}
