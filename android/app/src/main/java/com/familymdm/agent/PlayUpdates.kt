package com.familymdm.agent

import android.content.Context
import android.content.pm.PackageInfo
import android.os.Build
import com.aurora.gplayapi.data.models.AuthData
import com.aurora.gplayapi.data.models.PlayFile
import com.aurora.gplayapi.helpers.AppDetailsHelper
import com.aurora.gplayapi.helpers.AuthHelper
import com.aurora.gplayapi.helpers.PurchaseHelper
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Checks and silently installs updates for a small, explicit allow-list of packages through the
 * real Play Store protocol -- the same open-source library (gplayapi) Aurora Store itself is
 * built on -- using a short-lived token the dashboard mints on request from one dedicated Google
 * account (see /agent/play-token in src/index.js, and SETUP.md). No Play Store app or Google
 * account is ever on the phone itself; every other install/update stays blocked exactly as before.
 */
object PlayUpdates {
    /** Always checked regardless of the apps grid's own allow/block state -- these usually aren't
     * in it at all (no launcher icon), but Waze/Maps/Android Auto quietly depend on them staying
     * current. */
    @JvmField
    val CANDIDATE_PACKAGES: List<String> = listOf(
        "com.waze",
        "com.google.android.apps.maps",
        "com.google.android.projection.gearhead",
        "com.google.android.gms",
        "com.google.android.gsf",
        "com.android.vending",
    )

    data class UpdateInfo(
        val pkg: String,
        val label: String,
        val installedVersion: Long,
        val availableVersion: Long,
    )

    interface Callback<T> {
        fun onResult(value: T?, error: String?)
    }

    @JvmStatic
    fun checkForUpdates(context: Context, eligiblePackages: List<String>, callback: Callback<List<UpdateInfo>>) {
        Thread {
            try {
                val authData = fetchAuthData(context)
                val pm = context.packageManager
                val installed = LinkedHashMap<String, Long>()
                for (pkg in eligiblePackages) {
                    try {
                        installed[pkg] = longVersion(pm.getPackageInfo(pkg, 0))
                    } catch (ignored: Exception) {
                        // not installed on this phone -- nothing to update
                    }
                }
                if (installed.isEmpty()) {
                    callback.onResult(emptyList(), null)
                    return@Thread
                }
                val apps = AppDetailsHelper(authData).getAppByPackageName(installed.keys.toList())
                val updates = apps.mapNotNull { app ->
                    val mine = installed[app.packageName] ?: return@mapNotNull null
                    if (app.versionCode <= mine) return@mapNotNull null
                    UpdateInfo(app.packageName, app.displayName.ifBlank { app.packageName }, mine, app.versionCode)
                }
                callback.onResult(updates, null)
            } catch (e: Exception) {
                callback.onResult(null, e.message ?: e.toString())
            }
        }.start()
    }

    @JvmStatic
    fun updateOne(context: Context, pkg: String, callback: Callback<String>) {
        Thread {
            try {
                val authData = fetchAuthData(context)
                val app = AppDetailsHelper(authData).getAppByPackageName(pkg)
                val installedVersion = try {
                    longVersion(context.packageManager.getPackageInfo(pkg, 0))
                } catch (ignored: Exception) {
                    null
                }
                val files = PurchaseHelper(authData).purchase(
                    app.packageName, app.versionCode, app.offerType,
                    installedVersionCode = installedVersion,
                )
                val apkFiles = files.filter { it.type == PlayFile.Type.BASE || it.type == PlayFile.Type.SPLIT }
                if (apkFiles.isEmpty()) throw Exception("Google did not return an APK for $pkg")
                val dir = File(context.cacheDir, "play-update-$pkg")
                dir.deleteRecursively()
                dir.mkdirs()
                val downloaded = apkFiles.map { downloadTo(it, dir) }
                Installer.installMultiple(context, downloaded)
                callback.onResult("Updating " + app.displayName + " to build " + app.versionCode + ".", null)
            } catch (e: Exception) {
                callback.onResult(null, e.message ?: e.toString())
            }
        }.start()
    }

    private fun longVersion(info: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()

    private fun downloadTo(f: PlayFile, dir: File): File {
        val out = File(dir, f.name)
        val conn = URL(f.url).openConnection() as HttpURLConnection
        conn.connectTimeout = 20000
        conn.readTimeout = 60000
        try {
            if (conn.responseCode >= 400) throw Exception("download failed for ${f.name} (HTTP ${conn.responseCode})")
            conn.inputStream.use { input -> FileOutputStream(out).use { output -> input.copyTo(output) } }
        } finally {
            conn.disconnect()
        }
        return out
    }

    /** Asks the dashboard's /agent/play-token for a fresh ~50-minute token (it holds the one
     * long-lived credential; the phone never does) and wraps it the same way Aurora Store's own
     * "anonymous" mode wraps a dispenser-issued token. */
    private fun fetchAuthData(context: Context): AuthData {
        val server = Agent.prefs(context).getString("server", null) ?: throw Exception("no dashboard configured on this phone")
        val token = Agent.prefs(context).getString("token", null) ?: throw Exception("this phone is not enrolled")
        val response = Api.post("$server/agent/play-token", JSONObject(), token)
        if (response.has("error")) throw Exception(response.getString("error"))
        return AuthHelper.build(
            email = response.getString("email"),
            token = response.getString("auth"),
            tokenType = AuthHelper.Token.AUTH,
            isAnonymous = true,
        )
    }
}
