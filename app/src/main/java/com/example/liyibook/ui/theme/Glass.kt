package com.example.liyibook.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * iOS 27 液态玻璃配方（对齐原小程序 app.wxss 的 --lg-* / --rg-*）：
 * 低透填充 + 环境光透色 + 渐变高光描边 + 双层柔和投影。
 * Compose 无 backdrop-filter，采用与原小程序 detail 页一致的「伪玻璃」方案：
 * 底层铺环境光球，卡片半透明叠加，视觉上等效且兼容 minSdk 24。
 */
object LiquidGlass {
    val FillTop = Color.White.copy(alpha = 0.74f)
    val FillBottom = Color.White.copy(alpha = 0.54f)
    val FillStrongTop = Color.White.copy(alpha = 0.88f)
    val FillStrongBottom = Color.White.copy(alpha = 0.68f)
    val Edge = Color.White.copy(alpha = 0.78f)
    val Shadow = Color(0x211F263F)
    val Radius = 18.dp
}

/** 环境光背景：多层径向光球，复刻小程序 page 的 radial-gradient 背景 */
@Composable
fun AmbientBackground(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(BgGray)
            val w = size.width
            val h = size.height
            fun orb(cx: Float, cy: Float, r: Float, color: Color) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(color, Color.Transparent),
                        center = Offset(cx, cy),
                        radius = r
                    ),
                    radius = r,
                    center = Offset(cx, cy)
                )
            }
            orb(w * 0.08f, h * 0.02f, w * 0.60f, Color(0x4D667EEA))  // 左上 蓝紫
            orb(w * 0.95f, h * 0.18f, w * 0.55f, Color(0x40764BA2))  // 右上 紫
            orb(w * 0.15f, h * 0.78f, w * 0.52f, Color(0x3858A6FF))  // 左下 蓝
            orb(w * 0.92f, h * 0.96f, w * 0.48f, Color(0x2BFF956F))  // 右下 橙
            orb(w * 0.50f, h * 1.10f, w * 0.45f, Color(0x2234C789))  // 底部 绿
        }
        content()
    }
}

/** iOS 27 液态玻璃：半透渐变填充 + 高光描边 + 柔和投影 */
fun Modifier.glass(
    shape: RoundedCornerShape = RoundedCornerShape(LiquidGlass.Radius),
    strong: Boolean = false,
    elevation: Dp = 10.dp
): Modifier = this
    .shadow(
        elevation = elevation, shape = shape, clip = false,
        ambientColor = LiquidGlass.Shadow, spotColor = LiquidGlass.Shadow
    )
    .background(
        brush = Brush.verticalGradient(
            listOf(
                if (strong) LiquidGlass.FillStrongTop else LiquidGlass.FillTop,
                if (strong) LiquidGlass.FillStrongBottom else LiquidGlass.FillBottom
            )
        ),
        shape = shape
    )
    .border(width = 1.dp, color = LiquidGlass.Edge, shape = shape)
