package com.recomp.app.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import com.recomp.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale

/**
 * Actualizaciones de las Releases firmadas del repositorio oficial.
 * Las compilaciones debug no son compatibles con una firma estable.
 */
data class AvailableUpdate(
    val versionCode: Int,
    val versionName: String,
    val downloadUrl: String,
    val sha256: String
)

object UpdateManager {
    private const val API =
        "https://api.github.com/repos/JEstrada2007/recomp-android/releases?per_page=30"
    private const val DOWNLOAD_PREFIX =
        "https://github.com/JEstrada2007/recomp-android/releases/download/"
    private val versionPattern = Regex("^v0\\.2\\.[0-9]+$")
    private val codePattern = Regex("(?m)^VersionCode: ([0-9]+)\\s*$")
    private val hashPattern = Regex("(?m)^SHA256: ([0-9a-fA-F]{64})\\s*$")

    suspend fun check(): AvailableUpdate? = withContext(Dispatchers.IO) {
        if (BuildConfig.DEBUG) return@withContext null
        val connection = (URL(API).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "Recomp-Android-Updater")
        }
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                error("No se pudo consultar GitHub (HTTP " + connection.responseCode + ")")
            }
            val releases = JSONArray(connection.inputStream.bufferedReader().use { it.readText() })
            val candidates = mutableListOf<AvailableUpdate>()
            for (i in 0 until releases.length()) {
                val release = releases.getJSONObject(i)
                if (release.optBoolean("draft")) continue
                val tag = release.optString("tag_name")
                if (!versionPattern.matches(tag)) continue
                val body = release.optString("body")
                val code = codePattern.find(body)?.groupValues?.get(1)?.toIntOrNull() ?: continue
                val sha = hashPattern.find(body)?.groupValues?.get(1) ?: continue
                if (code <= BuildConfig.VERSION_CODE) continue
                val assets = release.optJSONArray("assets") ?: continue
                for (a in 0 until assets.length()) {
                    val asset = assets.getJSONObject(a)
                    if (asset.optString("name") != "Recomp.apk") continue
                    val url = asset.optString("browser_download_url")
                    if (!url.startsWith(DOWNLOAD_PREFIX) || !url.endsWith("/Recomp.apk")) continue
                    candidates += AvailableUpdate(code, tag.removePrefix("v"), url, sha.lowercase(Locale.US))
                }
            }
            candidates.maxByOrNull { it.versionCode }
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Solo descarga el APK aprobado desde Releases, usando HTTPS.
     * El hash publicado en los metadatos se verifica antes de ofrecer la instalación.
     */
    suspend fun download(
        context: Context,
        update: AvailableUpdate,
        onProgress: suspend (Int) -> Unit
    ): File = withContext(Dispatchers.IO) {
        require(update.downloadUrl.startsWith(DOWNLOAD_PREFIX)) { "URL no permitida" }
        val folder = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: error("Almacenamiento externo no disponible")
        if (!folder.exists()) folder.mkdirs()
        val targetFile = File(folder, "recomp-update-" + update.versionCode + ".apk")
        val temp = File(folder, "recomp-update-" + update.versionCode + ".part")
        temp.delete()
        val conn = (URL(update.downloadUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Recomp-Android-Updater")
        }
        try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                error("Descarga fallida (HTTP " + conn.responseCode + ")")
            }
            val expectedLength = conn.contentLengthLong
            if (expectedLength > 150L * 1024L * 1024L) error("APK demasiado grande")
            val digest = MessageDigest.getInstance("SHA-256")
            var copied = 0L
            conn.inputStream.use { input ->
                temp.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        copied += read
                        if (copied > 150L * 1024L * 1024L) error("APK demasiado grande")
                        if (expectedLength > 0) {
                            onProgress(((copied * 100L) / expectedLength).toInt().coerceIn(0, 100))
                        }
                    }
                }
            }
            if (copied == 0L || (expectedLength > 0 && copied != expectedLength)) {
                error("Descarga incompleta")
            }
            val calculated = digest.digest().joinToString("") { "%02x".format(it) }
            if (!calculated.equals(update.sha256, ignoreCase = true)) {
                error("El archivo no pasó la comprobación SHA-256")
            }
            targetFile.delete()
            if (!temp.renameTo(targetFile)) error("No se pudo guardar el APK")
            targetFile
        } catch (t: Throwable) {
            temp.delete()
            throw t
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Android solicita autorización cuando corresponde; nunca instala en silencio.
     * Devuelve false cuando el usuario debe habilitar la fuente de instalación.
     */
    fun install(context: Context, file: File): Boolean {
        if (!file.exists() || !file.name.endsWith(".apk")) error("APK no disponible")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            val settings = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:" + context.packageName)
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(settings)
            return false
        }
        val uri = FileProvider.getUriForFile(
            context, context.packageName + ".fileprovider", file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return true
    }
}
