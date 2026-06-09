package org.adaway.ui.update

import android.app.Application
import android.app.DownloadManager
import android.app.DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR
import android.app.DownloadManager.COLUMN_STATUS
import android.app.DownloadManager.COLUMN_TOTAL_SIZE_BYTES
import android.app.DownloadManager.STATUS_FAILED
import android.app.DownloadManager.STATUS_RUNNING
import android.app.DownloadManager.STATUS_SUCCESSFUL
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.adaway.AdAwayApplication
import org.adaway.helper.NotificationHelper
import org.adaway.model.update.Manifest
import org.adaway.ui.adware.AdwareViewModel
import timber.log.Timber

class UpdateViewModel(application: Application) : AdwareViewModel(application) {
    private val updateModel = (application as AdAwayApplication).updateModel

    val appManifest: StateFlow<Manifest?> = updateModel.manifest
        .asFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(FLOW_STOP_TIMEOUT_MILLIS), updateModel.manifest.value)

    private val _downloadProgress = MutableStateFlow<DownloadStatus?>(null)
    val downloadProgress: StateFlow<DownloadStatus?> = _downloadProgress

    private val _downloading = MutableStateFlow(false)
    val downloading: StateFlow<Boolean> = _downloading

    fun update() {
        _downloadProgress.value = null
        _downloading.value = true
        val downloadId = updateModel.update()
        if (downloadId == -1L) {
            _downloading.value = false
            return
        }
        NotificationHelper.showUpdateApplicationProgressNotification(getApplication())
        viewModelScope.launch(Dispatchers.IO) {
            trackProgress(downloadId)
        }
    }

    private suspend fun trackProgress(downloadId: Long) {
        val downloadManager = getApplication<Application>().getSystemService(DownloadManager::class.java)
        val query = DownloadManager.Query().setFilterById(downloadId)
        var finishDownload = false
        while (!finishDownload) {
            delay(100)
            downloadManager.query(query).use { cursor ->
                if (!cursor.moveToFirst()) {
                    Timber.d("Download item was not found")
                    return@use
                }
                when (cursor.getInt(cursor.getColumnIndex(COLUMN_STATUS))) {
                    STATUS_FAILED -> {
                        finishDownload = true
                        _downloadProgress.value = null
                        _downloading.value = false
                        NotificationHelper.clearUpdateApplicationProgressNotification(getApplication())
                    }

                    STATUS_RUNNING -> {
                        val total = cursor.getLong(cursor.getColumnIndex(COLUMN_TOTAL_SIZE_BYTES))
                        if (total > 0) {
                            val downloaded = cursor.getLong(cursor.getColumnIndex(COLUMN_BYTES_DOWNLOADED_SO_FAR))
                            val status = PendingDownloadStatus(downloaded, total)
                            _downloadProgress.value = status
                            NotificationHelper.showUpdateApplicationProgressNotification(
                                getApplication(),
                                status.progress,
                                status.format(getApplication())
                            )
                        }
                    }

                    STATUS_SUCCESSFUL -> {
                        _downloadProgress.value = CompleteDownloadStatus()
                        _downloading.value = false
                        finishDownload = true
                        NotificationHelper.clearUpdateApplicationProgressNotification(getApplication())
                    }
                }
            }
        }
    }

    companion object {
        private const val FLOW_STOP_TIMEOUT_MILLIS = 5_000L
    }
}
