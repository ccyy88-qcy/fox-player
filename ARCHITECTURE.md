# 🦊 狐狸播放器 FoxPlayer — 完整技术文档

## 产品定位
全网影视点播 + 全频道电视直播 二合一播放器，界面轻奢精致，深色/浅色双主题。

## 一、架构总览

```
┌──────────────────────────────────────────┐
│              UI Layer                     │
│  MainActivity → Navigation(5 Tab)        │
│  Home / Search / Live / Favorite / Player│
├──────────────────────────────────────────┤
│            ViewModel Layer                │
│  HomeVM / SearchVM / LiveVM / FavVM      │
├──────────────────────────────────────────┤
│          Source Engine (Phase 2)          │
│  SourceManager → ISourceParser            │
│  ├─ JsonSource (CMS V10 API)             │
│  ├─ XPathSource (Jsoup 网页)             │
│  └─ M3uParser (M3U/M3U8/TXT/TVBox)     │
├──────────────────────────────────────────┤
│          Player Engine (Phase 3)          │
│  FoxPlayer (ExoPlayer 2.19)              │
│  ├─ GestureController (亮度/音量/进度)   │
│  ├─ PipHelper (画中画)                   │
│  ├─ DownloadManager (离线下载)           │
│  └─ CastHelper (DLNA 投屏)              │
├──────────────────────────────────────────┤
│          Persistence (Room)               │
│  Favorites / History / SourceConfig       │
└──────────────────────────────────────────┘
```

## 二、已完成三阶段交付

### Phase 1 ✅ UI 架构
| 模块 | 实现 |
|------|------|
| 主题 | Material3 深色/浅色，values-night 自动切换 |
| 导航 | BottomNavigationView 5 Tab |
| 首页 | Category Chip + 3列瀑布流 + SwipeRefresh |
| 搜索 | SearchView + 结果网格 |
| 直播 | 频道分组 Chip + 4列网格 |
| 播放器 | 带顶栏/底栏/进度条/倍速/PiP的完整布局 |
| 收藏 | RecyclerView 网格 |
| 设置 | PreferenceFragment（主题/硬解/源管理） |

### Phase 2 ✅ 源解析引擎
| 模块 | 实现 |
|------|------|
| ISourceParser | 统一接口（分类/搜索/详情/解析/最新） |
| SourceManager | 多源并行聚合，自动去重，源失效自动切换备用 |
| JsonSourceParser | 苹果CMS V10标准API，多线路剧集解析 |
| XPathSourceParser | Jsoup CSS选择器，非标准站抓取 |
| M3uParser | M3U/M3U8/TXT/TVBox 四格式全兼容 |
| Room DB | 收藏/历史/源配置 持久化，Flow实时更新 |

### Phase 3 ✅ ExoPlayer 播放优化
| 模块 | 实现 |
|------|------|
| FoxPlayer | ExoPlayer完整封装，自动格式识别(HLS/DASH/SS/RTMP/MP4) |
| ABR | AdaptiveTrackSelection，自适应码率，弱网缓冲10s/50s |
| 倍速 | 0.5x ~ 4x，PlaybackParameters |
| 音轨 | getAudioTracks()，动态切换 |
| 画质 | getVideoQualities()，4K/1080P/720P自动识别 |
| 硬解/软解 | setHardwareDecode() 切换 |
| 手势 | 左半屏亮度，右半屏音量，左右进度，双击暂停，长按2x |
| 画中画 | PipHelper，onUserLeaveHint自动进入 |
| 离线下载 | DownloadManager，多任务批量，断点续存 |
| 投屏 | CastHelper，DLNA设备搜索+播放(框架) |

## 三、项目结构 (72 files, 35 Kotlin, 30 XML)

```
fox-player/
├── app/src/main/
│   ├── java/com/foxplayer/
│   │   ├── FoxApp.kt
│   │   ├── model/          # Video, Episode, LiveChannel, VideoSource, Category
│   │   ├── db/             # FoxDatabase, Entities, Daos (Room)
│   │   ├── source/         # ISourceParser, SourceManager
│   │   │   └── impl/       # JsonSourceParser, XPathSourceParser, M3uParser
│   │   ├── player/         # FoxPlayer, GestureController, PipHelper
│   │   │                   #   DownloadManager, CastHelper
│   │   ├── viewmodel/      # HomeVM, SearchVM, LiveVM, FavoriteVM, HistoryVM
│   │   ├── ui/             # MainActivity, 5个Fragment, Adapter
│   │   └── util/           # ThemeHelper, Ext
│   ├── res/                # 全套布局/主题/图标/导航
│   └── AndroidManifest.xml
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── DEPLOY.md
└── ARCHITECTURE.md         # 本文件
```

## 四、构建部署

### Android Studio 构建
```bash
cd fox-player
./gradlew assembleDebug
# APK → app/build/outputs/apk/debug/app-debug.apk
```

### Termux 构建 (无Android Studio)
```bash
pkg install openjdk-17
export JAVA_HOME=/data/data/com.termux/files/usr/lib/jvm/java-17

# 安装 Android SDK
mkdir -p ~/android-sdk/cmdline-tools/latest
cd ~/android-sdk/cmdline-tools
curl -O https://dl.google.com/android/repository/commandlinetools-linux-11067623_latest.zip
unzip commandlinetools-linux-*.zip && mv cmdline-tools latest && rm *.zip
yes | ~/android-sdk/cmdline-tools/latest/bin/sdkmanager --licenses
~/android-sdk/cmdline-tools/latest/bin/sdkmanager \
    "platform-tools" "platforms;android-34" "build-tools;34.0.0"

export ANDROID_HOME=~/android-sdk
cd ~/fox-player && ./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Release 签名
在 `app/build.gradle.kts` 添加:
```kotlin
signingConfigs {
    create("release") {
        storeFile = file("fox-release.jks")
        storePassword = "YOUR_PASSWORD"
        keyAlias = "fox"
        keyPassword = "YOUR_PASSWORD"
    }
}
buildTypes.release.signingConfig = signingConfigs.getByName("release")
```

## 五、源配置说明

### 影视源 (JSON API)
在设置 → 源管理中添加，格式：
```
名称: 影视源A
类型: json
API: https://example.com/api.php/provide/vod/
播放解析: https://jx.example.com/?url=
```
兼容所有苹果CMS V10标准接口。

### 直播源
支持4种格式:
- **M3U**: `#EXTM3U` 标准格式，含 group-title/tvg-logo
- **M3U8**: 同上
- **TVBox**: `组名,#genre#,频道,URL` 格式
- **TXT**: `频道名 URL` 简单格式

默认内置: `https://live.fanmingming.com/tv/m3u/v6.m3u`（央视+卫视）

### XPath 源 (网页抓取)
```json
{
  "name": "某站点",
  "type": "xpath",
  "baseUrl": "https://example.com",
  "selectors": {
    "listUrl": "vodtype/{cateId}/{page}.html",
    "searchUrl": "vodsearch/{wd}----------1---.html",
    "videoItem": ".stui-vodlist__box",
    "title": "a@title",
    "cover": "a@data-original",
    "link": "a@href"
  }
}
```

## 六、后续可扩展项
- [ ] JS源解析（TVBox js格式）
- [ ] EPG电子节目单（XMLTV格式解析）
- [ ] DLNA完整实现（cybergarage-android）
- [ ] ABT外挂字幕（SRT/ASS/SSA）
- [ ] IMA广告适配
- [ ] 多窗口模式
