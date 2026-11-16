package com.example.liyibook.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.liyibook.data.Store
import com.example.liyibook.model.Book
import com.example.liyibook.model.GiftRecord
import com.example.liyibook.model.UserInfo
import com.example.liyibook.util.FormatUtil
import com.example.liyibook.util.XlsxUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 全局状态与业务动作，对应小程序三个 Page 的 data + 方法。
 * 所有写操作落盘到 Store（SharedPreferences）。
 */
class BookViewModel(app: Application) : AndroidViewModel(app) {

    init { Store.init(app) }

    private val _books = MutableStateFlow<List<Book>>(Store.loadBooks())
    val books: StateFlow<List<Book>> = _books.asStateFlow()

    private val _user = MutableStateFlow<UserInfo?>(Store.loadUser())
    val user: StateFlow<UserInfo?> = _user.asStateFlow()

    private val _viewMode = MutableStateFlow(Store.loadViewMode())
    val viewMode: StateFlow<String> = _viewMode.asStateFlow()

    private val _toast = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val toast: SharedFlow<String> = _toast.asSharedFlow()

    val isLogin: Boolean get() = _user.value != null

    fun toast(msg: String) { _toast.tryEmit(msg) }

    private fun persist() { Store.saveBooks(_books.value) }

    // ===== 用户 =====
    fun login(user: UserInfo) {
        Store.saveUser(user)
        _user.value = user
        toast("登录成功")
    }

    fun logout() {
        Store.clearUser()
        _user.value = null
        toast("已退出登录")
    }

    // ===== 视图 =====
    fun toggleViewMode() {
        val next = if (_viewMode.value == "grid") "list" else "grid"
        _viewMode.value = next
        Store.saveViewMode(next)
    }

    fun reload() { _books.value = Store.loadBooks() }

    // ===== 账本 =====
    fun addBook(book: Book) {
        _books.value = listOf(book) + _books.value
        persist()
    }

    fun updateBook(id: String, transform: (Book) -> Book) {
        _books.value = _books.value.map { if (it.id == id) transform(it) else it }
        persist()
    }

    fun deleteBook(id: String) {
        _books.value = _books.value.filter { it.id != id }
        persist()
    }

    fun copyBook(id: String) {
        val idx = _books.value.indexOfFirst { it.id == id }
        if (idx < 0) return
        val original = _books.value[idx]
        val now = java.util.Date()
        val md = java.text.SimpleDateFormat("MMdd", java.util.Locale.getDefault()).format(now)
        val copy = original.copy(
            id = System.currentTimeMillis().toString(),
            title = "${original.title}_副本$md",
            sourceName = "",
            fileName = FormatUtil.initialFileName("${original.title}_副本$md"),
            date = FormatUtil.todayCN(),
            records = original.records.map { it.copy() }.toMutableList()
        )
        val list = _books.value.toMutableList()
        list.add((idx + 1).coerceAtMost(list.size), copy)
        _books.value = list
        persist()
        toast("复制成功")
    }

    /** 保存/覆盖一本账本（导入 Excel 用），返回提示文案 */
    fun saveImportedBook(
        tempName: String,
        sourceNameRaw: String,
        records: List<GiftRecord>
    ): String {
        val base = FormatUtil.baseNameOf(sourceNameRaw)
        val fileName = FormatUtil.initialFileName(base)
        val total = records.sumOf { it.amount }
        val list = _books.value.toMutableList()
        val sameIdx = FormatUtil.findSameBookIndex(list, sourceNameRaw)
        val date = FormatUtil.todayCN()
        if (sameIdx >= 0) {
            val old = list[sameIdx]
            list[sameIdx] = old.copy(
                records = records.toMutableList(),
                count = records.size,
                total = total,
                sourceName = base,
                fileName = fileName,
                date = date
            )
            _books.value = list
            persist()
            return "已覆盖更新"
        }
        list.add(
            0,
            Book(
                id = System.currentTimeMillis().toString(),
                title = tempName.trim(),
                sourceName = base,
                fileName = fileName,
                records = records.toMutableList(),
                count = records.size,
                total = total,
                date = date
            )
        )
        _books.value = list
        persist()
        return "导入成功"
    }

    // ===== 记录 CRUD（详情页）=====
    fun addRecord(bookId: String, record: GiftRecord) = mutateRecords(bookId) {
        it.add(0, record)
    }

    fun copyRecord(bookId: String, record: GiftRecord) = mutateRecords(bookId) {
        it.add(0, record.copy())
    }

    fun updateRecord(bookId: String, original: GiftRecord, updated: GiftRecord) =
        mutateRecords(bookId) { list ->
            val idx = list.indexOfFirst { r -> r.name == original.name && r.amount == original.amount }
            if (idx >= 0) list[idx] = updated
        }

    fun deleteRecord(bookId: String, record: GiftRecord) = mutateRecords(bookId) { list ->
        val idx = list.indexOfFirst { r -> r.name == record.name && r.amount == record.amount }
        if (idx >= 0) list.removeAt(idx)
    }

    private fun mutateRecords(bookId: String, action: (MutableList<GiftRecord>) -> Unit) {
        _books.value = _books.value.map { b ->
            if (b.id == bookId) {
                val rs = b.records.map { it.copy() }.toMutableList()
                action(rs)
                b.copy(
                    records = rs,
                    count = rs.size,
                    total = rs.sumOf { it.amount }
                )
            } else b
        }
        persist()
    }

    // ===== 备份 =====
    fun exportBackup(uri: Uri) = viewModelScope.launch(Dispatchers.IO) {
        val json = Store.exportBackupJson(_books.value)
        getApplication<Application>().contentResolver.openOutputStream(uri)?.use {
            it.write(json.toByteArray(Charsets.UTF_8))
        }
        withContext(Dispatchers.Main) { toast("导出成功") }
    }

    fun importBackup(uri: Uri, onDone: (Boolean) -> Unit) = viewModelScope.launch(Dispatchers.IO) {
        val text = getApplication<Application>().contentResolver.openInputStream(uri)
            ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
        val parsed = text?.let { Store.importBackupJson(it) }
        withContext(Dispatchers.Main) {
            if (parsed != null) {
                _books.value = parsed
                persist()
                toast("导入成功")
                onDone(true)
            } else {
                toast("备份文件格式错误")
                onDone(false)
            }
        }
    }

    fun clearAll() {
        Store.clearBooks()
        _books.value = emptyList()
        toast("已清空")
    }

    // ===== Excel 导入导出 =====
    fun parseExcel(uri: Uri, onResult: (List<GiftRecord>) -> Unit) =
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val parsed = getApplication<Application>().contentResolver.openInputStream(uri)?.use {
                    XlsxUtil.read(it)
                }
                withContext(Dispatchers.Main) {
                    if (parsed == null || parsed.records.isEmpty()) toast("文件内容为空或解析失败")
                    else onResult(parsed.records)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { toast("文件解析失败") }
            }
        }

    fun exportExcel(bookId: String, uri: Uri) = viewModelScope.launch(Dispatchers.IO) {
        val book = _books.value.firstOrNull { it.id == bookId } ?: return@launch
        val bytes = XlsxUtil.write(book.records)
        getApplication<Application>().contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
        withContext(Dispatchers.Main) { toast("已导出") }
    }
}
