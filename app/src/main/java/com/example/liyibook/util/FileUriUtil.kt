package com.example.liyibook.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns

/**
 * SAF 文件持久化权限与写回工具。
 * 用 ACTION_OPEN_DOCUMENT（OpenDocument）拿到的 Uri 可通过 takePersistableUriPermission
 * 长期持有读+写权限，从而在本机直接覆盖修改用户选中的 Excel 原文件。
 */
object FileUriUtil {

    val EXCEL_MIME = arrayOf(
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "application/vnd.ms-excel"
    )

    /** 取得该文件长期读写权限。OpenDocument 的 Uri 才支持持久化。 */
    fun takePermission(context: Context, uri: Uri): Boolean = try {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        context.contentResolver.takePersistableUriPermission(uri, flags)
        canWrite(context, uri)
    } catch (e: Exception) {
        false
    }

    /** 是否仍持有该 Uri 的写权限（重装机、文件删除、权限被撤会失效） */
    fun canWrite(context: Context, uri: Uri): Boolean =
        context.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isWritePermission }

    fun canWrite(context: Context, uriString: String): Boolean = try {
        if (uriString.isBlank()) false else canWrite(context, Uri.parse(uriString))
    } catch (e: Exception) {
        false
    }

    /** 覆盖写回文件（"wt" 截断重写）。返回是否成功。 */
    fun writeBytes(context: Context, uri: Uri, bytes: ByteArray): Boolean = try {
        context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
            out.write(bytes)
            out.flush()
        } ?: return false
        true
    } catch (e: Exception) {
        false
    }

    fun displayName(context: Context, uri: Uri): String {
        var name = ""
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c ->
                if (c.moveToFirst()) {
                    val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) name = c.getString(idx) ?: ""
                }
            }
        return name.ifBlank { "账本.xlsx" }
    }
}
