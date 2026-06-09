package org.adaway.vpn

import android.Manifest.permission.POST_NOTIFICATIONS
import android.app.Notification
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.app.Service.STOP_FOREGROUND_REMOVE
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.ConnectivityManager.NetworkCallback
import android.net.Network
import android.net.NetworkCapabilities.TRANSPORT_CELLULAR
import android.net.NetworkCapabilities.TRANSPORT_WIFI
import android.net.NetworkRequest
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Message
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import org.adaway.R
import org.adaway.broadcast.Command
import org.adaway.broadcast.Command.START
import org.adaway.broadcast.Command.STOP
import org.adaway.broadcast.CommandReceiver
import org.adaway.broadcast.CommandReceiver.Companion.SEND_COMMAND_ACTION
import org.adaway.helper.NotificationHelper.VPN_RESUME_SERVICE_NOTIFICATION_ID
import org.adaway.helper.NotificationHelper.VPN_RUNNING_SERVICE_NOTIFICATION_ID
import org.adaway.helper.NotificationHelper.VPN_SERVICE_NOTIFICATION_CHANNEL
import org.adaway.helper.PreferenceHelper
import org.adaway.ui.home.HomeActivity
import org.adaway.vpn.VpnStatus.RECONNECTING
import org.adaway.vpn.VpnStatus.RUNNING
import org.adaway.vpn.VpnStatus.STARTING
import org.adaway.vpn.VpnStatus.STOPPED
import org.adaway.vpn.VpnStatus.WAITING_FOR_NETWORK
import org.adaway.vpn.worker.VpnWorker
import timber.log.Timber
import java.lang.ref.WeakReference

class VpnService : android.net.VpnService(), Handler.Callback {
    private val handler = MyHandler(this)
    private val wifiNetworkCallback = NetworkTypeCallback(NetworkType.WIFI)
    private val cellularNetworkCallback = NetworkTypeCallback(NetworkType.CELLULAR)
    private val availableNetworkTypes = mutableSetOf<NetworkType>()
    private val vpnWorker = VpnWorker(this)

    override fun onCreate() {
        Timber.d("Creating VPN service...")
        registerNetworkCallback()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.d("onStartCommand %s", intent ?: "null intent")
        return when (val command = intent?.let(Command::readFromIntent) ?: START) {
            START -> {
                startVpn()
                START_STICKY
            }

            STOP -> {
                stopVpn()
                START_NOT_STICKY
            }

            Command.UNKNOWN -> {
                Timber.w("Unknown command: %s", command)
                START_NOT_STICKY
            }
        }
    }

    override fun onDestroy() {
        Timber.d("Destroying VPN service...")
        unregisterNetworkCallback()
        Timber.d("Destroyed VPN service.")
    }

    override fun handleMessage(message: Message): Boolean {
        if (message.what == VPN_STATUS_UPDATE_MESSAGE_TYPE) {
            updateVpnStatus(VpnStatus.fromCode(message.arg1))
        }
        return true
    }

    fun notifyVpnStatus(status: VpnStatus) {
        val statusMessage = handler.obtainMessage(VPN_STATUS_UPDATE_MESSAGE_TYPE, status.toCode(), 0)
        handler.sendMessage(statusMessage)
    }

    private fun startVpn() {
        Timber.d("Starting VPN service...")
        PreferenceHelper.setVpnServiceStatus(this, RUNNING)
        updateVpnStatus(STARTING)
        vpnWorker.start()
        Timber.i("VPN service started.")
    }

    private fun stopVpn() {
        Timber.d("Stopping VPN service...")
        PreferenceHelper.setVpnServiceStatus(this, STOPPED)
        vpnWorker.stop()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        updateVpnStatus(STOPPED)
        Timber.i("VPN service stopped.")
    }

    private fun waitForNetVpn() {
        vpnWorker.stop()
        updateVpnStatus(WAITING_FOR_NETWORK)
    }

    private fun reconnect() {
        updateVpnStatus(RECONNECTING)
        vpnWorker.start()
    }

