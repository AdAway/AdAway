package org.adaway.model.root

import android.content.Context
import android.content.Context.MODE_PRIVATE
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.adaway.R
import org.adaway.db.AppDatabase
import org.adaway.db.dao.HostEntryDao
import org.adaway.db.dao.HostsSourceDao
import org.adaway.db.entity.ListType.REDIRECTED
import org.adaway.helper.PreferenceHelper
import org.adaway.model.adblocking.AdBlockMethod
import org.adaway.model.adblocking.AdBlockMethod.ROOT
import org.adaway.model.adblocking.AdBlockModel
import org.adaway.model.error.HostError.COPY_FAIL
import org.adaway.model.error.HostError.NOT_ENOUGH_SPACE
import org.adaway.model.error.HostError.PRIVATE_FILE_FAILED
import org.adaway.model.error.HostError.REVERT_FAIL
import org.adaway.model.error.HostErrorException
import org.adaway.model.root.MountType.READ_ONLY
import org.adaway.model.root.MountType.READ_WRITE
import org.adaway.util.Constants.ANDROID_SYSTEM_ETC_HOSTS
import org.adaway.util.Constants.COMMAND_CHMOD_644
import org.adaway.util.Constants.COMMAND_CHOWN
import org.adaway.util.Constants.DEFAULT_HOSTS_FILENAME
import org.adaway.util.Constants.HOSTS_FILENAME
import org.adaway.util.Constants.LINE_SEPARATOR
import org.adaway.util.Constants.LOCALHOST_HOSTNAME
import org.adaway.util.Constants.LOCALHOST_IPV4
import org.adaway.util.Constants.LOCALHOST_IPV6
import org.adaway.util.WebServerUtils
import timber.log.Timber
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * This class is the model to represent hosts file installation.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
class RootModel(context: Context) : AdBlockModel(context) {

    private val hostsSourceDao: HostsSourceDao
    private val hostEntryDao: HostEntryDao
    private val modelScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        val database = AppDatabase.getInstance(context)
        this.hostsSourceDao = database.hostsSourceDao()
        this.hostEntryDao = database.hostEntryDao()

