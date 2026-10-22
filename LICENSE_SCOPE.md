# 许可范围与来源边界

本仓库不是全体代码统一 MIT，也不声称全体代码完全原创。顶层 [LICENSE](LICENSE) 是混合许可的入口；[自定义非商业许可](LICENSES/LicenseRef-ParcelHub-Noncommercial-1.0.txt)仅适用于 Saiyan-hl0408 拥有或有权按该许可授权的**可识别项目新增表达**。第三方原有表达、公共领域内容、不受版权保护的思想/方法/接口事实及依法无需许可的行为不受该商业条件限制。

## 文件与表达如何对应

机器清单 [docs/provenance-inventory.json](docs/provenance-inventory.json)记录此快照 `app/src/main` 和 `app/src/test` 的每个文件 SHA-256、与三个上游工作树的比较、上游提交号和跨路径相似性。它是审查索引，不是所有权证明。判断分为：

| 分类 | 范围 | 适用规则 |
| --- | --- | --- |
| `upstream-identical` | 和已知上游文件逐字节相同 | 继续原许可，无本项目新增商业限制 |
| `upstream-derived-with-project-changes` | 与 ExpressAssistant 同路径有差异 | 上游表达 MIT；可识别的自有新增表达才适用非商业许可 |
| `cross-path-upstream-derived-with-project-changes` | 跨路径提取/改写的已识别上游表达 | 同样拆分，不能因文件新增而把第三方代码转为受限专有贡献 |
| `project-specific-candidate-not-proof-of-exclusive-authorship` | 未发现相同路径，或本次导出的新占位资源/代码等 | 仅就许可人实际拥有或可授权的新增表达提供非商业许可；已知或后续确认的第三方片段仍优先适用第三方原许可 |

项目新增功能主要集中在 `hub/` 中的聚合 UI、同步覆盖、历史策略、取件信息处理和测试，以及 `ui/` 中新增的绑定/会话核验与网页采集协调。这一说明不把这些目录整体确认为独占原创。已识别跨路径来源特别包括：

- `hub/IdentityRoutes.kt` 的路由/入口来源涉及 Pickup Code Launcher 的 `Destination.java` 及 Parcel 的 `util/ThirdPartyEntry.kt`：相关原表达继续 MIT。
- `hub/JdLogisticsParser.kt` 中从 ExpressAssistant 的 `ui/JdTrackFetcher.kt` 提取/改写的解析表达：原表达继续 MIT；只对可识别的本项目新增表达适用自定义条款。
- 短小通用语法、常见协程/JSON/WebView 样式和相同官方 URL 的 token 相似性不自动证明抄袭或权属；相似性记录保留供进一步核对。

来源比对基准均在机器清单 `upstream_commits` 中固定提交号：

- [ExpressAssistant](https://github.com/Halo0sama/ExpressAssistant) — Copyright 2026 Halo0sama，MIT。
- [Parcel](https://github.com/shareven/parcel) — Copyright 2025 shareven，MIT。
- [Pickup Code Launcher](https://github.com/EidenLiu/pickup-code-launcher) — Copyright 2026 EyanLiu，MIT。

MIT 原文完整保存在 `LICENSES/MIT-*.txt`。原源码中已有的版权、许可头继续保留。上游 README 中若有“禁止商用”等与 MIT 文本不一致的宣传，不作为撤回上游 MIT 商业权利的依据。

## 非应用源码

- `rice_paper.png`：openhanako 的 Apache-2.0 素材，哈希/固定提交见第三方声明；不属于本项目受限原创素材。
- `gradle/wrapper/gradle-wrapper.jar`：官方 Gradle 8.13 工具，附其 LICENSE/NOTICE；原 wrapper 脚本保留版权头。项目非商业条件不限制其独立使用。
- 原有 Gradle 配置/脚本中的上游表达仍依原许可；新增构建脚本 `tools/build.ps1`、本次新写说明文档和中性向量占位资源中本项目有权授权的表达，适用项目非商业许可。文档引用的第三方文字继续原权利。
- Maven/Android 依赖各依原许可。仓库不再分发 SDK、缓存或已编译 APK；构建会由使用者下载依赖。未来分发二进制须对实际传递依赖及内嵌通知作相应审查。
- `docs/images` 仅收录四类选定设计示例；不包含原人物资产、私人头像或真实账号信息。新增设计表达只在实际有权授权的范围内适用项目许可，第三方平台名称与相关权利不纳入。

用户另要求提供既有约 7MB 加固包，故在 Git 源码之外通过 Release 附件分发原 APK，并附独立校验和、第三方许可资料。已补查源码解析的 96 个 runtime 组件许可元数据和可见内嵌通知，但不声称此元数据证明旧 APK 内每一段代码的全部权属。

没有随源码分发人物图片、人物字标、作者头像、群号、收款二维码、原截图和账号数据。平台名称/链接用于说明兼容性，不授权商标或平台服务。

## 权利证据和更正

本次以当前工作树、三个本地上游树、文件哈希、跨路径 token 比对及已知开发交接为来源依据，没有作司法意义的独立权属鉴定或 clean-room 认证，也未凭“AI 可实现”推断独占权利。未发现匹配不能证明不存在其他来源。发生明确来源争议时，应按原许可证核实、更正、排除相关内容或取得授权，不能以本仓库声明覆盖第三方原权利。

商业合同应明确所需模块和对应的自有新增表达，不承诺把混合仓库的全部代码独占或整体版权转让。外部贡献尚需另行确认商业再许可权限。
