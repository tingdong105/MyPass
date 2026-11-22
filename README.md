<div align="center">

<img src="docs/img/icon.png" width="128" height="128" alt="我的密码本 · 抱着钥匙的小橘猫" />

# 我的密码本

**让一个密码管理全部密码**

本地加密存储 · 多密码本分类 · 随机密码生成 · 自选云端备份

<p>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-MIT-blue?style=flat-square" alt="开源协议：MIT" /></a>
  <img src="https://img.shields.io/badge/UI-Miuix-FABB79?style=flat-square" alt="Miuix 界面" />
  <img src="https://img.shields.io/badge/Android-9.0%2B-3DDC84?style=flat-square&logo=android&logoColor=white" alt="Android 9.0 及以上" />
  <a href="../../releases"><img src="https://img.shields.io/badge/下载-Releases-2EA44F?style=flat-square&logo=github&logoColor=white" alt="前往 Releases 下载" /></a>
</p>

[功能一览](#功能一览) · [下载安装](#下载安装) · [使用指南](#使用指南) · [数据与安全](#数据与安全) · [源码构建](#源码构建) · [参与交流](#参与交流)

</div>

## 项目简介

软件、网站、小程序……账号越来越多，密码也越来越难记。

**我的密码本（MyPass）**是一款使用 Kotlin + Jetpack Compose 开发的 Android 个人密码管理工具。通过一个主密码解锁，集中保存账号、密码、网址、邮箱和备注，再按密码本、标签与类型整理，方便随时查找和复制。

日常管理可离线使用，无需注册 MyPass 账号；需要备份时，可以导出本地文件，也可以连接自己的 WebDAV 或对象存储服务。界面采用 Miuix，支持明暗主题和可选的悬浮玻璃底栏。

## 功能一览

| 功能 | 当前支持 |
| --- | --- |
| 密码管理 | 新增、查看、编辑和复制密码记录，保存平台、账号、网址、邮箱与备注 |
| 分类整理 | 多密码本管理，自定义标签与类型，内置软件、网站和小程序类型 |
| 搜索与排序 | 在当前密码本中按平台、账号、网址、类型和标签搜索，按名称、创建时间或更新时间排序 |
| 密码生成 | 生成 8–64 位随机密码，自选大写字母、小写字母、数字与符号，查看复制历史并多选删除 |
| 应用锁 | 九宫格图案、4 位数字、6 位数字或数字字母混合密码，支持修改主密码与切换解锁方式 |
| 回收站 | 查看删除记录，单条或多选恢复、永久删除、清空回收站；记录保留 30 天，过期后在访问相关数据时清理 |
| 本地备份 | 导出和还原 `.mp` 文件，默认保存到下载目录，也可自选导出目录 |
| 云端备份 | WebDAV、AWS S3、腾讯云 COS、阿里云 OSS、七牛云 Kodo，支持连接测试、备份历史和按设备保留份数 |
| 外观设置 | 跟随系统、亮色或暗色主题，可选悬浮底栏与液态玻璃效果（Android 13+） |
| 显示与复制 | 密码全显示、半隐藏或全隐藏，自定义隐藏符号，设置剪贴板清除时间 |

## 下载安装

前往 **[Releases 页面](../../releases)** 查看已发布版本及更新说明。版本发布后，可在对应版本的附件中下载 APK 安装包。

支持 **Android 9.0（API 28）及以上版本**，当前构建仅包含 **ARM64（`arm64-v8a`）** 架构。

## 使用指南

1. **设置主密码**：首次打开时选择解锁方式，并再次输入确认。
2. **添加记录**：在首页新增密码，填写账号信息，按需选择密码本、类型和标签。
3. **查找与复制**：切换密码本、搜索或排序找到记录，在详情页查看和复制；需要新密码时进入「生成」。
4. **建立备份**：在「设置 → 备份与还原」导出本地文件，或配置自己的云存储后点击「立即备份」。

### 备份与还原

- **本地备份**：导出 `.mp` 文件；换设备时，在新设备设置主密码后选择文件还原。
- **云端备份**：选择存储服务并填写连接信息，可先测试连接，再保存配置、上传备份或查看云端历史。保留份数按设备分别计算。
- **备份内容**：包含密码本、密码记录、标签、类型、回收站记录和生成密码的复制历史；主密码、云连接配置和外观偏好不在快照中。
- **还原方式**：还原会覆盖本机密码库与生成历史，不会合并数据；确认前会展示备份时间和数据数量。当前单个备份文件上限为 **16 MiB**。

云端备份由用户手动触发，多设备之间通过备份和还原转移数据。

## 数据与安全

### 本机保护

- **数据库加密**：使用 Room + SQLCipher 整库加密保存密码数据。
- **密钥保护**：使用带随机盐的 PBKDF2-HMAC-SHA256（600,000 次迭代）派生密钥，并结合 AES-GCM 与 Android Keystore 保护本机凭据和数据库密钥。
- **自动锁定**：进入后台 30 秒后锁定应用并关闭密码库会话；连续输入错误会触发重试冷却。
- **剪贴板管理**：可选择 30 秒、1 分钟、5 分钟或不自动清除。应用会在前台具备访问权限时清理自己复制且已到期的内容，后台到期的内容在重新获得焦点时检查。

### 备份文件保护

`.mp` 文件使用 AES-GCM 封装，但当前密钥由应用内固定值派生，**不受个人主密码或独立备份密码保护**。获得文件的人可以通过 MyPass 还原其中的数据，因此请将备份保存在私有目录或私有存储桶中。

云存储配置使用你自己的服务地址和凭据。对象存储要求 HTTPS；WebDAV 还支持局域网 HTTP，连接时会提示确认。

## 技术栈

| 类别 | 技术 |
| --- | --- |
| 开发语言 | Kotlin |
| 界面框架 | Jetpack Compose |
| 界面与主题 | Miuix、Material 3、Lottie |
| 架构与状态 | MVVM、ViewModel、Coroutines / Flow |
| 页面导航 | AndroidX Navigation 3、类型安全路由 |
| 本地存储 | Room、SQLCipher、MMKV |
| 网络与序列化 | OkHttp、Kotlinx Serialization |
| 开源许可展示 | AboutLibraries、随包提供的离线许可资源 |
| 运行平台 | Android 9.0+ / ARM64 |

依赖版本统一维护在 [`gradle/libs.versions.toml`](gradle/libs.versions.toml)。

## 源码构建

### 环境准备

- Android Studio，需支持项目配置的 Android Gradle Plugin 版本。
- Android SDK Platform **37**（当前 `compileSdk` / `targetSdk` 为 37）。
- **JDK 25**（与 [`gradle/gradle-daemon-jvm.properties`](gradle/gradle-daemon-jvm.properties) 的配置一致）。
- Git，以及项目自带的 Gradle Wrapper。

### 获取源码与签名配置

```shell
git clone https://github.com/tingdong105/MyPass.git
cd MyPass
```

使用 Android Studio 打开项目并同步依赖，通过 IDE 或本地 `local.properties` 指定 Android SDK 路径。

**Debug 和 Release 均使用自定义签名配置**，构建 APK 前需要准备自己的密钥：

1. 复制 [`signing.properties.example`](signing.properties.example) 为根目录下的 `signing.properties`。
2. 填写密钥文件路径、密钥库密码、别名和密钥密码，路径相对于项目根目录。
3. 如果没有密钥，可通过 Android Studio 的签名向导创建密钥库，再填写上述配置。

本地签名配置和 `.jks` / `.keystore` 文件已被 Git 忽略，请保留在本机。

### 执行构建

Windows / PowerShell：

```powershell
.\gradlew.bat assembleDebug
```

macOS / Linux：

```shell
chmod +x gradlew
./gradlew assembleDebug
```

Debug APK 输出到 `app/build/outputs/apk/debug/`。构建 Release 使用 `assembleRelease`，输出到 `app/build/outputs/apk/release/`。

## 参与交流

欢迎通过 [Issues](../../issues) 反馈问题、提出建议，或通过 Pull Request 参与完善项目。

反馈问题时，请附上应用版本、设备型号、Android 版本和复现步骤；截图与日志中的账号、密码和云存储密钥请先脱敏。

## 致谢

感谢 Miuix、InstallerX Revived、Lucide，以及 Compose、Room、SQLCipher、MMKV、Lottie、OkHttp、AboutLibraries 等开源项目。项目使用的相关组件、效果和图标来源已记录在 [`app/oss`](app/oss) 与 [`assets/licenses`](app/src/main/assets/licenses) 中。

应用内「设置 → 关于软件 → 开源代码许可」可查看依赖清单和许可说明。

## 开源协议

Copyright © 2026 Tingdong.

仓库根目录许可证为 [MIT](LICENSE)。第三方依赖及引入的源码保留各自的许可声明，包括 InstallerX 相关源码的 GPL-3.0、Miuix 效果源码的 Apache-2.0 和 Lucide 图标的 ISC，具体见对应源码与许可文件。
