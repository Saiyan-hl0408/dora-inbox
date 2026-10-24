# ParcelHub / 哆啦收件箱 · 源码版

Android 个人包裹聚合工具，把京东、淘宝/菜鸟、拼多多的订单、物流、取件码与身份码入口放在一起。

当前公开快照对应 **v0.9.9-fix1（应用版本 0.9.9 / versionCode 30）**，由 v0.9.7 更新。源码版显示“ParcelHub 源码版”，包名 `com.songoku.parcelhub.source`；它清空个人站点默认值，替换人物/私人素材，与独立下载的原加固 APK 有明确差异。

**源码公开，非商业使用依许可免费；商业使用本项目有权授权的新增表达，须事先取得明确书面许可。** 可免费、收费或协商合作。整体为 source available，非 OSI 意义的开源；上游 MIT 等原权利继续有效。[许可范围](LICENSE_SCOPE.md) · [商业授权](COMMERCIAL-LICENSING.md) · [第三方归属](THIRD_PARTY_NOTICES.md)

## 下载最新版

- [v0.9.9-fix1 Release](https://github.com/Saiyan-hl0408/dora-inbox/releases/tag/v0.9.9-fix1) · **[直接下载加固 APK](https://github.com/Saiyan-hl0408/dora-inbox/releases/download/v0.9.9-fix1/dora-inbox-v0.9.9-fix1.apk)**
- 大小 **8,160,007 字节 / 7.78 MiB**，Android 10+，包名 `com.songoku.parcelhub`，与旧个人加固版签名一致。
- SHA-256：`5adbe8ab23f86aa41f4548e8a71d261b3c585986b0bb3246ffc6a73cc9768260`。
- 同时附校验和、第三方许可资料和版本摘要。下载/升级边界见 [DOWNLOADS.md](DOWNLOADS.md)。既有站点仍提供 v0.9.7，本次仅更新 GitHub。

## 相比 GitHub v0.9.7 更新了什么

- **拼多多身份码**：加入 App 内条码/二维码切换、账号归属检查、服务端有效期与自动更新；京东/菜鸟普通态、放大态和底部操作统一，修复手势/返回后的显示问题。
- **后台定时同步**：与“打开软件时更新”分开设置，默认关闭，支持 15 / 30 / 60 分钟；手动、前台及后台任务协调，减少重复读取。系统调度可能延后。
- **物流读取更可靠**：按当前订单和账号验证详情归属，区分转运与待取，保留同一末端运单下的所有关联商品，排除明确多多买菜/非普通包裹业务，改进详情进度与诊断导出。
- **地点与阅读体验**：从可信取件事件优先提取真实站点名称，修复旧缓存中误存的取件提示句；最新物流状态、签收时间独立保留；返回详情或重建页面后恢复列表阅读位置。

详细阶段说明、修复边界和旧版对照见 [CHANGELOG.md](CHANGELOG.md)。默认历史范围仍为北京时间近 7 个自然日，最多 180 天，扩大范围会按覆盖进度补读。当前没有通知监听导入，也不以付费快递查询/短信为默认数据源。

## 核心 UI 与功能图解

仅展示四类核心 **UI 设计示例**，包含“我的”页；不是运行截图或真实账号数据。品牌与私人素材已中性化，图中的码无法用于取件，不能用配图代替真机验收。[完整功能图解](docs/FEATURES.md)

<table><tr><th>首页：同运单关联多笔订单</th><th>我的：账号与后台同步</th></tr><tr><td><img src="docs/images/home-mockup.png" alt="新版首页同运单多商品设计示例" width="290"></td><td><img src="docs/images/my-mockup.png" alt="新版我的页后台定时同步设计示例" width="290"></td></tr></table>

**物流详情**保留全部关联商品，并显示承运商、运单与物流状态；**身份码**按实际取件服务展示和选择账号。

<img src="docs/images/detail-mockup.png" alt="多商品关联的物流详情设计示例" width="290">

<img src="docs/images/identity-mockup.png" alt="京东菜鸟拼多多身份码新版设计示例，全部为无效演示码" width="1000">

## 从源码构建

Android SDK 36 / build-tools 36.0.0，JDK 17 或 21，Gradle wrapper 8.13，AGP 8.13.2，Kotlin 2.2.20。

```sh
./gradlew --no-daemon :app:assembleDebug :app:testDebugUnitTest
```

Windows 使用 `gradlew.bat`，或通过 `tools/build.ps1` 指定 SDK/JDK。详见 [BUILDING.md](BUILDING.md)。当前快照 Debug 构建与 **417 项合成单元测试**通过；公开源码版使用独立包名、素材和配置，不能逐字节复现原加固 APK。

## 实现与验证边界

[实现位置与官方入口](docs/IMPLEMENTATION.md) · [公开导出差异](docs/EXPORT-CHANGES.md) · [验证记录](docs/VALIDATION.md)

正式版启用京东、淘宝/菜鸟和拼多多。抖音相关实现保留在源码，`ENABLE_DOUYIN=false`，不作为正式支持的平台；隔离实验入口、原始研究与账号数据没有导出。

本轮同步没有登录真实账号、发送验证码、安装手机或触发实际订单同步。最新 fix1 的真机复测和真实站点扫码验收仍待完成；不以单元测试、回放或设计图证明这些流程成功。
