# 礼金账本 · Android 版

由原微信小程序转换而来的**标准 Android Studio 项目**。
Kotlin + Jetpack Compose + Material 3，单模块 `:app`，纯本地存储。

## 构建环境

| 项 | 版本 |
|---|---|
| Gradle | 9.9.1（`gradle-9.9.1-all.zip`，腾讯源；首个支持 JDK 27 的系列 ≥9.8.0） |
| Android Gradle Plugin | 9.5.1（内置 Kotlin，无需 `kotlin-android` 插件） |
| Kotlin | 2.4.21（由 Compose Compiler 插件传递锁定内置编译器） |
| Compose BOM | 2026.09.00 |
| compileSdk / targetSdk | 37 |
| minSdk | 24 |
| JDK | 17 及以上（本机使用 `D:\Java\jdk-27.0.1.0.3`） |

> AGP 9.x 为重大版本：Kotlin 编译内置于 AGP，build 文件中不再 apply
> `org.jetbrains.kotlin.android`；Compose Compiler 插件（`org.jetbrains.kotlin.plugin.compose`）
> 仍保留，版本与 Kotlin 一致。`kotlinOptions` 已删除，jvmTarget 自动取自
> `compileOptions.targetCompatibility`。

## 腾讯源配置（已内置，无需手动改）

- `gradle/wrapper/gradle-wrapper.properties`
  `distributionUrl=https://mirrors.cloud.tencent.com/gradle/gradle-9.9.1-all.zip`
- `settings.gradle.kts`：`pluginManagement` 与 `dependencyResolutionManagement` 均将
  `https://mirrors.cloud.tencent.com/nexus/repository/maven-public/` 置于首位，
  后接 `google()` / `mavenCentral()` 兜底。

## 打开与运行

1. Android Studio（支持 AGP 9 的版本）→ File → Open → 选择本目录。
2. JDK 设为 17+（本机 `D:\Java\jdk-27.0.1.0.3`）。首次同步自动从腾讯源下载 Gradle 9.9.1 与依赖。
3. compileSdk 37 需 SDK Platform `android-37.0`（本机 `D:\AndroidSdk` 已装）。
4. 连接真机或启动模拟器，`Run 'app'`。

命令行（可选）：
```bash
./gradlew assembleDebug      # Windows: gradlew.bat assembleDebug
```

## 与原小程序的功能对应

| 小程序 | Android |
|---|---|
| `pages/bookshelf` 书架 | `ui/bookshelf/BookshelfScreen.kt`（网格/列表切换、长按编辑/复制/删除、Excel 导入） |
| `pages/detail` 详情 | `ui/detail/DetailScreen.kt`（拼音搜索+高亮、字母索引、记录增删改、Excel 导出） |
| `pages/mine` 我的 | `ui/mine/MineScreen.kt`（登录、备份导入/导出、统计、清空、协议） |
| `wx.getStorageSync` | `data/Store.kt`（SharedPreferences + Gson） |
| `xlsx.mini.js` / `excelSync.js` | `util/XlsxUtil.kt` + `util/FormatUtil.kt`（零第三方库的 XLSX 读写，避免 DEX 方法数膨胀） |
| `pinyin-match.js` + 姓氏表 | `util/PinyinUtil.kt`（Android 内置 ICU Transliterator 生成拼音，无需打包字典） |
| `app.json` tabBar | `ui/AppNav.kt`（Navigation Compose + BottomNavigation） |

## 差异说明

- **登录**：Android 无微信登录 SDK，改为本地一键登录（头像 emoji + 默认昵称），数据仍只存本机。
- **文件选择**：用系统 SAF（`ActivityResultContracts.GetContent` / `CreateDocument`），替代 `chooseMessageFile` / `shareFileMessage`，无需存储权限。
- **备份格式**：导出为「中文表头」JSON（姓名/礼金/地址/备注），可与原小程序备份互通；导入兼容中英文表头。
- **Excel**：自实现读写器支持 `.xlsx` 的常规表头与四列数据，不依赖 Apache POI。

## 项目结构

```
├── settings.gradle.kts          # 仓库（腾讯源优先）
├── build.gradle.kts             # 插件声明
├── gradle/libs.versions.toml    # 版本目录
├── gradle/wrapper/              # gradle-wrapper.jar + 腾讯源 all.zip 配置
├── gradlew / gradlew.bat
└── app/
    ├── build.gradle.kts
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/com/example/liyibook/
        │   ├── MainActivity.kt
        │   ├── model/Models.kt           # Book / GiftRecord / UserInfo
        │   ├── data/Store.kt             # 本地存储 + 备份编解码
        │   ├── util/                     # XLSX / 拼音 / 格式化
        │   └── ui/                       # 导航 + 三页面 + 组件 + 主题
        └── res/                          # 主题、字符串、矢量图标、自适应图标
```

> 原小程序源码（`pages/`、`utils/`、`images/`、`app.*` 等）已清理出项目（移入回收站，可恢复）。
> 功能映射见上表；如需追溯，可从系统回收站或 git 历史找回。
