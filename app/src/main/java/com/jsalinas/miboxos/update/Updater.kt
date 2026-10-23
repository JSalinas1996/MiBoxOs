package com.jsalinas.miboxos.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.jsalinas.miboxos.Endpoints
import com.jsalinas.miboxos.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class Release(val version: String, val apkUrl: String)

sealed interface UpdateStatus {
    data object Idle : UpdateStatus
    data object Checking : UpdateStatus
    data object UpToDate : UpdateStatus
    data class Available(val release: Release) : UpdateStatus
    data object Downloading : UpdateStatus
    data object WaitingConfirmation : UpdateStatus
    data class Failed(val message: String) : UpdateStatus
}

/**
 * Actualización desde las versiones publicadas en GitHub (repo público, sin credenciales).
 * Android pide confirmar la instalación y, la primera vez, permitir "instalar apps desconocidas"
 * para MiBoxOs. Solo funciona si la versión nueva está firmada con la misma clave que la instalada.
 */
class Updater(private val context: Context, private val prefs: Prefs, private val scope: CoroutineScope) {
    var status by mutableStateOf<UpdateStatus>(UpdateStatus.Idle)
        private set

    val installedVersion: String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()

    /** Busca una vez por día (o siempre si [force]). */
    fun check(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - prefs.lastUpdateCheck < DAY_MS) return
        prefs.lastUpdateCheck = now
        status = UpdateStatus.Checking
        scope.launch {
            status = runCatching { latest() }.fold(
                onSuccess = { r -> if (r != null && isNewer(r.version, installedVersion)) UpdateStatus.Available(r) else UpdateStatus.UpToDate },
                onFailure = { UpdateStatus.Failed("No pude consultar GitHub: ${it.message}") },
            )
        }
    }

    fun install(release: Release) {
        if (!context.packageManager.canRequestPackageInstalls()) {
            // Primera vez: Android pide permitir que MiBoxOs instale apps
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        }
        status = UpdateStatus.Downloading
        scope.launch {
            runCatching {
                val apk = download(release.apkUrl)
                commit(apk)
            }.onSuccess { status = UpdateStatus.WaitingConfirmation }
                .onFailure { status = UpdateStatus.Failed("No se pudo actualizar: ${it.message}") }
        }
    }

    private suspend fun latest(): Release? = withContext(Dispatchers.IO) {
        val conn = URL(Endpoints.latestRelease).openConnection() as HttpURLConnection
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        conn.connectTimeout = 8_000
        conn.readTimeout = 10_000
        try {
            if (conn.responseCode == 404) return@withContext null // todavía no hay versiones publicadas
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val assets = json.optJSONArray("assets")
            val apk = (0 until (assets?.length() ?: 0)).map { assets!!.getJSONObject(it) }
                .firstOrNull { it.optString("name").endsWith(".apk") } ?: return@withContext null
            Release(json.getString("tag_name").removePrefix("v"), apk.getString("browser_download_url"))
        } finally {
            conn.disconnect()
        }
    }

    private suspend fun download(url: String): File = withContext(Dispatchers.IO) {
        val target = File(context.cacheDir, "update.apk")
        var conn = URL(url).openConnection() as HttpURLConnection
        // GitHub redirige la descarga a otro host
        repeat(5) {
            conn.instanceFollowRedirects = false
            val code = conn.responseCode
            if (code !in 300..399) return@repeat
            val next = conn.getHeaderField("Location")
            conn.disconnect()
            conn = URL(next).openConnection() as HttpURLConnection
        }
        try {
            check(conn.responseCode in 200..299) { "descarga ${conn.responseCode}" }
            conn.inputStream.use { input -> target.outputStream().use { input.copyTo(it) } }
        } finally {
            conn.disconnect()
        }
        target
    }

    private suspend fun commit(apk: File) = withContext(Dispatchers.IO) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            apk.inputStream().use { input ->
                session.openWrite("miboxos.apk", 0, apk.length()).use { out ->
                    input.copyTo(out)
                    session.fsync(out)
                }
            }
            val intent = PendingIntent.getBroadcast(
                context, id, Intent(context, UpdateStatusReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            session.commit(intent.intentSender)
        }
    }

    companion object {
        private const val DAY_MS = 24 * 60 * 60_000L

        /** "0.10.0" > "0.9.3": compara número por número. */
        fun isNewer(remote: String, local: String): Boolean {
            val r = remote.split('.').map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
            val l = local.split('.').map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
            for (i in 0 until maxOf(r.size, l.size)) {
                val a = r.getOrElse(i) { 0 }
                val b = l.getOrElse(i) { 0 }
                if (a != b) return a > b
            }
            return false
        }
    }
}

/** Android avisa acá el resultado; si pide confirmación, se abre su pantalla de instalación. */
class UpdateStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1) == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            @Suppress("DEPRECATION")
            val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
            context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