        modelScope.launch { checkApplied() }
        modelScope.launch { syncPreferences(context) }
    }

    override fun getMethod(): AdBlockMethod = ROOT

    @Throws(HostErrorException::class)
    override fun apply() {
        setState(R.string.status_apply_sources)
        setState(R.string.status_create_new_hosts)
        createNewHostsFile()
        setState(R.string.status_copy_new_hosts)
        copyNewHostsFile()
        setState(R.string.status_check_copy)
        setState(R.string.status_hosts_updated)
        this.applied.postValue(true)
    }

    @Throws(HostErrorException::class)
    override fun revert() {
        setState(R.string.status_revert)
        try {
            revertHostFile()
            setState(R.string.status_revert_done)
            this.applied.postValue(false)
        } catch (exception: IOException) {
            throw HostErrorException(REVERT_FAIL, exception)
        }
    }

    override fun isRecordingLogs(): Boolean = TcpdumpUtils.isTcpdumpRunning()

    override fun setRecordingLogs(recording: Boolean) {
        if (recording) {
            TcpdumpUtils.startTcpdump(this.context)
        } else {
            TcpdumpUtils.stopTcpdump()
        }
    }

    override fun getLogs(): List<String> = TcpdumpUtils.getLogs(this.context)

    override fun clearLogs() {
        TcpdumpUtils.clearLogFile(this.context)
    }

    private fun checkApplied() {
        var isApplied = false
        val result = Shell.cmd("head -n 1 $ANDROID_SYSTEM_ETC_HOSTS").exec()
        if (!result.isSuccess) {
            Timber.e("Failed to read first line of hosts file. Error code: %s", result.code)
        } else {
            isApplied = ShellUtils.mergeAllLines(result.out).startsWith(HEADER1)
        }
        this.applied.postValue(isApplied)
    }

    private fun syncPreferences(context: Context) {
        if (PreferenceHelper.getWebServerEnabled(context) && !WebServerUtils.isWebServerRunning()) {
            WebServerUtils.startWebServer(context)
        }
    }

    private fun deleteNewHostsFile() {
        this.context.deleteFile(HOSTS_FILENAME)
    }

    @Throws(HostErrorException::class)
    private fun copyNewHostsFile() {
        try {
            copyHostsFile(HOSTS_FILENAME)
        } catch (exception: CommandException) {
            throw HostErrorException(COPY_FAIL, exception)
        }
    }

    @Throws(HostErrorException::class)
    private fun createNewHostsFile() {
        deleteNewHostsFile()
        try {
            BufferedWriter(OutputStreamWriter(this.context.openFileOutput(HOSTS_FILENAME, MODE_PRIVATE))).use { writer ->
                writeHostsHeader(writer)
                writeLoopbackToHosts(writer)
                writeHosts(writer)
            }
        } catch (exception: IOException) {
            throw HostErrorException(PRIVATE_FILE_FAILED, exception)
        }
    }

    @Throws(IOException::class)
    private fun writeHostsHeader(writer: BufferedWriter) {
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val now = Date()
        val date = formatter.format(now)

        writer.write(HEADER1)
        writer.write(date)
        writer.newLine()
        writer.write(HEADER2)
        writer.newLine()

        writer.write(HEADER_SOURCES)
        writer.newLine()
        for (hostsSource in this.hostsSourceDao.enabled) {
            writer.write("# - ${hostsSource.label}:${hostsSource.url}")
            writer.newLine()
        }
        writer.newLine()
    }

    @Throws(IOException::class)
    private fun writeLoopbackToHosts(writer: BufferedWriter) {
        writer.write("$LOCALHOST_IPV4 $LOCALHOST_HOSTNAME")
        writer.newLine()
        writer.write("$LOCALHOST_IPV6 $LOCALHOST_HOSTNAME")
        writer.newLine()
    }

    @Throws(IOException::class)
    private fun writeHosts(writer: BufferedWriter) {
        val redirectionIpv4 = PreferenceHelper.getRedirectionIpv4(this.context)
        val redirectionIpv6 = PreferenceHelper.getRedirectionIpv6(this.context)
        val enableIpv6 = PreferenceHelper.getEnableIpv6(this.context)

        for (entry in this.hostEntryDao.all) {
            val hostname = entry.host
            if (entry.type == REDIRECTED) {
                writer.write("${entry.redirection} $hostname")
                writer.newLine()
            } else {
                writer.write("$redirectionIpv4 $hostname")
                writer.newLine()
                if (enableIpv6) {
                    writer.write("$redirectionIpv6 $hostname")
                    writer.newLine()
                }
            }
        }
    }

    @Throws(IOException::class)
    private fun revertHostFile() {
        try {
            this.context.openFileOutput(DEFAULT_HOSTS_FILENAME, MODE_PRIVATE).use { fos ->
                val localhost = "$LOCALHOST_IPV4 $LOCALHOST_HOSTNAME$LINE_SEPARATOR$LOCALHOST_IPV6 $LOCALHOST_HOSTNAME$LINE_SEPARATOR"
                fos.write(localhost.toByteArray())

                copyHostsFile(DEFAULT_HOSTS_FILENAME)

                this.context.deleteFile(DEFAULT_HOSTS_FILENAME)
            }
        } catch (exception: Exception) {
            throw IOException("Unable to revert hosts file.", exception)
        }
    }

    @Throws(HostErrorException::class, CommandException::class)
    private fun copyHostsFile(source: String) {
        val privateDir = this.context.filesDir.absolutePath
        val privateFile = privateDir + File.separator + source

        val target = ANDROID_SYSTEM_ETC_HOSTS
        val targetFile = File(target)

        val size = File(privateFile).length()
        Timber.i("Size of hosts file: %s.", size)
        if (!hasEnoughSpaceOnPartition(targetFile, size)) {
            throw HostErrorException(NOT_ENOUGH_SPACE)
        }

        val writable = ShellUtils.isWritable(targetFile)
        try {
            if (!writable) {
                Timber.i("Remounting for RW…")
                if (!ShellUtils.remountPartition(targetFile, READ_WRITE)) {
                    throw CommandException("Failed to remount hosts file partition as read-write.")
                }
            }
            val result = Shell.cmd(
                "dd if=$privateFile of=$target",
                "$COMMAND_CHOWN $target",
                "$COMMAND_CHMOD_644 $target"
            ).exec()
            if (!result.isSuccess) {
                throw CommandException("Failed to copy hosts file: ${ShellUtils.mergeAllLines(result.err)}")
            }
        } finally {
            if (!writable) {
                ShellUtils.remountPartition(targetFile, READ_ONLY)
            }
        }
    }

    private fun hasEnoughSpaceOnPartition(target: File, size: Long): Boolean {
        val freeSpace = target.freeSpace
        return freeSpace == 0L || freeSpace > size
    }

    companion object {
        private const val HEADER1 = "# This hosts file has been generated by AdAway on: "
        private const val HEADER2 = "# Please do not modify it directly, it will be overwritten when AdAway is applied again."
        private const val HEADER_SOURCES = "# This file is generated from the following sources:"
    }
}
