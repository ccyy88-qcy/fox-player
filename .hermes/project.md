# 影视聚合APP - Hermes集成文档

## 项目位置
`~/android-movie-app/`

## 快速编译
```bash
cd ~/android-movie-app
./gradlew assembleDebug    # Debug APK
./gradlew assembleRelease  # Release APK
```

## APK输出路径
- Debug: `app/build/outputs/apk/debug/app-debug.apk`
- Release: `app/build/outputs/apk/release/app-release.apk`

## GitHub仓库
推送后即可通过 GitHub Actions 自动编译：
1. `git init && git remote add origin <your-repo>`
2. `git add . && git push -u origin main`
3. 在 Actions 页面下载 APK

## 影视源配置
编辑 `app/src/main/java/com/aggregator/movie/MovieApplication.kt` 中的 `createSources()` 方法。

默认源为示例占位，需要替换为实际可用的影视API：
- 修改 `baseUrl` 
- 调整解析逻辑（FreeMovieSource.kt）
- 添加更多源实现自动换源

## 迭代管理
- 功能迭代：修改对应Screen文件
- 新增影视源：实现MovieSource接口 + 注册到MovieApplication
- UI调整：修改Theme.kt和对应Screen
- 数据层：修改Repository和Dao

## 开发守则
1. 所有UI字符串使用strings.xml
2. 网络请求统一走Repository
3. 新增影视源遵循接口隔离原则
4. 播放器错误自动换源，不弹错
5. 深色主题保持一致性
