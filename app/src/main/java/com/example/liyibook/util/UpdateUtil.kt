package com.example.liyibook.util

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * GitHub Releases 更新检查 / 下载 / 安装。
 * 仓库公开，API 无需 token。
 * tag 约定为 vX.Y（如 v1.1），与 versionName 对应；发布时同步改 build.gradle.kts 的 versionName/versionCode。
 */
object UpdateUtil {
    private const val API_LATEST = "https://api.github.com/repos/CyanGlaze/GuestbookApp/releases/latest"

    /** 当前版本，与 app/build.gradle.kts 的 versionName 保持一致 */
    const val CURRENT_VERSION = "1.0"

    data class ReleaseInfo(
        val tag: String,
        val version: String,
        val notes: String,
        val apkUrl: String,
        val apkName: String,
        val sizeMb: Float,
        val publishedAt: String
    )

    sealed interface CheckResult {
        data class Found(val info: ReleaseInfo, val newer: Boolean) : CheckResult
        data object None : CheckResult
        data class Error(val message: String) : CheckResult
    }

    /** 请求最新 release（IO 线程） */
    suspend fun checkLatest(): CheckResult = withContext(Dispatchers.IO) {
        try {
            val conn = URL(API_LATEST).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", "LiJinBook-Android")
            val code = conn.responseCode
            if (code == 404) {
                conn.disconnect()
                return@withContext CheckResult.None
            }
            if (code != 200) {
                conn.disconnect()
                return@withContext CheckResult.Error("网络异常（HTTP $code）")
            }
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()

            val obj = JsonParser.parseString(text).asJsonObject
            val tag = obj.get("tag_name")?.asString?.takeIf { it.isNotBlank() }
                ?: return@withContext CheckResult.None
            val notes = obj.get("body")?.asString ?: ""
            val published = obj.get("published_at")?.asString ?: ""

            var apkUrl = ""
            var apkName = ""
            var size = 0L
            obj.getAsJsonArray("assets")?.let { arr ->
                for (i in 0 until arr.size()) {
                    val a = arr[i].asJsonObject
                    val name = a.get("name")?.asString ?: ""
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        apkUrl = a.get("browser_download_url")?.asString ?: ""
                        apkName = name
                        size = a.get("size")?.asLong ?: 0L
                        break
                    }
                }
            }
            if (apkUrl.isBlank()) return@withContext CheckResult.None

            val info = ReleaseInfo(
                tag = tag,
                version = tag.removePrefix("v").removePrefix("V"),
                notes = notes,
                apkUrl = apkUrl,
                apkName = apkName,
                sizeMb = size / 1024f / 1024f,
                publishedAt = published
            )
            CheckResult.Found(info, isNewer(info.version, CURRENT_VERSION))
        } catch (e: Exception) {
            CheckResult.Error(e.message?.take(60) ?: "检查失败")
        }
    }

    /** 点分版本号比较：remote 大于 current 返回 true */
    private fun isNewer(remote: String, current: String): Boolean {
        fun parts(v: String) = v.trim().removePrefix("v").removePrefix("V")
            .split(".").map { it.toIntOrNull() ?: 0 }
        val r = parts(remote)
        val c = parts(current)
        for (i in 0 until maxOf(r.size, c.size)) {
            val rv = r.getOrElse(i) { 0 }
            val cv = c.getOrElse(i) { 0 }
            if (rv != cv) return rv > cv
        }
        return false
    }

    /** 下载目录：app 外部私有目录 Download/ */
    fun apkFile(context: Context, name: String): File =
        File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), name)

    /** 入队下载，返回 downloadId。重复下载前调用方应先删旧文件。 */
    fun download(context: Context, info: ReleaseInfo): Long {
        apkFile(context, info.apkName).let { if (it.exists()) it.delete() }
        val request = DownloadManager.Request(Uri.parse(info.apkUrl))
            .setTitle("礼金账本 ${info.version}")
            .setDescription("正在下载更新包…")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, info.apkName)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        return dm.enqueue(request)
    }

    fun canInstallUnknownApps(context: Context): Boolean =
        context.packageManager.canRequestPackageInstalls()

    fun openInstallPermissionSettings(context: Context) {
        try {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "请到系统设置中允许安装未知应用", Toast.LENGTH_LONG).show()
        }
    }

    /** 拉起安装界面 */
    fun install(context: Context, name: String) {
        val file = apkFile(context, name)
        if (!file.exists() || file.length() == 0L) {
            Toast.makeText(context, "安装包不存在，请重新下载", Toast.LENGTH_SHORT).show()
            return
        }
        if (!canInstallUnknownApps(context)) {
            Toast.makeText(context, "请先允许本应用安装未知应用", Toast.LENGTH_LONG).show()
            openInstallPermissionSettings(context)
            return
        }
        try {
            val uri: Uri = FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "无法打开安装包：${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