    private fun updateVpnStatus(status: VpnStatus) {
        val notification = getNotification(status)
        val notificationManager = NotificationManagerCompat.from(this)
        when (status) {
            STARTING, RUNNING -> {
                notificationManager.cancel(VPN_RESUME_SERVICE_NOTIFICATION_ID)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startForeground(
                        VPN_RUNNING_SERVICE_NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                    )
                } else {
                    startForeground(VPN_RUNNING_SERVICE_NOTIFICATION_ID, notification)
                }
            }

            else -> {
                if (checkSelfPermission(POST_NOTIFICATIONS) == PERMISSION_GRANTED) {
                    notificationManager.notify(VPN_RESUME_SERVICE_NOTIFICATION_ID, notification)
                }
            }
        }
        VpnStatusRepository.update(status)
    }

    private fun getNotification(status: VpnStatus): Notification {
        val title = getString(R.string.vpn_notification_title, getString(status.textResource))
        val intent = Intent(applicationContext, HomeActivity::class.java)
            .setFlags(FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_CLEAR_TASK)
        val contentIntent = PendingIntent.getActivity(applicationContext, 0, intent, FLAG_IMMUTABLE)

        val builder = NotificationCompat.Builder(this, VPN_SERVICE_NOTIFICATION_CHANNEL)
            .setPriority(android.app.NotificationManager.IMPORTANCE_LOW)
            .setContentIntent(contentIntent)
            .setSmallIcon(R.drawable.logo)
            .setColor(getColor(R.color.notification))
            .setContentTitle(title)

        if (Build.VERSION.SDK_INT >= 36) {
            val notificationManager = getSystemService(android.app.NotificationManager::class.java)
            if (notificationManager != null && notificationManager.canPostPromotedNotifications()) {
                builder.extras.putBoolean("android.requestPromotedOngoing", true)
                builder.setColorized(false)
            } else {
                builder.setColorized(true)
            }
        } else {
            builder.setColorized(true)
        }

        when (status) {
            RUNNING -> {
                val stopIntent = Intent(this, CommandReceiver::class.java)
                    .setAction(SEND_COMMAND_ACTION)
                STOP.appendToIntent(stopIntent)
                val stopActionIntent = PendingIntent.getBroadcast(this, REQUEST_CODE_PAUSE, stopIntent, FLAG_IMMUTABLE)
                builder.addAction(
                    R.drawable.ic_pause_24dp,
                    getString(R.string.vpn_notification_action_pause),
                    stopActionIntent
                ).setOngoing(true)
            }

            STOPPED -> {
                val startIntent = Intent(this, CommandReceiver::class.java)
                    .setAction(SEND_COMMAND_ACTION)
                START.appendToIntent(startIntent)
                val startActionIntent = PendingIntent.getBroadcast(this, REQUEST_CODE_START, startIntent, FLAG_IMMUTABLE)
                builder.addAction(
                    0,
                    getString(R.string.vpn_notification_action_resume),
                    startActionIntent
                )
            }

            else -> Unit
        }
        return builder.build()
    }

    private fun registerNetworkCallback() {
        val connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val wifiNetworkRequest = NetworkRequest.Builder()
            .addTransportType(TRANSPORT_WIFI)
            .build()
        val cellularNetworkRequest = NetworkRequest.Builder()
            .addTransportType(TRANSPORT_CELLULAR)
            .build()
        initializeNetworkTypes(connectivityManager)
        connectivityManager.registerNetworkCallback(wifiNetworkRequest, wifiNetworkCallback, handler)
        connectivityManager.registerNetworkCallback(cellularNetworkRequest, cellularNetworkCallback, handler)
    }

    private fun unregisterNetworkCallback() {
        val connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        connectivityManager.unregisterNetworkCallback(wifiNetworkCallback)
        connectivityManager.unregisterNetworkCallback(cellularNetworkCallback)
    }

    private fun initializeNetworkTypes(connectivityManager: ConnectivityManager) {
        availableNetworkTypes.clear()
        connectivityManager.activeNetwork?.let { activeNetwork ->
            connectivityManager.getNetworkCapabilities(activeNetwork)?.let { networkCapabilities ->
                if (networkCapabilities.hasTransport(TRANSPORT_WIFI)) {
                    availableNetworkTypes += NetworkType.WIFI
                }
                if (networkCapabilities.hasTransport(TRANSPORT_CELLULAR)) {
                    availableNetworkTypes += NetworkType.CELLULAR
                }
            }
        }
        Timber.d("Initial network types: %s ", availableNetworkTypes)
    }

    private fun addNetworkType(type: NetworkType) {
        val noNetwork = availableNetworkTypes.isEmpty()
        availableNetworkTypes += type
        if (noNetwork) {
            Timber.d("Reconnecting VPN on network %s.", type)
            reconnect()
        }
    }

    private fun removeNetworkType(type: NetworkType) {
        availableNetworkTypes -= type
        if (availableNetworkTypes.isEmpty()) {
            Timber.d("Waiting for network...")
            waitForNetVpn()
        } else {
            reconnect()
        }
    }

    private inner class NetworkTypeCallback(private val monitoredType: NetworkType) : NetworkCallback() {
        override fun onAvailable(network: Network) {
            Timber.d("On available %s", monitoredType)
            addNetworkType(monitoredType)
        }

        override fun onLost(network: Network) {
            Timber.d("On lost %s", monitoredType)
            removeNetworkType(monitoredType)
        }
    }

    private enum class NetworkType {
        CELLULAR,
        WIFI,
    }

    private class MyHandler(callback: Handler.Callback) : Handler(Looper.getMainLooper()) {
        private val callback = WeakReference(callback)

        override fun handleMessage(msg: Message) {
            callback.get()?.handleMessage(msg)
            super.handleMessage(msg)
        }
    }

    companion object {
        private const val REQUEST_CODE_START = 43
        private const val REQUEST_CODE_PAUSE = 42
        private const val VPN_STATUS_UPDATE_MESSAGE_TYPE = 0
    }
}
