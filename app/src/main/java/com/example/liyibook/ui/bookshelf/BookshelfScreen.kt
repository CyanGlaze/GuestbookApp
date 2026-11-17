package com.example.liyibook.ui.bookshelf

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.liyibook.model.Book
import com.example.liyibook.model.GiftRecord
import com.example.liyibook.ui.ActionSheet
import com.example.liyibook.ui.BookViewModel
import com.example.liyibook.ui.ConfirmDialog
import com.example.liyibook.ui.EmptyState
import com.example.liyibook.ui.InputDialog
import com.example.liyibook.ui.theme.BrandBlue
import com.example.liyibook.ui.theme.BrandRed
import com.example.liyibook.ui.theme.AmbientBackground
import com.example.liyibook.ui.theme.glass
import com.example.liyibook.util.FileUriUtil
import com.example.liyibook.util.FormatUtil

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookshelfScreen(vm: BookViewModel, nav: NavController) {
    val books by vm.books.collectAsState()
    val user by vm.user.collectAsState()
    val viewMode by vm.viewMode.collectAsState()
    val locked = user == null

    var editBook by remember { mutableStateOf<Book?>(null) }
    var actionBook by remember { mutableStateOf<Book?>(null) }
    var deleteBook by remember { mutableStateOf<Book?>(null) }
    var pendingRecords by remember { mutableStateOf<List<GiftRecord>?>(null) }
    var pendingSource by remember { mutableStateOf("") }
    var importName by remember { mutableStateOf("") }
    var editTitle by remember { mutableStateOf("") }
    var editDesc by remember { mutableStateOf("") }
    var editColor by remember { mutableStateOf(Book.DEFAULT_COLORS[0]) }
    var overwriteTarget by remember { mutableStateOf<OverwriteTarget?>(null) }
    var pendingSourceUri by remember { mutableStateOf("") }
    var permissionDenied by remember { mutableStateOf(false) }

    val context = androidx.compose.ui.platform.LocalContext.current
    // OpenDocument 才能取得持久化写权限，从而直接写回真机原 Excel
    val pickExcel = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val name = FileUriUtil.displayName(context, uri)
            val defaultName = name.replace(Regex("\\.[^/.]+$"), "")
            val granted = FileUriUtil.takePermission(context, uri)
            pendingSourceUri = if (granted) uri.toString() else ""
            if (!granted) permissionDenied = true
            pendingSource = defaultName
            importName = defaultName
            vm.parseExcel(uri) { records -> pendingRecords = records }
        }
    }
    fun launchImport() = pickExcel.launch(FileUriUtil.EXCEL_MIME)

    AmbientBackground {
    Column(Modifier.fillMaxSize()) {
        // 顶部渐变头
        Box(
            Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(listOf(BrandRed, Color(0xFFE05A5A))))
                .padding(horizontal = 20.dp, vertical = 22.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("📚 礼金书架", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (locked) "请登录后查看" else "共 ${books.size} 本账谱",
                        color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp
                    )
                }
                Box(
                    Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.25f))
                        .clickable { nav.navigate("mine") },
                    contentAlignment = Alignment.Center
                ) {
                    Text(if (user != null) (user!!.avatarEmoji.takeIf { it.isNotBlank() } ?: "👤") else "👤", fontSize = 22.sp)
                }
            }
        }

        when {
            locked -> EmptyState("🔒", "请先登录", "登录后即可查看与管理你的礼金账本", "去登录") { nav.navigate("mine") }
            books.isEmpty() -> EmptyState("📖", "书架空空如也", "记录人情往来，从此不再乱", "导入账本") {
                launchImport()
            }
            else -> {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        if (viewMode == "grid") "☰ 列表" else "▦ 网格",
                        color = BrandBlue, fontSize = 14.sp,
                        modifier = Modifier
                            .glass(RoundedCornerShape(20.dp), elevation = 4.dp)
                            .clickable { vm.toggleViewMode() }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                if (viewMode == "grid") {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(books, key = { it.id }) { book ->
                            BookGridCard(
                                book,
                                onClick = { nav.navigate("detail/${book.id}") },
                                onLongClick = { actionBook = book }
                            )
                        }
                        item {
                            AddCard(onClick = {
                                launchImport()
                            })
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(books, key = { it.id }) { book ->
                            BookListRow(
                                book,
                                onClick = { nav.navigate("detail/${book.id}") },
                                onLongClick = { actionBook = book }
                            )
                        }
                        item {
                            AddListRow(onClick = {
                                launchImport()
                            })
                        }
                    }
                }
            }
        }
    }
    } // AmbientBackground

    // 长按操作菜单
    actionBook?.let { book ->
        ActionSheet(
            items = listOf("✏️ 编辑账本", "📋 复制账本", "🗑️ 删除账本"),
            onSelect = { i ->
                actionBook = null
                when (i) {
                    0 -> {
                        editBook = book
                        editTitle = book.title
                        editDesc = book.desc
                        editColor = book.color
                    }
                    1 -> vm.copyBook(book.id)
                    2 -> deleteBook = book
                }
            },
            onDismiss = { actionBook = null }
        )
    }

    // 编辑账本弹窗
    editBook?.let {
        EditBookDialog(
            title = editTitle, desc = editDesc, color = editColor,
            onTitle = { editTitle = it }, onDesc = { editDesc = it }, onColor = { editColor = it },
            onSave = {
                if (editTitle.isBlank()) vm.toast("请输入账本名称")
                else {
                    vm.updateBook(it.id) { b -> b.copy(title = editTitle.trim(), desc = editDesc.trim(), color = editColor) }
                    editBook = null
                    vm.toast("保存成功")
                }
            },
            onDismiss = { editBook = null }
        )
    }

    // 删除确认
    deleteBook?.let { book ->
        ConfirmDialog(
            title = "删除确认", content = "确定要删除《${book.title}》吗？删除后无法恢复。",
            confirmText = "删除", danger = true,
            onConfirm = { vm.deleteBook(book.id); deleteBook = null; vm.toast("已删除") },
            onDismiss = { deleteBook = null }
        )
    }

    // 导入确认弹窗（含同名覆盖提示）
    pendingRecords?.let { records ->
        val total = records.sumOf { it.amount }
        InputDialog(
            title = "账本入库确认",
            value = importName, label = "账本名称",
            onValueChange = { importName = it },
            onConfirm = {
                if (importName.isBlank()) {
                    vm.toast("请输入账本名称")
                } else {
                    val source = pendingSource.ifBlank { importName }
                    val sameIdx = FormatUtil.findSameBookIndex(books, source)
                    pendingRecords = null
                    if (sameIdx >= 0) {
                        val existing = books[sameIdx]
                        // 覆盖需要二次确认：用另一个弹窗状态
                        overwriteTarget = OverwriteTarget(existing, records, importName.trim(), source)
                    } else {
                        vm.toast(vm.saveImportedBook(importName, source, records, pendingSourceUri))
                    }
                }
            },
            onDismiss = { pendingRecords = null }
        )
    }

    overwriteTarget?.let { t ->
        ConfirmDialog(
            title = "检测到同一份 Excel",
            content = "《${t.book.title}》已存在，本次导入将覆盖其中 ${t.book.count} 条记录，是否继续？（书架名称保持不变）",
            confirmText = "覆盖更新",
            onConfirm = {
                vm.updateBook(t.book.id) { b ->
                    b.copy(
                        records = t.records.toMutableList(),
                        count = t.records.size,
                        total = t.records.sumOf { it.amount },
                        sourceName = FormatUtil.baseNameOf(t.source),
                        fileName = FormatUtil.initialFileName(t.source),
                        sourceUri = pendingSourceUri.ifBlank { b.sourceUri },
                        date = FormatUtil.todayCN()
                    )
                }
                overwriteTarget = null
                vm.toast("已覆盖更新")
            },
            onDismiss = { overwriteTarget = null }
        )
    }

    if (permissionDenied) {
        ConfirmDialog(
            title = "未获得文件修改权限",
            content = "系统未授予该文件的持久写权限，导入的记录只能保存在本机，无法写回原 Excel。\n\n如需直接修改原文件，请在系统文件选择器中选择存储在手机内部存储的 Excel 文件（部分网盘/聊天气泡中的文件不支持写回）。",
            confirmText = "知道了", dismissText = "",
            onConfirm = { permissionDenied = false }, onDismiss = { permissionDenied = false }
        )
    }
}

