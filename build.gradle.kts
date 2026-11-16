// 顶层构建文件：声明全项目使用的插件版本，仅在子模块中 apply
// AGP 9.x 内置 Kotlin 编译，无需 org.jetbrains.kotlin.android 插件
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.compose.compiler) apply false
}
