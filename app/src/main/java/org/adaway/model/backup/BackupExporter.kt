package org.adaway.model.backup

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.annotation.UiThread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.adaway.R
import org.adaway.db.AppDatabase
import org.adaway.db.entity.HostListItem
import org.adaway.db.entity.HostsSource
import org.adaway.db.entity.ListType.ALLOWED
import org.adaway.db.entity.ListType.BLOCKED
import org.adaway.db.entity.ListType.REDIRECTED
import org.adaway.model.backup.BackupFormat.ALLOWED_KEY
import org.adaway.model.backup.BackupFormat.BLOCKED_KEY
import org.adaway.model.backup.BackupFormat.REDIRECTED_KEY
import org.adaway.model.backup.BackupFormat.SOURCES_KEY
import org.adaway.model.backup.BackupFormat.hostToJson
import org.adaway.model.backup.BackupFormat.sourceToJson
import org.adaway.util.ExpressiveToast
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import timber.log.Timber
import java.io.BufferedWriter
import java.io.File
import java.io.IOException
import java.io.OutputStreamWriter

object BackupExporter {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @JvmStatic
    fun exportToBackup(context: Context, backupUri: Uri) {
        scope.launch {
            val exported = withContext(Dispatchers.IO) {
                try {
                    exportBackup(context, backupUri)
                    true
                } catch (exception: IOException) {
                    Timber.e(exception, "Failed to export backup.")
                    false
                }
            }
            val fileName = backupUri.path?.let { File(it).name }.orEmpty()
            notifyExportEnd(context, exported, fileName)
        }
    }

    @UiThread
    private fun notifyExportEnd(context: Context, successful: Boolean, backupUri: String) {
        ExpressiveToast.makeText(
            context,
            context.getString(if (successful) R.string.export_success else R.string.export_failed, backupUri),
            Toast.LENGTH_LONG
        ).show()
    }

    @JvmStatic
    @Throws(IOException::class)
    fun exportBackup(context: Context, backupUri: Uri) {
        try {
            val outputStream = context.contentResolver.openOutputStream(backupUri)
                ?: throw IOException("Could not open backup file.")
            outputStream.use { stream ->
                BufferedWriter(OutputStreamWriter(stream)).use { writer ->
                    writer.write(makeBackup(context).toString(4))
                }
            }
        } catch (exception: JSONException) {
            throw IOException("Failed to generate backup.", exception)
        } catch (exception: IOException) {
            throw IOException("Could not write file.", exception)
        }
    }

    @Throws(JSONException::class)
    private fun makeBackup(context: Context): JSONObject {
        val database = AppDatabase.getInstance(context)
        val hostsSourceDao = database.hostsSourceDao()
        val hostListItemDao = database.hostsListItemDao()
        val userHosts = hostListItemDao.userList

        return JSONObject().apply {
            put(SOURCES_KEY, buildSourcesBackup(hostsSourceDao.all))
            put(BLOCKED_KEY, buildListBackup(userHosts.filter { it.type == BLOCKED }))
            put(ALLOWED_KEY, buildListBackup(userHosts.filter { it.type == ALLOWED }))
            put(REDIRECTED_KEY, buildListBackup(userHosts.filter { it.type == REDIRECTED }))
        }
    }

    @Throws(JSONException::class)
    private fun buildSourcesBackup(sources: List<HostsSource>): JSONArray {
        val sourceArray = JSONArray()
        sources.forEach { source -> sourceArray.put(sourceToJson(source)) }
        return sourceArray
    }

    @Throws(JSONException::class)
    private fun buildListBackup(hosts: List<HostListItem>): JSONArray {
        val listArray = JSONArray()
        hosts.forEach { host -> listArray.put(hostToJson(host)) }
        return listArray
    }
}
