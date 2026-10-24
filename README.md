<div align="center">

# WearInstaller

**⌚ Wear OS 应用安装器**

*通过 ADB 直连在手表上安装 APK*

[![🔨 Build](https://github.com/LyonHyrik/WearInstaller/actions/workflows/build.yml/badge.svg)](https://github.com/LyonHyrik/WearInstaller/actions/workflows/build.yml)
[![📱 API](https://img.shields.io/badge/API-25%2B-brightgreen.svg)](https://developer.android.com/about/versions/nougat)

</div>

---

## ✨ 功能

- 📦 选择 APK 文件并安装到 Wear OS 手表
- 🔗 直连 ADB（无需配对模式）
- 🎨 Material 3 Expressive 界面
- ⚙️ 可自定义主题色
- 📱 支持 Android 10+ 手表

## 📋 环境要求

- 🤖 Wear OS 手表（Android 10+）
- 🔧 已开启开发者选项与 ADB 调试

## 📥 安装

1. ⬇️ 从 [Releases](https://github.com/LyonHyrik/WearInstaller/releases) 下载 APK
2. 📦 安装到手表
3. 🔧 在设置中填写手表 ADB 地址

## 🔨 编译

```bash
./gradlew assembleDebug
```

📦 APK 输出在 `app/build/outputs/apk/debug/app-debug.apk`

## 👤 作者

- 🌐 博客：[https://lyonhyrik.github.io/](https://lyonhyrik.github.io/)
- 📱 酷安：[LyonHyrik](https://www.coolapk.com/u/24533526)
- 💻 GitHub：[LyonHyrik](https://github.com/LyonHyrik)

## 📜 License

[GNU Affero General Public License v3.0](LICENSE)