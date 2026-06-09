package org.adaway.ui.adblocking

import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.snackbar.Snackbar.LENGTH_INDEFINITE
import com.google.android.material.snackbar.Snackbar.LENGTH_LONG
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.adaway.AdAwayApplication
import org.adaway.R
import org.adaway.model.error.HostErrorException
import org.adaway.util.ExpressiveSnackbar

class ApplyConfigurationSnackbar(
    private val view: View,
    private val syncSources: Boolean,
    private val ignoreEventDuringInstall: Boolean
) {
    private val notifySnackbar: Snackbar = Snackbar
        .make(view, R.string.notification_configuration_changed, LENGTH_INDEFINITE)
        .setAction(R.string.notification_configuration_changed_action) { applyConfiguration() }
    private val waitSnackbar: Snackbar = Snackbar
        .make(view, R.string.notification_configuration_installing, LENGTH_INDEFINITE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var update = false
    private var skipUpdate = false

    init {
        ExpressiveSnackbar.style(notifySnackbar)
        ExpressiveSnackbar.style(waitSnackbar)
        appendViewToSnackbar(waitSnackbar, ProgressBar(view.context))
    }

    fun notifyUpdateAvailable() {
        if (notifySnackbar.isShown) {
            return
        }
        if (waitSnackbar.isShown) {
            update = true
            return
        }
        if (skipUpdate) {
            skipUpdate = false
            return
        }
        notifySnackbar.show()
        update = false
    }

    private fun applyConfiguration() {
        showLoading()
        scope.launch {
            val successful = withContext(Dispatchers.IO) {
                val application = view.context.applicationContext as AdAwayApplication
                val sourceModel = application.sourceModel
                val adBlockModel = application.adBlockModel
                try {
                    if (syncSources) {
                        sourceModel.retrieveHostsSources()
                    } else {
                        sourceModel.syncHostEntries()
                    }
                    adBlockModel.apply()
                    true
                } catch (_: HostErrorException) {
                    false
                }
            }
            endLoading(successful)
        }
    }

    private fun showLoading() {
        notifySnackbar.dismiss()
        waitSnackbar.show()
    }

    private fun endLoading(successfulInstall: Boolean) {
        waitSnackbar.dismiss()
        if (!successfulInstall) {
            val failureSnackbar = Snackbar.make(view, R.string.notification_configuration_failed, LENGTH_LONG)
            ExpressiveSnackbar.style(failureSnackbar)
            val icon = ImageView(view.context).apply {
                setImageResource(R.drawable.ic_error_outline_24dp)
            }
            appendViewToSnackbar(failureSnackbar, icon)
            failureSnackbar.show()
        } else if (update) {
            if (ignoreEventDuringInstall) {
                skipUpdate = true
            } else {
                notifyUpdateAvailable()
            }
        }
    }

    private fun appendViewToSnackbar(snackbar: Snackbar, view: View) {
        val viewGroup = snackbar.view
            .findViewById<View>(com.google.android.material.R.id.snackbar_text)
            .parent as ViewGroup
        viewGroup.addView(view)
    }
}
