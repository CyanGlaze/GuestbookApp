package com.example.liyibook.ui.detail

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.example.liyibook.model.Book
import com.example.liyibook.model.GiftRecord
import com.example.liyibook.ui.ActionSheet
import com.example.liyibook.ui.BookViewModel
import com.example.liyibook.ui.ConfirmDialog
import com.example.liyibook.ui.theme.BrandRed
import com.example.liyibook.util.FormatUtil
import com.example.liyibook.util.PinyinUtil

private val ALPHABET = "ABCDEFGHJKLMNOPQRSTWXYZ#".toList()
private val LEVEL_TAGS = mapOf(2 to "🎉", 3 to "⭐", 4 to "🔥", 5 to "🧧")
private val AVATAR_COLORS = listOf(
    Color(0xFFE8D5C4), Color(0xFFC4E8D5), Color(0xFFC4D5E8),
    Color(0xFFE8C4D5), Color(0xFFD5C4E8), Color(0xFFFFE0B2)
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DetailScreen(vm: BookViewModel, bookId: String, onBack: () -> Unit) {
    val books by vm.books.collectAsState()
    val book = books.firstOrNull { it.id == bookId }
    var searchKey by remember { mutableStateOf("") }
    var fabOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<EditTarget?>(null) }
    var actionRecord by remember { mutableStateOf<GiftRecord?>(null) }
    var deleteRecord by remember { mutableStateOf<GiftRecord?>(null) }
    var currentLetter by remember { mutableStateOf('A') }
    val scope = rememberCoroutineScope()

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri: Uri? -> if (uri != null) vm.exportExcel(bookId, uri) }

    val groups = remember(book, searchKey) { buildGroups(book, searchKey) }
    val totalCount = book?.records?.size ?: 0
    val totalAmount = book?.records?.sumOf { it.amount } ?: 0.0

    if (book == null) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center) {
            Text("账本不存在或已删除", fontSize = 15.sp, color = Color.Gray)
            Spacer(Modifier.height(12.dp))
            androidx.compose.material3.Button(onClick = onBack) { Text("返回") }
        }
        return
    }

    val listState = rememberLazyListState()

    Box(Modifier.fillMaxSize().background(Color(0xFFF2F2F7))) {
        Column(Modifier.fillMaxSize()) {
            // 顶部
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.linearGradient(listOf(BrandRed, Color(0xFFE05A5A))))
                    .padding(horizontal = 12.dp, vertical = 16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("‹", color = Color.White, fontSize = 30.sp,
                        modifier = Modifier.clickable(onClick = { onBack() }))
                    Spacer(Modifier.width(6.dp))
                    Text(book.title, color = Color.White, fontSize = 18.sp,
                        fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                }
            }

            // 搜索框
            Box(Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 16.dp, vertical = 10.dp)) {
                OutlinedTextField(
                    value = searchKey,
                    onValueChange = { searchKey = it },
                    placeholder = { Text("搜索姓名/拼音/地址") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Row(
                Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("共 $totalCount 人", fontSize = 12.sp, color = Color.Gray)
                Text("合计 ¥${FormatUtil.money(totalAmount)}", fontSize = 12.sp, color = BrandRed,
                    fontWeight = FontWeight.SemiBold)
            }

            // 列表 + 右侧字母索引
            Box(Modifier.fillMaxSize().weight(1f)) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 88.dp)
                ) {
                    groups.forEach { g ->
                        stickyHeader { GroupHeader(g.letter) }
                        items(g.items, key = { "${it.name}-${it.amount}-${it.address}" }) { rec ->
                            RecordCard(
                                rec = rec,
                                key = searchKey,
                                onClick = { editing = EditTarget(false, rec, rec.copy()) },
                                onLongClick = { actionRecord = rec }
                            )
                        }
                    }
                    if (groups.isEmpty()) {
                        item {
                            Column(Modifier.fillMaxWidth().padding(48.dp),
                                horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(if (searchKey.isBlank()) "📖" else "🔍", fontSize = 48.sp)
                                Spacer(Modifier.height(10.dp))
                                Text(if (searchKey.isBlank()) "还没有任何记录" else "未找到相关记录",
                                    fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                Text(if (searchKey.isBlank()) "点右下角 + 添加第一条礼金记录" else "试试其他关键词",
                                    fontSize = 12.sp, color = Color.Gray)
                            }
                        }
                    }
                }

                // 右侧字母索引
                Column(
                    Modifier
                        .align(Alignment.CenterEnd)
                        .padding(start = 0.dp, top = 8.dp, end = 2.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    ALPHABET.forEach { letter ->
                        Text(
                            letter.toString(),
                            fontSize = 10.sp,
                            color = if (letter == currentLetter) BrandRed else Color(0xFF8E8E93),
                            fontWeight = if (letter == currentLetter) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                                .clickable(onClick = {
                                    currentLetter = letter
                                    val idx = groups.indexOfFirst { it.letter == letter.toString() }
                                    if (idx >= 0) {
                                        val offset = groups.take(idx).sumOf { 1 + it.items.size }
                                        scope.launch { listState.animateScrollToItem(offset) }
                                    }
                                })
                        )
                    }
                }

                if (fabOpen) {
                    Column(
                        Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 168.dp),
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FabOption("📤", "分享") {
                            fabOpen = false
                            exportLauncher.launch("${FormatUtil.baseNameOf(book.title)}-礼金记录.xlsx")
                        }
                        FabOption("＋", "添加") {
                            fabOpen = false
                            editing = EditTarget(true, GiftRecord(), GiftRecord())
                        }
                    }
                }

                FloatingActionButton(
                    onClick = { fabOpen = !fabOpen },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                    containerColor = BrandRed,
                    contentColor = Color.White
                ) { Text(if (fabOpen) "×" else "+", fontSize = 26.sp) }
            }
        }
    }

    actionRecord?.let { rec ->
        ActionSheet(
            items = listOf("📝 编辑记录", "📋 复制记录", "🗑️ 删除记录"),
            onSelect = { i ->
                actionRecord = null
                when (i) {
                    0 -> editing = EditTarget(false, rec, rec.copy())
                    1 -> { vm.copyRecord(bookId, rec); vm.toast("已复制") }
                    2 -> deleteRecord = rec
                }
            },
            onDismiss = { actionRecord = null }
        )
    }

    deleteRecord?.let { rec ->
        ConfirmDialog(
            title = "确认删除", content = "确定删除 ${rec.name} 的记录吗？",
            confirmText = "删除", danger = true,
            onConfirm = { vm.deleteRecord(bookId, rec); deleteRecord = null; vm.toast("已删除") },
            onDismiss = { deleteRecord = null }
        )
    }

    editing?.let { target ->
        RecordEditDialog(
            adding = target.isAdding,
            record = target.draft,
            onField = { key, value ->
                editing = target.copy(draft = when (key) {
                    "姓名" -> target.draft.copy(name = value)
                    "礼金" -> target.draft.copy(amount = value.toDoubleOrNull() ?: 0.0)
                    "地址" -> target.draft.copy(address = value)
                    else -> target.draft.copy(remark = value)
                })
            },
            onSave = {
                val d = target.draft
                if (d.name.isBlank() || d.amount == 0.0) {
                    vm.toast("姓名和礼金必填")
                } else {
                    if (target.isAdding) vm.addRecord(bookId, d)
                    else vm.updateRecord(bookId, target.original, d)
                    editing = null
                    vm.toast("已保存")
                }
            },
            onDismiss = { editing = null }
        )
    }
}

