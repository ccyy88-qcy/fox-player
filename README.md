# 🎬 影视聚合APP - Movie Aggregator

基于 Kotlin + Jetpack Compose 的安卓影视聚合播放器。

## 功能特性

- 🏠 **首页推荐** - Banner轮播 + 热门推荐 + 分类推荐
- 🔍 **关键词搜索** - 多源聚合搜索，结果去重合并
- 📂 **影视分类** - 电影/电视剧/动漫/综艺，支持分页加载
- ▶️ **在线播放** - ExoPlayer内核，支持MP4/M3U8/FLV
- 🔄 **多线路切换** - 自动检测失效片源，无缝换源
- 📺 **选集播放** - 电视剧多集选择，自动续播
- ❤️ **收藏管理** - 本地收藏，一键管理
- 📜 **观看历史** - 自动记录播放进度，支持续播
- 🌙 **深色主题** - Material3深色主题，橙色主调

## 技术架构

```
┌─────────────────────────────────────────┐
│              UI Layer (Compose)          │
│  Home | Search | Detail | Player | ...   │
├─────────────────────────────────────────┤
│           ViewModel Layer               │
├─────────────────────────────────────────┤
│         Repository Layer                │
│  MovieRepository (统一数据入口)           │
├──────────────┬──────────────────────────┤
│  SourceManager│   Room Database          │
│  (多源聚合)    │   (本地存储)              │
├──────────────┴──────────────────────────┤
│         MovieSource Interface           │
│  FreeMovieSource | HtmlSource | ...     │
├─────────────────────────────────────────┤
│  OkHttp + JSoup | ExoPlayer | Coil     │
└─────────────────────────────────────────┘
```

## 项目结构

```
app/src/main/java/com/aggregator/movie/
├── MovieApplication.kt          # Application入口
├── data/
│   ├── api/
│   │   ├── MovieSource.kt       # 影视源接口 + SourceManager
│   │   ├── FreeMovieSource.kt   # 免费API源实现（示例）
│   │   └── HttpClientFactory.kt # OkHttp客户端
│   ├── local/
│   │   └── MovieDao.kt          # Room DAO + Database
│   ├── model/
│   │   └── Models.kt            # 数据模型
│   └── repository/
│       └── MovieRepository.kt   # 数据仓库
├── domain/                      # 业务逻辑层（可扩展）
├── service/
│   └── PlaybackService.kt       # 后台播放服务
└── ui/
    ├── MainActivity.kt          # 主Activity
    ├── NavHost.kt               # 导航路由
    ├── theme/                   # 主题配置
    ├── home/                    # 首页
    ├── search/                  # 搜索
    ├── detail/                  # 详情
    ├── player/                  # 播放器
    ├── category/                # 分类
    ├── collection/              # 收藏
    └── history/                 # 历史
```

## 编译打包

### 方式一：GitHub Actions（推荐）

1. 推送代码到GitHub仓库
2. 自动触发 `.github/workflows/build.yml`
3. 在 Actions 页面下载 APK

### 方式二：本地编译

```bash
# 1. 安装 Android SDK + JDK 17
# 2. 设置 ANDROID_HOME 环境变量
export ANDROID_HOME=/path/to/android-sdk

# 3. 编译Debug APK
./gradlew assembleDebug
# 输出: app/build/outputs/apk/debug/app-debug.apk

# 4. 编译Release APK
./gradlew assembleRelease
# 输出: app/build/outputs/apk/release/app-release.apk
```

### 方式三：Termux编译（实验性）

```bash
# 安装依赖
pkg install openjdk-17 gradle

# 编译
cd ~/android-movie-app
gradle assembleDebug
```

## 添加新影视源

只需3步：

```kotlin
// 1. 实现 MovieSource 接口
class MyMovieSource : MovieSource {
    override val sourceId = "my_source"
    override val sourceName = "我的影视源"
    override val baseUrl = "https://api.example.com"
    
    override suspend fun search(keyword: String, page: Int): SearchResult {
        // 实现搜索逻辑
    }
    
    // ... 实现其他方法
}

// 2. 在 MovieApplication 中注册
private fun createSources(): List<MovieSource> = listOf(
    FreeMovieSource(),
    MyMovieSource()  // ← 添加这行
)

// 3. 完成！SourceManager 自动管理多源
```

## 配置说明

### 影视源配置
编辑 `MovieApplication.kt` 中的 `createSources()` 方法：
- 修改 `baseUrl` 为实际影视API地址
- 调整 `priority` 控制源优先级
- 添加多个源实现自动换源

### 网络配置
- `network_security_config.xml` - 允许HTTP明文流量
- `AndroidManifest.xml` - `usesCleartextTraffic=true`

### 主题配置
编辑 `ui/theme/Theme.kt`：
- `OrangePrimary` - 主色调
- `DarkBackground` - 背景色
- `DarkSurface` - 卡片背景色

## 依赖清单

| 库 | 用途 | 版本 |
|---|---|---|
| Jetpack Compose | UI框架 | BOM 2024.02.00 |
| ExoPlayer (Media3) | 视频播放器 | 1.2.1 |
| Room | 本地数据库 | 2.6.1 |
| OkHttp | 网络请求 | 4.12.0 |
| Retrofit | API封装 | 2.9.0 |
| Coil | 图片加载 | 2.5.0 |
| JSoup | HTML解析 | 1.17.2 |
| Gson | JSON解析 | 2.10.1 |
| Navigation Compose | 导航 | 2.7.6 |

## 注意事项

1. **影视源**：默认使用示例API地址，需替换为实际可用的影视接口
2. **直链解析**：支持从HTML/JS中提取视频直链，覆盖大多数影视站
3. **自动换源**：播放失败时自动切换到下一条线路
4. **播放历史**：自动保存播放进度，支持断点续播
5. **权限**：需要 INTERNET 权限，Android 13+ 需要通知权限

## 后续迭代计划

- [ ] 弹幕支持
- [ ] 投屏功能 (DLNA/Cast)
- [ ] 下载缓存
- [ ] 字幕加载
- [ ] 更多影视源适配
- [ ] 用户系统/云同步
- [ ] TV端适配