private data class OverwriteTarget(
    val book: Book, val records: List<GiftRecord>, val name: String, val source: String
)

/** 从 SAF Uri 读取显示文件名 */
private fun fileNameOf(context: android.content.Context, uri: android.net.Uri): String {
    var name = ""
    context.contentResolver.query(
        uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null
    )?.use { c ->
        if (c.moveToFirst()) {
            val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0) name = c.getString(idx) ?: ""
        }
    }
    return name.ifBlank { "账本.xlsx" }
}

@Composable
private fun BookGridCard(book: Book, onClick: () -> Unit, onLongClick: () -> Unit) {
    val solid = Color(android.graphics.Color.parseColor(book.color))
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(solid.copy(alpha = 0.62f))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(12.dp)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(96.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(solid),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("礼尚往来", color = Color.White, fontSize = 11.sp)
                Text("囍", color = Color(0xFFFFD98A), fontSize = 30.sp)
                Text("嘉宾 · 礼簿", color = Color.White, fontSize = 10.sp)
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(book.title, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
        if (book.desc.isNotBlank()) Text(book.desc, fontSize = 11.sp, color = Color(0xFF666666), maxLines = 1)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("¥${FormatUtil.money(book.total)}", color = BrandRed, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.width(6.dp))
            Text("|", color = Color.Gray, fontSize = 12.sp)
            Spacer(Modifier.width(6.dp))
            Text("${book.count}人", color = Color.Gray, fontSize = 12.sp)
        }
        Text(FormatUtil.formatDateCN(book.date), color = Color.Gray, fontSize = 11.sp)
    }
}

