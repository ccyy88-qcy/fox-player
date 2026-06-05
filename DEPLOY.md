# 🦊 狐狸播放器 FoxPlayer — 部署文档

## 项目概览

| 项目 | 说明 |
|------|------|
| 定位 | 全网影视点播 + 电视直播 二合一播放器 |
| 内核 | ExoPlayer 2.19.1 |
| 语言 | Kotlin |
| 架构 | MVVM + Navigation + Room |
| 最低SDK | 24 (Android 7.0) |
| 目标SDK | 34 (Android 14) |

## 三阶段开发计划

### Phase 1 ✅ UI 架构（已完成）
- 项目骨架 + Gradle 依赖
- Material Design 3 深色/浅色双主题
- Navigation 5 Tab 底部导航（首页/搜索/直播/收藏/设置）
- 首页分类 Chip + 瀑布流网格 (3列)
- 搜索页 SearchView + 结果网格
- 直播频道列表 + 分组 Chip
- 播放器占位界面（ExoPlayer 在 Phase 3 集成）
- 收藏页 + 设置页（主题切换/硬解开关/源管理）

### Phase 2 🔜 源解析
- VideoSource 抽象接口
- JSON 源解析（影视大全/CMS 模板）
- XPath 源解析（Jsoup 网页抓取）
- M3U/TXT 直播源导入
- EPG 电子节目单
- 源管理 CRUD（添加/排序/启禁）

### Phase 3 🔜 播放优化
- ExoPlayer 完整集成（HLS/DASH/RTMP/本地）
- 硬解/软解切换
- 画中画 (PiP)
- 手势控制（亮度/音量/进度/倍速）
- 预加载 + 缓存
- ABT 字幕支持

## 项目结构

```
fox-player/
├── app/src/main/
│   ├── java/com/foxplayer/
│   │   ├── FoxApp.kt                    # Application
│   │   ├── model/                       # 数据模型
│   │   │   ├── Video.kt                 # 影视条目
│   │   │   ├── Episode.kt              # 剧集
│   │   │   ├── LiveChannel.kt          # 直播频道
│   │   │   ├── VideoSource.kt          # 源配置
│   │   │   └── Category.kt             # 分类枚举
│   │   ├── viewmodel/                   # ViewModel
│   │   │   ├── HomeViewModel.kt
│   │   │   ├── SearchViewModel.kt
│   │   │   └── LiveViewModel.kt
│   │   ├── ui/                          # UI 层
│   │   │   ├── MainActivity.kt          # 主 Activity
│   │   │   ├── home/                    # 首页
│   │   │   ├── search/                  # 搜索
│   │   │   ├── live/                    # 直播
│   │   │   ├── favorite/                # 收藏
│   │   │   ├── settings/                # 设置
│   │   │   └── player/                  # 播放器
│   │   ├── source/                      # (Phase 2) 源解析
│   │   ├── player/                      # (Phase 3) ExoPlayer 封装
│   │   └── util/                        # 工具类
│   │       ├── ThemeHelper.kt
│   │       └── Ext.kt
│   ├── res/                             # 资源文件
│   │   ├── layout/                      # 布局
│   │   ├── navigation/                  # 导航图
│   │   ├── drawable/                    # 图标
│   │   ├── values/                      # 主题/颜色/字符串
│   │   └── values-night/               # 深色主题
│   └── AndroidManifest.xml
├── build.gradle.kts
├── settings.gradle.kts
└── DEPLOY.md                            # 本文件
```

## 构建步骤

### 环境要求
- Android Studio Hedgehog (2023.1.1) 或更高
- JDK 17
- Android SDK 34
- Gradle 8.5（项目自带 wrapper）

### 构建
```bash
# 1. 打开项目
cd fox-player
# 用 Android Studio 打开，或命令行构建：

# 2. Debug 构建
./gradlew assembleDebug

# 3. Release 构建（需要签名配置）
./gradlew assembleRelease

# 4. 输出位置
# APK: app/build/outputs/apk/debug/app-debug.apk
# APK: app/build/outputs/apk/release/app-release.apk
```

### 签名配置（Release）
在 `app/build.gradle.kts` 的 `android.signingConfigs` 添加：
```kotlin
signingConfigs {
    create("release") {
        storeFile = file("fox-release.jks")
        storePassword = "YOUR_STORE_PASSWORD"
        keyAlias = "fox"
        keyPassword = "YOUR_KEY_PASSWORD"
    }
}
buildTypes.release.signingConfig = signingConfigs.getByName("release")
```

## 在 Termux 上构建（无需 Android Studio）

```bash
# 安装 JDK 17
pkg install openjdk-17

# 设置 JAVA_HOME
export JAVA_HOME=/data/data/com.termux/files/usr/lib/jvm/java-17

# 安装 Android SDK cmdline-tools
mkdir -p ~/android-sdk/cmdline-tools
cd ~/android-sdk/cmdline-tools
curl -O https://dl.google.com/android/repository/commandlinetools-linux-11067623_latest.zip
unzip commandlinetools-linux-*.zip
mv cmdline-tools latest
rm commandlinetools-linux-*.zip

# 接受许可
yes | ~/android-sdk/cmdline-tools/latest/bin/sdkmanager --licenses

# 安装必要组件
~/android-sdk/cmdline-tools/latest/bin/sdkmanager \
    "platform-tools" "platforms;android-34" "build-tools;34.0.0"

export ANDROID_HOME=~/android-sdk
export PATH=$PATH:$ANDROID_HOME/platform-tools

# 构建
cd ~/fox-player
chmod +x gradlew
./gradlew assembleDebug
```

## 安装到设备
```bash
# USB 连接手机
adb install -r app/build/outputs/apk/debug/app-debug.apk

# 或无线调试
adb connect <手机IP>:5555
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