private data class EditTarget(val isAdding: Boolean, val original: GiftRecord, val draft: GiftRecord)
private data class Group(val letter: String, val items: List<GiftRecord>)

private fun buildGroups(book: Book?, key: String): List<Group> {
    if (book == null) return emptyList()
    val filtered = book.records.filter { r ->
        PinyinUtil.smartMatch(r.name, key) || PinyinUtil.smartMatch(r.address, key)
    }
    val grouped = filtered.groupBy { PinyinUtil.getFirstLetter(it.name) }
    return grouped.keys.sortedWith(compareBy<String> { it == "#" }).map { Group(it, grouped[it]!!) }
}

@Composable
private fun GroupHeader(letter: String) {
    Text(
        letter,
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFEDEDF2))
            .padding(horizontal = 16.dp, vertical = 6.dp),
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = Color(0xFF5B6FD8)
    )
}

@Composable
private fun RecordCard(rec: GiftRecord, key: String, onClick: () -> Unit, onLongClick: () -> Unit) {
    val letter = PinyinUtil.getFirstLetter(rec.name)
    val colorIdx = (letter.firstOrNull()?.code ?: 0) % AVATAR_COLORS.size
    val level = when {
        rec.amount >= 1000 -> 5
        rec.amount >= 500 -> 4
        rec.amount >= 200 -> 3
        rec.amount >= 100 -> 2
        rec.amount >= 50 -> 1
        else -> 0
    }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 5.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(36.dp).clip(RoundedCornerShape(50)).background(AVATAR_COLORS[colorIdx]),
                contentAlignment = Alignment.Center
            ) { Text(letter, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF444444)) }
            Spacer(Modifier.width(10.dp))
            Text(highlight(rec.name, key), fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f))
            Text("¥${FormatUtil.money(rec.amount)}", color = BrandRed, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            LEVEL_TAGS[level]?.let { tag ->
                Spacer(Modifier.width(4.dp))
                Text(tag, fontSize = 14.sp)
            }
        }
        if (rec.address.isNotBlank() || rec.remark.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Row {
                if (rec.address.isNotBlank()) {
                    Text("📍 ", fontSize = 12.sp)
                    Text(highlight(rec.address, key), fontSize = 12.sp, color = Color(0xFF666666),
                        modifier = Modifier.weight(1f))
                } else Spacer(Modifier.weight(1f))
                if (rec.remark.isNotBlank()) {
                    Text("📝 ${rec.remark}", fontSize = 12.sp, color = Color(0xFF8E8E93))
                }
            }
        }
    }
}

@Composable
private fun highlight(text: String, key: String): AnnotatedString = buildAnnotatedString {
    PinyinUtil.highlight(text, key).forEach { seg ->
        if (seg.hit) withStyle(SpanStyle(background = Color(0xFFFFE082), color = Color(0xFF1C1C1E))) {
            append(seg.text)
        } else append(seg.text)
    }
}

@Composable
private fun FabOption(icon: String, label: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(icon, fontSize = 16.sp)
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 13.sp)
    }
}

@Composable
private fun RecordEditDialog(
    adding: Boolean, record: GiftRecord,
    onField: (String, String) -> Unit, onSave: () -> Unit, onDismiss: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (adding) "新增记录" else "编辑记录", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                listOf("姓名", "礼金", "地址", "备注").forEach { key ->
                    val value = when (key) {
                        "姓名" -> record.name
                        "礼金" -> if (record.amount == 0.0) "" else FormatUtil.money(record.amount)
                        "地址" -> record.address
                        else -> record.remark
                    }
                    OutlinedTextField(
                        value = value,
                        onValueChange = { onField(key, it) },
                        label = { Text(key) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = onSave) { Text("保存") } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