@Composable
private fun AddCard(onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .height(230.dp)
            .glass(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("+", fontSize = 40.sp, color = BrandRed)
        Text("新增账本", fontSize = 13.sp, color = Color.Gray)
    }
}

@Composable
private fun BookListRow(book: Book, onClick: () -> Unit, onLongClick: () -> Unit) {
    val solid = Color(android.graphics.Color.parseColor(book.color))
    Row(
        Modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(12.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(6.dp).clip(RoundedCornerShape(3.dp)).background(solid))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(book.title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            if (book.desc.isNotBlank()) Text(book.desc, fontSize = 11.sp, color = Color.Gray)
            Text("${book.count}人 · ${FormatUtil.formatDateCN(book.date)}", fontSize = 11.sp, color = Color.Gray)
        }
        Text("¥${FormatUtil.money(book.total)}", color = BrandRed, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text("›", color = Color.Gray, fontSize = 22.sp)
    }
}

@Composable
private fun AddListRow(onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("＋", fontSize = 22.sp, color = BrandRed)
        Spacer(Modifier.width(12.dp))
        Text("新增账本", color = Color.Gray, fontSize = 14.sp)
    }
}

@Composable
private fun EditBookDialog(
    title: String, desc: String, color: String,
    onTitle: (String) -> Unit, onDesc: (String) -> Unit, onColor: (String) -> Unit,
    onSave: () -> Unit, onDismiss: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑账本", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                androidx.compose.material3.OutlinedTextField(
                    value = title, onValueChange = onTitle, label = { Text("账本名称") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                androidx.compose.material3.OutlinedTextField(
                    value = desc, onValueChange = onDesc, label = { Text("账本说明") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(14.dp))
                Text("书本颜色", fontSize = 13.sp, color = Color.Gray)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Book.DEFAULT_COLORS.forEach { c ->
                        Box(
                            Modifier
                                .size(30.dp)
                                .clip(RoundedCornerShape(50))
                                .background(Color(android.graphics.Color.parseColor(c)))
                                .clickable { onColor(c) }
                                .padding(if (c == color) 3.dp else 0.dp)
                        )
                    }
                }
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = onSave) { Text("保存") } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
