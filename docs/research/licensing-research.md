# 源码公开与商业另行授权：一手依据核查

核查日期：2026-10-22（Asia/Shanghai）。范围：0.9.7 / versionCode 27 的公开快照许可方案、`app/build.gradle` 的 21 项直接依赖、Gradle wrapper 与纸纹素材。本文记录依据和实施建议，不作法院认定、权属或跨法域效力保证；最终实际范围以仓库许可、来源清单和单独签署的合同为准。

## 推荐方案及边界

采用“第三方保留原许可 + 许可人有权授权的新增表达使用自定义有限非商业许可 + 商业用途事先书面另行许可”。可以免费许可、收费许可或另谈合作；无需为了这一目标重写所有 MIT 基础代码。

1. 只把能够合理核定权利且可识别的新增代码、改动中的新增表达及文档列为本项目受限贡献。**新文件、新路径、不同哈希、AI 生成或没有同路径上游文件均不等于权属证明。**混合文件中，上游表达与项目新增表达应分别说明；不明来源部分必须继续查明、排除或明确替换，不能用全仓默认版权声明消除不确定性。《计算机软件保护条例》第四、六、九至十三条涉及独立开发、表达范围及开发/合作/委托/职务权属，是这些谨慎边界的法律依据。[条例官方文本](https://www.cac.gov.cn/2013-02/08/c_12648744.htm)
2. 对已知第三方代码、资源、Gradle 文件及依赖保留其原有许可和版权声明。项目限制不限制直接依据第三方许可获得的权利。ExpressAssistant、Parcel、Pickup Code Launcher 的官方 LICENSE 均为 MIT；不得通过顶层“禁止商用”撤回这些作者已授予的商业权利，也不能声称所有代码完全原创。[ExpressAssistant LICENSE](https://raw.githubusercontent.com/Halo0sama/ExpressAssistant/main/LICENSE)、[Parcel LICENSE](https://raw.githubusercontent.com/shareven/parcel/main/LICENSE)、[Pickup Code Launcher LICENSE](https://raw.githubusercontent.com/EidenLiu/pickup-code-launcher/main/LICENSE)
3. MIT 允许使用、修改、再分发、再许可及销售，但要求保留版权和许可声明。新增自有表达可另设许可；直接使用不含这些自有表达的原 MIT 部分，不能据此要求向本项目许可人申请商业许可。这是从 MIT 授权范围和版权边界作出的项目实施判断。[OSI MIT 原文](https://opensource.org/license/mit)
4. 商业使用须书面许可的整体模式应称“源码公开”或“源码可用”（source available）。OSI 开源定义第 6 项不允许限制商业领域，第 1 项不允许限制相应自由再分发；因而不宜给整个混合快照贴 OSI 开源许可标签。[OSI Open Source Definition](https://opensource.org/osd)
5. 许可表达权利边界，不是技术防复制工具。使用者依法享有的例外或限制应明确保留。是否存在可主张的权利及能否救济仍取决于表达、来源、证据和适用法律。[软件保护条例第四、六、十六、十七、二十四条](https://www.cac.gov.cn/2013-02/08/c_12648744.htm)

## 为什么不直接采用 PolyForm Noncommercial 1.0.0

官方有效地址为无末尾斜杠版本：<https://polyformproject.org/licenses/noncommercial/1.0.0>；带末尾 `/` 的地址本轮返回 404，不代表该许可不存在。

该许可允许非商业使用、相应修改及再分发，且特别允许慈善、教育、公共研究、公共安全/健康、环保与政府组织使用，不受其资金来源或资金义务影响。因此它有意给这些主体较宽的许可，无法精确表达“任何主体进行商业用途均须事先书面许可”的严格目标。官方采用指南也明确其非商业定义较宽，并提示面向教育、研究机构收费的供应商并不适合此方案。**推荐不直接选用该标准许可，也不修改其文字后仍冒用同名/同版本。**[PolyForm Noncommercial 1.0.0 原文](https://polyformproject.org/licenses/noncommercial/1.0.0)、[PolyForm 官方采用说明](https://polyformproject.org/why-adopt-polyform)

## 自定义许可应写清楚的具体内容

以下是与用户已表达目标一致的起草建议，不是已经取得全部权利的结论：

- **范围**：以来源清单和明确声明识别许可人可授权的贡献；第三方原代码、素材、商标、依赖及平台服务均排除。混合文件中不得把原第三方表达全部归为本项目专有贡献。
- **免费授予**：个人非商业学习、研究、私用、修改及非商业再分发源代码/构建产物；必须保留来源、许可与修改说明，并向接收者提供相同受限贡献的许可文本。下游针对自己的新增表达可保留权利，但无权替原贡献扩大商业许可。
- **商业用途**：为了直接或间接商业利益进行使用、复制、修改、分发、集成或提供服务，包括销售/收费下载、广告或订阅获利、付费托管/SaaS、企业内部经营和客户交付；即使暂未收费也可能是商业用途。按活动实际目的判断，不因机构名称为学校、公益或政府就自动豁免；个人员工在工作以外的私人非商业使用不因雇主性质自动成为商用。
- **另行书面许可**：由可核验的权利人明确确认主体、代码/版本或提交号、用途、范围、期限、地域、费用、再分发/再许可、衍生作品和终止条件；无回复、口头闲聊、Issue 留言申请、公开可下载或取得源代码均不自动构成商业许可。公开 Issue 只作申请入口，签署资料中的商业或个人信息可改走双方认可的私密书面渠道。
- **不默示转让**：默认保留版权，不默认授予独占、专利、商标、平台接口或第三方权利。已生效授权与后续许可证版本的关系要清楚，不写“许可人可随时撤回一切已授权权利”。无担保和责任限制只在法律允许范围内适用。
- **贡献入口**：在接受外部代码前，明确贡献者的权利、许可证和是否授予维护者另行商业许可的权限。仅仅收到 PR 不等于著作权转让，也不等于维护者有权单独商业再许可。

上述起草策略以第三方许可的明示授权、[著作权法第二十六、二十七、二十九条](https://www.ncac.gov.cn/xxfb/flfg/flfg_532/202103/t20210309_50530.html)及[软件保护条例第十八至二十条](https://www.cac.gov.cn/2013-02/08/c_12648744.htm)所体现的许可/转让区分为依据；自定义商业定义的具体效力仍需结合实际交易判断。

## 商业许可、专有许可和版权转让

| 形式 | 实际谈什么 | 本项目可承诺的边界 |
| --- | --- | --- |
| 普通非专有商业许可 | 允许特定主体在合同范围商用，可免费或收费；其他人仍可另外获许可 | 建议默认，许可人保留所有权及继续授权权 |
| 专有/独占商业许可 | 对约定权利、用途、时间、地域的排他程度；许可人自身能否使用需明确约定 | 须另订明确书面合同，不能覆盖上游第三方已授予的权利 |
| 著作财产权转让 | 将合同明确列出的可转让权利交给对方，而非仅允许使用 | 不能把“买断”三个字直接解释为全部版权转让，不能转让他人的版权 |

著作权法第二十六条要求许可合同区分专有与非专有及范围等，第二十七条要求财产权转让书面合同，第二十九条保留未明确授予的权利；软件保护条例第十九、二十条亦分别要求专有许可和转让书面合同。此处是合同选择提示，不是对任一“独占”“排他”词在所有司法管辖区含义的保证。[国家版权局现行著作权法文本](https://www.ncac.gov.cn/xxfb/flfg/flfg_532/202103/t20210309_50530.html)、[网信办软件保护条例文本](https://www.cac.gov.cn/2013-02/08/c_12648744.htm)

## 21 项直接依赖的许可证据

每项均已读取本机构建缓存中相应版本的发布 POM；其 SHA-256、缓存相对位置、发布 POM 地址和许可声明保存在 [dependencies-license-evidence.json](dependencies-license-evidence.json)。表中链接是官方发布 POM 或官方代码版本的许可；并不表示已经下载核对所有远端 POM 字节，也不代替完整传递依赖清单。

| 坐标及版本 | POM / 官方许可 | 声明 |
| --- | --- | --- |
| androidx.core:core-ktx:1.13.1 | [Google POM](https://dl.google.com/dl/android/maven2/androidx/core/core-ktx/1.13.1/core-ktx-1.13.1.pom) | Apache-2.0 |
| androidx.webkit:webkit:1.8.0 | [Google POM](https://dl.google.com/dl/android/maven2/androidx/webkit/webkit/1.8.0/webkit-1.8.0.pom) | Apache-2.0 |
| androidx.appcompat:appcompat:1.7.0 | [Google POM](https://dl.google.com/dl/android/maven2/androidx/appcompat/appcompat/1.7.0/appcompat-1.7.0.pom) | Apache-2.0 |
| androidx.activity:activity:1.8.0 | [Google POM](https://dl.google.com/dl/android/maven2/androidx/activity/activity/1.8.0/activity-1.8.0.pom) | Apache-2.0 |
| com.google.android.material:material:1.14.0 | [Google POM](https://dl.google.com/dl/android/maven2/com/google/android/material/material/1.14.0/material-1.14.0.pom) | Apache-2.0 |
| androidx.constraintlayout:constraintlayout:2.2.0 | [Google POM](https://dl.google.com/dl/android/maven2/androidx/constraintlayout/constraintlayout/2.2.0/constraintlayout-2.2.0.pom) | Apache-2.0 |
| androidx.recyclerview:recyclerview:1.3.2 | [Google POM](https://dl.google.com/dl/android/maven2/androidx/recyclerview/recyclerview/1.3.2/recyclerview-1.3.2.pom) | Apache-2.0 |
| androidx.swiperefreshlayout:swiperefreshlayout:1.1.0 | [Google POM](https://dl.google.com/dl/android/maven2/androidx/swiperefreshlayout/swiperefreshlayout/1.1.0/swiperefreshlayout-1.1.0.pom) | Apache-2.0 |
| androidx.lifecycle:lifecycle-runtime-ktx:2.6.1 | [Google POM](https://dl.google.com/dl/android/maven2/androidx/lifecycle/lifecycle-runtime-ktx/2.6.1/lifecycle-runtime-ktx-2.6.1.pom) | Apache-2.0 |
| androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.1 | [Google POM](https://dl.google.com/dl/android/maven2/androidx/lifecycle/lifecycle-viewmodel-ktx/2.6.1/lifecycle-viewmodel-ktx-2.6.1.pom) | Apache-2.0 |
| androidx.lifecycle:lifecycle-livedata-ktx:2.6.1 | [Google POM](https://dl.google.com/dl/android/maven2/androidx/lifecycle/lifecycle-livedata-ktx/2.6.1/lifecycle-livedata-ktx-2.6.1.pom) | Apache-2.0 |
| androidx.work:work-runtime-ktx:2.9.0 | [Google POM](https://dl.google.com/dl/android/maven2/androidx/work/work-runtime-ktx/2.9.0/work-runtime-ktx-2.9.0.pom) | Apache-2.0 |
| org.jetbrains.kotlinx:kotlinx-serialization-json:1.5.0 | [官方版本 LICENSE](https://raw.githubusercontent.com/Kotlin/kotlinx.serialization/v1.5.0/LICENSE.txt) | Apache-2.0 |
| org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3 | [官方版本 LICENSE](https://raw.githubusercontent.com/Kotlin/kotlinx.coroutines/1.7.3/LICENSE.txt) | Apache-2.0 |
| com.squareup.okhttp3:okhttp:4.11.0 | [官方版本 LICENSE](https://raw.githubusercontent.com/square/okhttp/parent-4.11.0/LICENSE.txt) | Apache-2.0 |
| io.coil-kt:coil:2.6.0 | [官方版本 LICENSE](https://raw.githubusercontent.com/coil-kt/coil/2.6.0/LICENSE.txt) | Apache-2.0 |
| io.noties.markwon:core:4.6.2 | [官方版本 LICENSE](https://raw.githubusercontent.com/noties/Markwon/v4.6.2/LICENSE) | Apache-2.0 |
| org.nanohttpd:nanohttpd:2.3.1 | [官方版本 LICENSE](https://raw.githubusercontent.com/NanoHttpd/nanohttpd/nanohttpd-project-2.3.1/LICENSE.md)；许可继承自 nanohttpd-project:2.3.1 父 POM | BSD-3-Clause |
| dev.rikka.shizuku:api:13.1.5 | [官方 POM](https://repo.maven.apache.org/maven2/dev/rikka/shizuku/api/13.1.5/api-13.1.5.pom) / [官方 LICENSE](https://raw.githubusercontent.com/RikkaApps/Shizuku-API/master/LICENSE) | MIT |
| dev.rikka.shizuku:provider:13.1.5 | [官方 POM](https://repo.maven.apache.org/maven2/dev/rikka/shizuku/provider/13.1.5/provider-13.1.5.pom) / [官方 LICENSE](https://raw.githubusercontent.com/RikkaApps/Shizuku-API/master/LICENSE) | MIT |
| junit:junit:4.13.2（testImplementation） | [官方版本 LICENSE](https://raw.githubusercontent.com/junit-team/junit4/r4.13.2/LICENSE-junit.txt) | EPL-1.0，测试依赖 |

Apache-2.0 对再分发要求提供许可、保留相关归属、修改说明及已有 NOTICE 的相关归属内容；允许对自己的修改另设条款，但仍须满足第三方条款。BSD-3-Clause 要求源码/二进制保留其归属、条件及免责声明，且不得以贡献者名义背书。JUnit 是测试依赖，不能因此直接声称应用所有代码都受 EPL；若实际分发 JUnit 本身或其修改，应按 EPL 履行相应义务。[Apache 官方许可第 4、6 条](https://www.apache.org/licenses/LICENSE-2.0)、[NanoHTTPD 许可](https://raw.githubusercontent.com/NanoHttpd/nanohttpd/nanohttpd-project-2.3.1/LICENSE.md)、[JUnit 许可](https://raw.githubusercontent.com/junit-team/junit4/r4.13.2/LICENSE-junit.txt)

### Shizuku 特别区分

本项目直接引入的是 **Shizuku-API 的 api/provider（MIT）**，不是将整个 Shizuku 管理 App 源码改成自己的产品。Shizuku 主 App 仓库是 Apache-2.0，其官方 README 另外明确禁止挪用启动图标（展示 Shizuku 本身除外）、把 `Shizuku` 当应用名称、使用其 `moe.shizuku.privileged.api` 应用 ID 或声明 `moe.shizuku.manager.permission.*` 权限。这些说明属于具体上游品牌/命名边界，不能反过来把 MIT SDK 说成禁止所有商业使用；开发者正常集成 SDK 的权限、provider 和包引用也须与“冒用管理 App 身份”区分。[Shizuku 主仓库 License 段](https://github.com/RikkaApps/Shizuku#license)、[SDK LICENSE](https://raw.githubusercontent.com/RikkaApps/Shizuku-API/master/LICENSE)

## rice_paper.png 素材证据

本轮通过官方 GitHub 树及 blob API 确认原 `rice_paper.png` 与上游 `desktop/src/assets/textures/rice-paper.png` **逐字节对应**：

- 树提交：`9a6cabf8c513db5edc6cf8179a538c9075885c6e`；官方 API 返回 `truncated=false`。
- 上游 blob：`4dce15c5bc1fccc7adbdb7ae16acef3bc5033d21`，大小 131654 bytes。
- 双方 SHA-256：`A09B093B715FC9E8FD0F91AC02FCDC68636E825FA9555820EF8D6B8B7889D64B`。
- 对应树中未发现独立 `NOTICE` 文件；根 `LICENSE` 为 Apache-2.0，未见该图片单独许可说明。可按此现有上游证据保留原图及归属/完整 Apache 许可；这不构成对上游素材权利链的独立法律保证。

[固定提交资产位置](https://github.com/liliMozi/openhanako/blob/9a6cabf8c513db5edc6cf8179a538c9075885c6e/desktop/src/assets/textures/rice-paper.png)、[官方 blob API](https://api.github.com/repos/liliMozi/openhanako/git/blobs/4dce15c5bc1fccc7adbdb7ae16acef3bc5033d21)、[固定提交 LICENSE](https://github.com/liliMozi/openhanako/blob/9a6cabf8c513db5edc6cf8179a538c9075885c6e/LICENSE)。人物品牌图、字标、头像与收款二维码不因这一纹理的证据而获得授权，应分别核查或从公开导出排除。

## Gradle wrapper 的再分发

原 wrapper JAR 的 SHA-256 为 `e996d452d2645e70c01c11143ca2d3742734a28da2bf61f25c82bdc288c9e637`，与 Gradle 8.13 官方 wrapper 哈希不同；这说明它不是该官方 8.13 wrapper 字节，不等于已发现恶意文件。公开导出独立替换为官方 `v8.13.0` wrapper JAR，并核对 Gradle 官方校验值：

`81a82aaea5abcc8ff68b3dfcb58b3c3c429378efd98e7433460610fecd7ae45f`

[官方 wrapper 下载](https://raw.githubusercontent.com/gradle/gradle/v8.13.0/gradle/wrapper/gradle-wrapper.jar)、[官方校验值](https://services.gradle.org/distributions/gradle-8.13-wrapper.jar.sha256)、[wrapper 文档](https://docs.gradle.org/8.13/userguide/gradle_wrapper.html)。wrapper 启动脚本的原版权头也要保留。随附 `LICENSES/Gradle-8.13-LICENSE.txt` 和 `Gradle-8.13-NOTICE.txt` 分别来自官方同版本 [LICENSE](https://raw.githubusercontent.com/gradle/gradle/v8.13.0/LICENSE) 和 [NOTICE](https://raw.githubusercontent.com/gradle/gradle/v8.13.0/subprojects/distributions-full/src/toplevel/NOTICE)，保留其完整文本；这是构建工具归属，不将整个 Gradle 发行包作为本应用原创代码发布。

## 公开前仍须完成的核查

目前直接依赖许可未显示必须将本项目自有代码全部改为 MIT/Apache/EPL 的障碍。真正可能阻碍此快照公开或后续商业授权的是：未界定的来源、品牌图/个人素材、秘密与个人数据、曾对新增代码作出的旧公开授权，以及外部贡献者授权权限。来源清单和导出审查应落实到实际文件与表达，不能由本文替代。

本记录初稿仅覆盖直接依赖。后续因用户要求分发既有加固 APK，已另行解析 releaseRuntimeClasspath 的 96 个组件、89 个工件，收集 POM 许可（无缺失）及 14 项可见嵌入 LICENSE/NOTICE 记录，结果见同目录 runtime-dependencies-license-evidence.json。它不等于对旧 APK 逐类反编译核定完整权属或安全审计。仍未取得京东、淘宝、菜鸟或拼多多平台接口的商业合作许可。源码许可不代替平台服务协议、账号权限或数据处理授权；不要把个人设备验证、源码公开与平台商业授权混为一谈。
