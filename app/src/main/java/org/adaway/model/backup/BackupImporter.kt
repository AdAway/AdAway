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
import org.adaway.db.entity.ListType
import org.adaway.db.entity.ListType.ALLOWED
import org.adaway.db.entity.ListType.BLOCKED
import org.adaway.db.entity.ListType.REDIRECTED
import org.adaway.model.backup.BackupFormat.ALLOWED_KEY
import org.adaway.model.backup.BackupFormat.BLOCKED_KEY
import org.adaway.model.backup.BackupFormat.REDIRECTED_KEY
import org.adaway.model.backup.BackupFormat.SOURCES_KEY
import org.adaway.model.backup.BackupFormat.hostFromJson
import org.adaway.model.backup.BackupFormat.sourceFromJson
import org.adaway.util.ExpressiveToast
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import timber.log.Timber
import java.io.BufferedReader
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStreamReader

object BackupImporter {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @JvmStatic
    @UiThread
    fun importFromBackup(context: Context, backupUri: Uri) {
        scope.launch {
            val imported = withContext(Dispatchers.IO) {
                try {
                    importBackup(context, backupUri)
                    true
                } catch (exception: IOException) {
                    Timber.e(exception, "Failed to import backup.")
                    false
                }
            }
            notifyImportEnd(context, imported)
        }
    }

    @UiThread
    private fun notifyImportEnd(context: Context, successful: Boolean) {
        ExpressiveToast.makeText(
            context,
            context.getString(if (successful) R.string.import_success else R.string.import_failed),
            Toast.LENGTH_LONG
        ).show()
    }

    @JvmStatic
    @Throws(IOException::class)
    fun importBackup(context: Context, backupUri: Uri) {
        try {
            val inputStream = context.contentResolver.openInputStream(backupUri)
                ?: throw FileNotFoundException("Could not open backup file.")
            val content = inputStream.use { stream ->
                BufferedReader(InputStreamReader(stream)).use { reader ->
                    buildString {
                        var line = reader.readLine()
                        while (line != null) {
                            append(line)
                            line = reader.readLine()
                        }
                    }
                }
            }
            importBackup(context, JSONObject(content))
        } catch (exception: JSONException) {
            throw IOException("Failed to parse backup file.", exception)
        } catch (exception: FileNotFoundException) {
            throw IOException("Failed to find backup file.", exception)
        } catch (exception: IOException) {
            throw IOException("Failed to read backup file.", exception)
        }
    }

    @Throws(JSONException::class)
    private fun importBackup(context: Context, backupObject: JSONObject) {
        val database = AppDatabase.getInstance(context)
        val hostsSourceDao = database.hostsSourceDao()
        val hostListItemDao = database.hostsListItemDao()

        importSourceBackup(hostsSourceDao, backupObject.getJSONArray(SOURCES_KEY))
        importListBackup(hostListItemDao, BLOCKED, backupObject.getJSONArray(BLOCKED_KEY))
        importListBackup(hostListItemDao, ALLOWED, backupObject.getJSONArray(ALLOWED_KEY))
        importListBackup(hostListItemDao, REDIRECTED, backupObject.getJSONArray(REDIRECTED_KEY))
    }

    @Throws(JSONException::class)
    private fun importSourceBackup(
        hostsSourceDao: org.adaway.db.dao.HostsSourceDao,
        sources: JSONArray
    ) {
        for (index in 0 until sources.length()) {
            hostsSourceDao.insert(sourceFromJson(sources.getJSONObject(index)))
        }
    }

    @Throws(JSONException::class)
    private fun importListBackup(
        hostListItemDao: org.adaway.db.dao.HostListItemDao,
        type: ListType,
        hosts: JSONArray
    ) {
        for (index in 0 until hosts.length()) {
            val host: HostListItem = hostFromJson(hosts.getJSONObject(index))
            host.type = type
            val id = hostListItemDao.getHostId(host.host)
            if (id.isPresent) {
                host.id = id.get()
                hostListItemDao.update(host)
            } else {
                hostListItemDao.insert(host)
            }
        }
    }
}
