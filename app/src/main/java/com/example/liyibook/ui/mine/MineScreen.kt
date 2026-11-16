package com.example.liyibook.ui.mine

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.liyibook.model.GiftRecord
import com.example.liyibook.model.UserInfo
import com.example.liyibook.ui.BookViewModel
import com.example.liyibook.ui.ConfirmDialog
import com.example.liyibook.ui.InfoDialog
import com.example.liyibook.ui.InputDialog
import com.example.liyibook.ui.PanelCard
import com.example.liyibook.ui.PrimaryButton
import com.example.liyibook.ui.SectionTitle
import com.example.liyibook.ui.theme.BrandBlue
import com.example.liyibook.ui.theme.BrandRed
import com.example.liyibook.util.FormatUtil
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MineScreen(vm: BookViewModel, nav: NavController) {
    val user by vm.user.collectAsState()
    val books by vm.books.collectAsState()
    val context = LocalContext.current

    var showAgreement by remember { mutableStateOf<String?>(null) }
    var confirmClear by remember { mutableStateOf<Boolean?>(false) }
    var confirmLogout by remember { mutableStateOf<Boolean?>(false) }
    var pendingRecords by remember { mutableStateOf<List<GiftRecord>?>(null) }
    var pendingSource by remember { mutableStateOf("") }
    var importName by remember { mutableStateOf("") }
    var overwriteConfirm by remember { mutableStateOf(false) }

    val totalBooks = books.size
    val totalRecords = books.sumOf { it.count }
    val totalAmount = books.sumOf { it.total }

    val pickExcel = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val name = fileNameOf(context, uri).replace(Regex("\\.[^/.]+$"), "")
            pendingSource = name
            importName = name
            vm.parseExcel(uri) { pendingRecords = it }
        }
    }
    val createBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? -> if (uri != null) vm.exportBackup(uri) }
    val pickBackup = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) vm.importBackup(uri) { parsed -> if (parsed) { /* imported */ } }
    }

    Column(Modifier.fillMaxSize().background(Color(0xFFF2F2F7))) {
        // 顶部
        Box(
            Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(listOf(BrandRed, Color(0xFFE05A5A))))
                .padding(horizontal = 20.dp, vertical = 26.dp)
        ) {
            Text("我的", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }

        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            if (user == null) {
                LoginCard(
                    onLogin = {
                        val userInfo = UserInfo(
                            nickName = "用户",
                            avatarType = "emoji",
                            avatarEmoji = "🎉",
                            loginTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                        )
                        vm.login(userInfo)
                    },
                    onAgreement = { showAgreement = it }
                )
            } else {
                // 用户卡片
                PanelCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(56.dp).clip(RoundedCornerShape(50))
                                .background(Brush.linearGradient(listOf(Color(0xFF5B9BFF), Color(0xFF7C5BFF)))),
                            contentAlignment = Alignment.Center
                        ) { Text(user!!.avatarEmoji.ifBlank { "🎉" }, fontSize = 28.sp) }
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(user!!.nickName, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            Spacer(Modifier.height(4.dp))
                            Text("✓ 已登录", fontSize = 12.sp, color = BrandBlue)
                        }
                    }
                }

                SectionTitle("账本管理")
                PanelCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        QuickAction("📊", "导入账本", "xlsx / xls") {
                            pickExcel.launch("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                        }
                        QuickAction("📤", "导出备份", "保存数据") {
                            createBackup.launch("礼金账本备份_${FormatUtil.nowStamp()}.json")
                        }
                        QuickAction("📥", "导入备份", "恢复数据") { pickBackup.launch("application/json") }
                    }
                }

                SectionTitle("数据总览")
                PanelCard {
                    Text("累计礼金", fontSize = 12.sp, color = Color.Gray)
                    Text("¥${FormatUtil.moneyThousands(totalAmount)}", fontSize = 28.sp,
                        fontWeight = FontWeight.Bold, color = BrandRed)
                    Spacer(Modifier.height(12.dp))
                    Row {
                        Text("$totalBooks 本账谱", fontSize = 13.sp, color = Color(0xFF444444))
                        Spacer(Modifier.width(20.dp))
                        Text("$totalRecords 条记录", fontSize = 13.sp, color = Color(0xFF444444))
                    }
                }

                SectionTitle("更多操作")
                PanelCard {
                    Row(
                        Modifier.fillMaxWidth().clickable { confirmClear = true }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🗑️", fontSize = 18.sp)
                        Spacer(Modifier.width(12.dp))
                        Text("清空所有数据", color = Color(0xFFFA5151), fontSize = 15.sp,
                            modifier = Modifier.weight(1f))
                        Text("›", color = Color.Gray, fontSize = 20.sp)
                    }
                }

                Spacer(Modifier.height(20.dp))
                PrimaryButton(
                    "退出登录",
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    onClick = { confirmLogout = true }
                )
                Spacer(Modifier.height(16.dp))
                Text("数据仅保存在本机，卸载应用会丢失", fontSize = 11.sp, color = Color.Gray,
                    modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Text("礼金账本 · Android 版 v1.0", fontSize = 11.sp, color = Color.Gray,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
    }

    // ===== 弹窗 =====
    showAgreement?.let { type ->
        if (type == "privacy") {
            InfoDialog("隐私政策", "本应用为纯本地记账工具：所有礼金数据仅保存在你的手机本地，不会上传任何服务器；仅在你主动选择文件时，在本机读取并解析 Excel / 备份内容用于生成或恢复礼金记录，不收集、不上传任何个人信息。") { showAgreement = null }
        } else {
            InfoDialog("用户协议", "你确认：本应用仅提供礼金账本的本地记录与管理，数据由你自行保管；卸载应用或清除数据会导致数据丢失，建议定期使用「导出备份」保存。") { showAgreement = null }
        }
    }

    confirmClear?.let {
        ConfirmDialog("危险操作", "确定要清空所有数据吗？此操作不可恢复！", "清空", danger = true,
            onConfirm = { vm.clearAll(); confirmClear = null }, onDismiss = { confirmClear = null })
    }
    confirmLogout?.let {
        ConfirmDialog("提示", "确定退出登录吗？", "退出", danger = true,
            onConfirm = { vm.logout(); confirmLogout = null }, onDismiss = { confirmLogout = null })
    }

    pendingRecords?.let { records ->
        InputDialog("账本入库确认", importName, "账本名称", { importName = it }, {
            if (importName.isBlank()) vm.toast("请输入账本名称")
            else {
                val source = pendingSource.ifBlank { importName }
                pendingRecords = null
                val sameIdx = FormatUtil.findSameBookIndex(books, source)
                if (sameIdx >= 0) {
                    overwriteConfirm = true
                } else {
                    vm.toast(vm.saveImportedBook(importName, source, records))
                    nav.navigate("bookshelf")
                }
            }
        }, { pendingRecords = null })
    }

    if (overwriteConfirm) {
        val source = pendingSource.ifBlank { importName }
        val existing = books.getOrNull(FormatUtil.findSameBookIndex(books, source))
        ConfirmDialog(
            "检测到同一份 Excel",
            "《${existing?.title ?: ""}》已存在，本次导入将覆盖其中 ${existing?.count ?: 0} 条记录，是否继续？",
            "覆盖更新",
            onConfirm = {
                if (existing != null && pendingRecords != null) {
                    vm.updateBook(existing.id) { b ->
                        b.copy(
                            records = pendingRecords!!.toMutableList(),
                            count = pendingRecords!!.size,
                            total = pendingRecords!!.sumOf { it.amount },
                            sourceName = FormatUtil.baseNameOf(source),
                            fileName = FormatUtil.initialFileName(source),
                            date = FormatUtil.todayCN()
                        )
                    }
                    vm.toast("已覆盖更新")
                }
                overwriteConfirm = false
                nav.navigate("bookshelf")
            },
            onDismiss = { overwriteConfirm = false }
        )
    }

}

