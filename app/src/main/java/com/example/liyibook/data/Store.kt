package com.example.liyibook.data

import android.content.Context
import com.example.liyibook.model.Book
import com.example.liyibook.model.GiftRecord
import com.example.liyibook.model.UserInfo
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * 本地持久化：对应小程序的 wx.getStorageSync / setStorageSync。
 * 全部数据保存在应用私有目录（SharedPreferences），卸载应用即丢失，与小程序行为一致。
 */
object Store {
    private const val PREF = "liyin_book"
    private const val KEY_BOOKS = "bookShelf"
    private const val KEY_USER = "userInfo"
    private const val KEY_VIEW = "shelfViewMode"

    private val gson = Gson()
    private lateinit var ctx: Context
    private val bookListType = object : TypeToken<MutableList<Book>>() {}.type

    fun init(context: Context) {
        ctx = context.applicationContext
    }

    private fun sp() = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    // ===== 书架 =====
    fun loadBooks(): MutableList<Book> = try {
        val json = sp().getString(KEY_BOOKS, null)
        if (json.isNullOrBlank()) mutableListOf()
        else gson.fromJson<MutableList<Book>>(json, bookListType) ?: mutableListOf()
    } catch (e: Exception) {
        mutableListOf()
    }

    fun saveBooks(books: List<Book>) {
        sp().edit().putString(KEY_BOOKS, gson.toJson(books)).apply()
    }

    fun clearBooks() {
        sp().edit().remove(KEY_BOOKS).apply()
    }

    // ===== 用户 =====
    fun loadUser(): UserInfo? = try {
        sp().getString(KEY_USER, null)?.let { gson.fromJson(it, UserInfo::class.java) }
    } catch (e: Exception) {
        null
    }

    fun saveUser(user: UserInfo) {
        sp().edit().putString(KEY_USER, gson.toJson(user)).apply()
    }

    fun clearUser() {
        sp().edit().remove(KEY_USER).apply()
    }

    // ===== 视图模式 =====
    fun loadViewMode(): String = sp().getString(KEY_VIEW, "grid") ?: "grid"

    fun saveViewMode(mode: String) {
        sp().edit().putString(KEY_VIEW, mode).apply()
    }

    // ===== 备份编解码 =====
    // 导出为「中文表头」结构，兼容原微信小程序的备份 JSON（姓名/礼金/地址/备注）。
    // 导入同时兼容中文表头与英文表头两种格式。
    fun exportBackupJson(books: List<Book>): String {
        val arr = com.google.gson.JsonArray()
        books.forEach { b ->
            val o = com.google.gson.JsonObject().apply {
                addProperty("id", b.id)
                addProperty("title", b.title)
                addProperty("sourceName", b.sourceName)
                addProperty("fileName", b.fileName)
                addProperty("sourceUri", b.sourceUri)
                addProperty("color", b.color)
                addProperty("desc", b.desc)
                addProperty("count", b.count)
                addProperty("total", b.total)
                addProperty("date", b.date)
            }
            val recs = com.google.gson.JsonArray()
            b.records.forEach { r ->
                recs.add(com.google.gson.JsonObject().apply {
                    addProperty("姓名", r.name)
                    addProperty("礼金", r.amount)
                    addProperty("地址", r.address)
                    addProperty("备注", r.remark)
                })
            }
            o.add("records", recs)
            arr.add(o)
        }
        return gson.toJson(arr)
    }

    fun importBackupJson(json: String): MutableList<Book>? = try {
        val arr = gson.fromJson(json, com.google.gson.JsonArray::class.java)
            ?: return null
        val result = mutableListOf<Book>()
        for (el in arr) {
            if (!el.isJsonObject) continue
            val o = el.asJsonObject
            val records = mutableListOf<GiftRecord>()
            o.getAsJsonArray("records")?.forEach { re ->
                if (!re.isJsonObject) return@forEach
                val r = re.asJsonObject
                val name = str(r, "姓名", "name")
                val amount = num(r, "礼金", "amount")
                val address = str(r, "地址", "address")
                val remark = str(r, "备注", "remark")
                if (name.isNotBlank()) records.add(GiftRecord(name, amount, address, remark))
            }
            result.add(
                Book(
                    id = str(o, "id"),
                    title = str(o, "title", "name"),
                    sourceName = str(o, "sourceName"),
                    fileName = str(o, "fileName"),
                    sourceUri = str(o, "sourceUri"),
                    color = str(o, "color").ifBlank { Book.DEFAULT_COLORS[0] },
                    desc = str(o, "desc"),
                    records = records,
                    count = num(o, "count").toInt().let { if (it > 0) it else records.size },
                    total = num(o, "total").let { if (it != 0.0) it else records.sumOf { r -> r.amount } },
                    date = str(o, "date")
                )
            )
        }
        result
    } catch (e: Exception) {
        null
    }

    private fun str(o: com.google.gson.JsonObject, vararg keys: String): String {
        keys.forEach { k ->
            val e = o.get(k)
            if (e != null && e.isJsonPrimitive) return e.asString
        }
        return ""
    }

    private fun num(o: com.google.gson.JsonObject, vararg keys: String): Double {
        keys.forEach { k ->
            val e = o.get(k)
            if (e != null && e.isJsonPrimitive) {
                return try { e.asDouble } catch (t: Exception) { e.asString.replace(",", "").toDoubleOrNull() ?: 0.0 }
            }
        }
        return 0.0
    }
}
