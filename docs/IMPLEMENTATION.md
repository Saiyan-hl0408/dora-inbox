# 核心功能实现与网址入口

下列路径均从仓库根目录开始，核心包前缀是 `app/src/main/java/com/halo/expressassistant/`。链接来自实现中的常量/路由；仅列入口，不在整理过程中登录实际账号或测试验证码。

| 功能 | 实现 | 行为/边界 |
| --- | --- | --- |
| 主界面与数据聚合 | `hub/HubActivity.kt`、`hub/HubData.kt`、`data/Store.kt` | 汇总平台记录及本地记录，按运单合并；本地补充不等于平台新增包裹 |
| 绑定与会话 | `ui/PlatformLoginActivity.kt`、`ui/TbLoginActivity.kt`、`ui/PddLoginActivity.kt`、`ui/PddSessionVerifier.kt` | 官方 WebView 登录/扫码，返回后核验会话；不保证拉起原 App 就表示绑定成功 |
| 会话保存 | `hub/SessionCookies.kt`、`hub/Secrets.kt` | 会话字段使用 Android Keystore AES/GCM 工具处理；源码不含运行时生成的密钥。上游其他设置/缓存另有存储机制，不宣称所有数据都已加密 |
| 同步与历史 | `ui/SyncEngine.kt`、`hub/SyncWindow.kt`、`hub/SyncLedger.kt`、`hub/HistoryRangePolicy.kt` | 默认 7、最多 180 自然日；记录范围覆盖与补读；“未返回”不自动当作全量完成或历史不存在 |
| 物流与商品 | `hub/ParcelDetailActivity.kt`、`hub/JdLogisticsParser.kt`、`ui/JdlWebFetcher.kt`、`ui/TbWebOrders.kt`、`hub/PddOrderPayload.kt` | 解析本人可见网页/响应，保留状态和事件时间；商品图片按记录链接加载，无附带真实商品截图 |
| 取件信息 | `hub/CainiaoPickup.kt`、`hub/CainiaoPickupService.kt`、`hub/PddPickupCodes.kt`、`hub/PickupUiPolicy.kt` | 区分购物来源、取件服务和当前/历史取件码；验证码、手机号及历史码不能充作当前有效取件码。菜鸟结果是补充，不是购物订单清单 |
| 身份码 | `hub/IdentityLauncher.kt`、`hub/IdentityRoutes.kt` | 按服务选择官方入口和已绑定账号；包含上游路由表达，保留 MIT 归属 |
| 本地导入 | `hub/PickupText.kt`、`hub/ImportPolicy.kt`、`hub/HubActivity.kt` | 手动/分享文本解析；当前 manifest 不含通知监听导入服务 |

## 官方入口

| 平台/用途 | 网址 | 对应源码 |
| --- | --- | --- |
| 京东物流 | [京东快递网页](https://kd.jd.com/) | `ui/JdlWebFetcher.kt`、`hub/JdLogisticsSource.kt` |
| 京东关联收件号码 | [官方账号关联页](https://kd.jd.com/express/family/account?source=wlgwj) | `hub/HubActivity.kt` |
| 淘宝绑定 | [淘宝移动登录](https://login.m.taobao.com/login.htm) | `ui/TbLoginActivity.kt`、`ui/PlatformLoginActivity.kt` |
| 拼多多绑定 | [拼多多移动登录](https://mobile.yangkeduo.com/login.html) | `ui/PddLoginActivity.kt` |
| 拼多多订单 | [移动订单页](https://mobile.yangkeduo.com/orders.html) | `ui/PddLoginActivity.kt`、`ui/PddListFetcher.kt` |
| 京东身份码 | [京东身份码](https://convenient-h5.jd.com/identityCode?channel=xueshengjirukou) | `hub/IdentityRoutes.kt` |
| 淘宝/菜鸟身份码 | [菜鸟频道身份码](https://pages-fast.m.taobao.com/wow/z/uniapp/1011717/last-mile-fe/end-collect-platform/identity-code?x-ssr=true) | 同上 |
| 拼多多身份码 | [拼多多取件频道](https://m.pinduoduo.net/mdkd/package?entry_source=18&extra_params=from_wx_jump%3D1&p_channel=0) | 同上 |
| 菜鸟身份码 | [菜鸟官方身份码入口](https://market.m.taobao.com/app/cn-yz/multi-activity/authCode.html?bizEntry=ALIPAY_GUOGUO) | 同上 |
| 菜鸟站点取件查询 | [官方频道基础页](https://pages-fast.m.taobao.com/wow/z/uniapp/1011717/last-mile-fe/end-collect-platform/school-search-ssr?openFrom=searchEnter&from=XY) | `hub/CainiaoPickup.kt`；公开版不附个人站点编号，需用户自配 |

页面可以要求登录、风控、人机验证、手机号确认、客户端跳转或特定站点权限；本次源码整理没有自动提交这些操作。所有平台名称仅说明兼容对象，不表示其为本项目提供官方背书或商业授权。
