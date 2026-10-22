# 第三方归属与许可

本项目建立在上游基础之上。源码可见、UI 没有显示致谢或当前文件路径不同，都不消除第三方版权和许可条件。第三方原许可及其商业使用权继续有效；本项目的非商业许可仅覆盖有权授权的新增表达，详见 [LICENSE_SCOPE.md](LICENSE_SCOPE.md)。

## 应用来源

| 上游 | 权利声明 | 完整许可 |
| --- | --- | --- |
| [Halo0sama/ExpressAssistant](https://github.com/Halo0sama/ExpressAssistant) | Copyright (c) 2026 Halo0sama | [MIT](LICENSES/MIT-ExpressAssistant.txt) |
| [shareven/parcel](https://github.com/shareven/parcel) | Copyright (c) 2025 shareven | [MIT](LICENSES/MIT-Parcel.txt) |
| [EidenLiu/pickup-code-launcher](https://github.com/EidenLiu/pickup-code-launcher) | Copyright (c) 2026 EyanLiu | [MIT](LICENSES/MIT-Pickup-Code-Launcher.txt) |

逐文件来源、基准提交与跨路径片段见 [provenance-inventory.json](docs/provenance-inventory.json)。本快照含有修改；不能宣称与上游原件完全相同，也不能把本项目许可证套到未修改的 MIT 原代码上。

## 纸纹素材

`app/src/main/res/drawable-nodpi/rice_paper.png` 来自 [openhanako 固定提交的 rice-paper.png](https://github.com/liliMozi/openhanako/blob/9a6cabf8c513db5edc6cf8179a538c9075885c6e/desktop/src/assets/textures/rice-paper.png)，原图未修改，大小 131654 字节，SHA-256：`a09b093b715fc9e8fd0f91ac02fcdc68636e825fa9555820ef8d6b8b7889d64b`。适用上游 [Apache-2.0](https://github.com/liliMozi/openhanako/blob/9a6cabf8c513db5edc6cf8179a538c9075885c6e/LICENSE)，全文附于 [LICENSES/Apache-2.0.txt](LICENSES/Apache-2.0.txt)。核对的完整上游树没有 NOTICE 文件，未发现该素材单列其他许可；这不是对上游权利链的独立保证。

## 直接依赖

| 依赖组 | 本快照声明版本 | 许可 |
| --- | --- | --- |
| AndroidX core-ktx、webkit、appcompat、activity、constraintlayout、recyclerview、swiperefreshlayout、lifecycle runtime/viewmodel/livedata、work-runtime-ktx | 以 `app/build.gradle` 为准（21 项直接依赖清单逐项列版本） | Apache-2.0 |
| Google Material | 1.14.0 | Apache-2.0 |
| kotlinx serialization-json / coroutines-android | 1.5.0 / 1.7.3 | Apache-2.0 |
| OkHttp / Coil / Markwon core | 4.11.0 / 2.6.0 / 4.6.2 | Apache-2.0 |
| NanoHTTPD | 2.3.1 | [BSD-3-Clause](LICENSES/BSD-3-Clause-NanoHTTPD.txt) |
| Shizuku API / provider | 13.1.5 | [MIT](LICENSES/MIT-Shizuku-API.txt) |
| JUnit（仅 testImplementation） | 4.13.2 | [EPL-1.0](LICENSES/EPL-1.0-JUnit.txt) |

直接依赖的发布 POM、许可链接和 SHA-256 证据见 [dependencies-license-evidence.json](docs/research/dependencies-license-evidence.json)。AndroidX 的传递依赖可能使最终解析版本不同于直接声明；本表不冒充完整二进制 SBOM。上述依赖通过构建系统下载，不作为本项目原创源代码发布；之后分发 APK 仍须审查完整实际依赖与嵌入 LICENSE/NOTICE。

为用户指定的旧加固 APK 下载附件，本轮另解析了源码快照的 `releaseRuntimeClasspath`，收集 **96 个组件 / 89 个工件** 的许可元数据、工件哈希和 **14 项嵌入许可/NOTICE 记录**，无缺失 POM 许可项。见 [runtime-dependencies-license-evidence.json](docs/research/runtime-dependencies-license-evidence.json) 及 [LICENSES/runtime](LICENSES/runtime)。原加固工程与本快照的直接依赖声明一致；解析版本可能高于直接声明，例如 OkHttp 实际解析 4.12.0。这里不声称已对旧 APK 逐类反编译确定最终工件组成。

新增重要传递归属：`com.atlassian.commonmark:commonmark:0.13.0` 为 BSD-2-Clause（完整原文位于 LICENSES/runtime 的对应文件）；Shizuku `aidl`/`shared` 与 API/provider 同属 SDK MIT。其余解析组件的官方 POM、具体版本及许可记录均在上述机器清单，不把它们当作本项目自有版权。提取出的嵌入 NOTICE（包括 OkHttp）随源码与 Release 声明资料保留。

`docs/images` 的四类设计示例不是第三方官方产品的真实截图：三个来自已验收的可编辑本地设计组件，中性化人物和商品素材后渲染；身份码板来自已确认的无人物素材原设计。平台名称用于兼容性说明，码和账号皆为示意，不授予商标/平台权利，具体记录见 docs/images/manifest.json。

Shizuku **SDK** 的 MIT 与 Shizuku **管理 App** 的 Apache-2.0 及品牌说明需区分。本项目不使用 Shizuku 的启动图标作为自己图标、不把 Shizuku 作为应用名、不冒用其管理 App ID/manager 权限。正常 SDK 的 provider/接口引用依其许可集成。

## 构建工具

Gradle wrapper 使用官方 8.13 JAR（哈希见 BUILDING.md），保留原启动脚本版权头，附完整 [Gradle LICENSE](LICENSES/Gradle-8.13-LICENSE.txt) 和 [NOTICE](LICENSES/Gradle-8.13-NOTICE.txt)。Android Gradle Plugin、Kotlin、JDK、Android SDK 等由使用者准备/下载，仓库没有打包工具发行版或缓存，不授予第三方工具的额外权利。

京东、淘宝、菜鸟、拼多多及其他名称/网址仅描述兼容对象。源码许可不授予平台服务、数据、账号、商标、人物角色或接口商业合作权。
