# 核心实现与官方入口

对应 v0.9.9-fix1。代码前缀：`app/src/main/java/com/halo/expressassistant/`。以下是源码实现范围，不表示本轮重新登录账号或验证平台现网。

| 功能 | 实现位置 | 主要边界 |
| --- | --- | --- |
| 聚合、商品关联及列表位置 | `hub/HubActivity.kt`、`hub/HubData.kt`、`hub/ListReadingPosition.kt`、`data/Express.kt`、`data/Store.kt` | 包裹维度展示并保留关联商品；按范围/筛选/搜索恢复锚点与偏移 |
| 绑定、会话与核验 | `ui/PlatformLoginActivity.kt`、`ui/TbLoginActivity.kt`、`ui/PddLoginActivity.kt`、`ui/PddSessionVerifier.kt`、`hub/SessionCookies.kt`、`hub/Secrets.kt` | 官方网页会话和账号归属核验；Keystore 是运行时工具，源码不带实际密钥；不宣称所有缓存都已加密 |
| 同步、范围与互斥 | `ui/SyncEngine.kt`、`hub/SyncWindow.kt`、`hub/SyncLedger.kt`、`hub/SyncRunCoordinator.kt` | 7/180 自然日和覆盖证明，未知/失败不当作完整空列表；手动/前台/后台任务协调 |
| 后台定时设置 | `BackgroundSyncScheduler.kt`、`TrackingWorker.kt`、`hub/BackgroundSyncSettings.kt`、`hub/BackgroundSyncRunner.kt`、`hub/BackgroundSyncPolicy.kt` | 开关默认关，间隔15/30/60，WorkManager系统调度；不能承诺精准执行时间 |
| 拼多多物流与商品 | `ui/PddTraceFetcher.kt`、`hub/PddDetailPayload.kt`、`hub/PddOrderPayload.kt`、`hub/ParcelMetadata.kt` | 每单验证URL/页面token/归属，拒绝外单结果；末端转单保留关联商品和原运单；状态、站点、签收时间独立 |
| 拼多多诊断 | `hub/PddDiagnostics.kt`、`hub/PddDiagnosticPayload.kt`、`hub/PddDiagnosticBusiness.kt`、`hub/PddDiagnosticSummary.kt` | 摘要、TXT分享和用户选择保存；不自动公开诊断；FileProvider仅授权对应诊断目录 |
| 取件服务 | `hub/CainiaoPickup.kt`、`hub/CainiaoPickupService.kt`、`hub/PddPickupCodes.kt`、`hub/PickupUiPolicy.kt` | 区分商城来源和发码服务；菜鸟结果是补充；没有个人默认站点，不伪造取件码 |
| 身份码 | `hub/IdentityLauncher.kt`、`hub/IdentityPageShell.kt`、`hub/IdentityCodeActivity.kt`、`hub/TaobaoIdentityActivity.kt`、`hub/PddIdentityActivity.kt`、`hub/PddIdentityPayload.kt` | 京东/菜鸟官方WebView与PDD内存码展示；到期更新、账号检查、普通/放大态；渲染使用独立MIT库 |
| 本地文本导入 | `hub/PickupText.kt`、`hub/ImportPolicy.kt` | 手动/分享文本；手机号、验证码、无效码不作为当前取件码；无通知监听导入服务 |

## 官网入口

| 入口 | 网址 | 代码 |
| --- | --- | --- |
| 京东物流 | [京东快递](https://kd.jd.com/) | `ui/JdlWebFetcher.kt`、`hub/JdLogisticsSource.kt` |
| 京东关联收件号码 | [账号关联](https://kd.jd.com/express/family/account?source=wlgwj) | `hub/HubActivity.kt` |
| 淘宝绑定 | [移动登录](https://login.m.taobao.com/login.htm) | `ui/TbLoginActivity.kt` |
| 拼多多绑定/订单 | [移动登录](https://mobile.yangkeduo.com/login.html) / [订单页](https://mobile.yangkeduo.com/orders.html) | `ui/PddLoginActivity.kt`、`ui/PddListFetcher.kt` |
| 京东身份码 | [官方入口](https://convenient-h5.jd.com/identityCode?channel=xueshengjirukou) | `hub/IdentityRoutes.kt` |
| 淘宝/菜鸟身份码 | [频道身份码](https://pages-fast.m.taobao.com/wow/z/uniapp/1011717/last-mile-fe/end-collect-platform/identity-code?x-ssr=true) | 同上 |
| 拼多多官方取件备用入口 | [取件频道](https://m.pinduoduo.net/mdkd/package?entry_source=18&extra_params=from_wx_jump%3D1&p_channel=0) | 同上；App内身份码不等同该网页截图 |
| 菜鸟官方身份码 | [身份码入口](https://market.m.taobao.com/app/cn-yz/multi-activity/authCode.html?bizEntry=ALIPAY_GUOGUO) | 同上 |
| 菜鸟站点查询基础页 | [官方频道](https://pages-fast.m.taobao.com/wow/z/uniapp/1011717/last-mile-fe/end-collect-platform/school-search-ssr?openFrom=searchEnter&from=XY) | `hub/CainiaoPickup.kt`，需用户自配站点 |

页面/接口可能要求登录、人机或手机号验证、特定站点权限和原 App。本轮整理不执行这些操作。平台名称仅描述兼容对象，不表示平台背书、商业接口或数据授权。抖音实现保留在源码但正式构建 `ENABLE_DOUYIN=false`，隔离实验入口与账号研究数据不公开。