private fun fileNameOf(context: android.content.Context, uri: Uri): String {
    var name = ""
    context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
        null, null, null)?.use { c ->
        if (c.moveToFirst()) {
            val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0) name = c.getString(idx) ?: ""
        }
    }
    return name.ifBlank { "账本.xlsx" }
}

@Composable
private fun QuickAction(icon: String, label: String, desc: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick).padding(8.dp)
    ) {
        Text(icon, fontSize = 26.sp)
        Spacer(Modifier.height(6.dp))
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Text(desc, fontSize = 10.sp, color = Color.Gray)
    }
}

@Composable
private fun LoginCard(onLogin: () -> Unit, onAgreement: (String) -> Unit) {
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(30.dp))
        // Logo：三色书脊
        Row(
            Modifier
                .size(96.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.White),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf(Color(0xFFC8323C), Color(0xFF2D5A43), Color(0xFF3A5A9C)).forEach { c ->
                Box(Modifier.size(width = 14.dp, height = 46.dp).clip(RoundedCornerShape(3.dp)).background(c))
                Spacer(Modifier.width(4.dp))
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("礼金账本", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text("记录每一份情谊", fontSize = 13.sp, color = Color.Gray)
        Spacer(Modifier.height(28.dp))

        PanelCard {
            Text("本地登录", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text("数据仅存本机，无需联网与授权", fontSize = 12.sp, color = Color.Gray)
            Spacer(Modifier.height(16.dp))
            PrimaryButton("一键登录", modifier = Modifier.fillMaxWidth().height(46.dp), onClick = onLogin)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                FeatureItem("📊", "导入Excel")
                FeatureItem("🔍", "智能搜索")
                FeatureItem("✏️", "便捷编辑")
            }
        }

        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Text("登录即表示同意", fontSize = 11.sp, color = Color.Gray)
            Text("《用户协议》", fontSize = 11.sp, color = BrandBlue,
                modifier = Modifier.clickable { onAgreement("user") })
            Text("和", fontSize = 11.sp, color = Color.Gray)
            Text("《隐私政策》", fontSize = 11.sp, color = BrandBlue,
                modifier = Modifier.clickable { onAgreement("privacy") })
        }
    }
}

@Composable
private fun FeatureItem(icon: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(icon, fontSize = 20.sp)
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 11.sp, color = Color(0xFF444444))
    }
}
