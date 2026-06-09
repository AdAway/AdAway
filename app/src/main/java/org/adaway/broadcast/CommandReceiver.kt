package org.adaway.broadcast

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.adaway.AdAwayApplication
import org.adaway.model.adblocking.AdBlockModel
import org.adaway.model.error.HostErrorException
import org.adaway.util.CoroutineDispatchers
import timber.log.Timber

class CommandReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (SEND_COMMAND_ACTION == intent.action) {
            val adBlockModel = (context.applicationContext as AdAwayApplication).adBlockModel
            val command = Command.readFromIntent(intent)
            Timber.i("CommandReceiver invoked with command %s.", command)
            IO_EXECUTOR.execute { executeCommand(adBlockModel, command) }
        }
    }

    private fun executeCommand(adBlockModel: AdBlockModel, command: Command) {
        try {
            when (command) {
                Command.START -> adBlockModel.apply()
                Command.STOP -> adBlockModel.revert()
                Command.UNKNOWN -> Timber.i("Failed to run an unsupported command.")
            }
        } catch (exception: HostErrorException) {
            Timber.w(exception, "Failed to apply ad block command $command.")
        }
    }

    companion object {
        const val SEND_COMMAND_ACTION = "org.adaway.action.SEND_COMMAND"
        private val IO_EXECUTOR = CoroutineDispatchers.ioExecutor()
    }
}
