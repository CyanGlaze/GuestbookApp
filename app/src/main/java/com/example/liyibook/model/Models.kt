package com.example.liyibook.model

/**
 * 单条礼金记录。字段与 Excel 表头一致，用 Gson 原样存入本地 JSON。
 */
data class GiftRecord(
    val name: String = "",
    val amount: Double = 0.0,
    val address: String = "",
    val remark: String = ""
)

/**
 * 一本账本。对应小程序 shelf 中的一本书。
 */
data class Book(
    val id: String = "",
    val title: String = "",
    val sourceName: String = "",   // 导入文件基础名（同一份 Excel 的识别主键）
    val fileName: String = "",     // 导出的 xlsx 文件名
    val sourceUri: String = "",    // 已授权的真机 Excel 文件 URI（持久化，用于直接写回原文件）
    val color: String = DEFAULT_COLORS[0],
    val desc: String = "",
    val records: MutableList<GiftRecord> = mutableListOf(),
    val count: Int = 0,
    val total: Double = 0.0,
    val date: String = ""
) {
    companion object {
        val DEFAULT_COLORS = listOf(
            "#c8323c", "#b8863b", "#2d5a43", "#3a5a9c", "#7a4b8c", "#b5546f"
        )
    }
}

/**
 * 本地用户信息。Android 无微信登录 SDK，采用本地头像 + 昵称登录，字段保持兼容。
 */
data class UserInfo(
    val nickName: String = "用户",
    val avatarType: String = "emoji", // image | emoji
    val avatarPath: String = "",
    val avatarEmoji: String = "🎉",
    val avatarBg: String = "linear-gradient(135deg,#5b9bff,#7c5bff)",
    val loginTime: String = ""
)
