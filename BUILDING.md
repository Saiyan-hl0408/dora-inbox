# 构建说明

## 环境

| 项目 | 要求 |
| --- | --- |
| Android | minSdk 29；compile/target 36；build-tools 36.0.0 |
| Java | JDK 17 或 21；源码/字节码目标 17 |
| Gradle | wrapper 8.13，已配置发行 ZIP SHA-256 |
| 插件 | Android Gradle Plugin 8.13.2；Kotlin/serialization 2.2.20 |

安装 Android SDK 的 `platforms;android-36`、`build-tools;36.0.0` 和 platform-tools。设置 `ANDROID_HOME`（或私有的 `local.properties` 中 `sdk.dir`）及 `JAVA_HOME`。不要提交 SDK、本机路径、签名密钥或 Gradle 缓存。

## 命令

macOS/Linux：

```sh
chmod +x gradlew
./gradlew --no-daemon --console=plain :app:assembleDebug :app:testDebugUnitTest
```

Windows PowerShell：

```powershell
.\gradlew.bat --no-daemon --console=plain :app:assembleDebug :app:testDebugUnitTest
# 或由可复用脚本设置环境（路径由使用者提供）
.\tools\build.ps1 -SdkPath 'D:\Android\Sdk' -JdkPath 'D:\Java\jdk-21'
```

首次构建需网络下载 Gradle 和 Maven 依赖；缓存完整后才可使用 `--offline` 或脚本的 `-Offline`。非 CI 默认先尝试阿里云镜像再回退官方源；`CI=true` 使用官方 Google/Maven/Gradle 源。这不保证所有地区首次联网下载可用。本轮验证使用既有依赖缓存，不声称已在空缓存机器完成首次安装。

Windows 若出现 `Unable to establish loopback connection`，可为脚本传入短、可写且支持当前 JDK 本地套接字的目录，例如 `-SocketPath 'D:\di-sock'`。脚本会设置当前进程的 TEMP/TMP 与 `jdk.net.unixdomain.tmpdir`，执行后恢复环境；本轮仅在显式短目录下验证成功，默认用户临时目录在本机失败。若已有 Gradle 8.13，可用 `-GradleExecutable` 指定其 `gradle.bat`；默认使用仓库 wrapper。

## 输出与发布

- Debug APK：`app/build/outputs/apk/debug/app-debug.apk`。
- 单元测试：`app/build/reports/tests/testDebugUnitTest/index.html`，XML 在 `app/build/test-results/testDebugUnitTest/`。
- `:app:assembleRelease` 可产生未签名 Release；本仓库不带个人签名、加固工程、mapping、安装包或发布凭据。签名与正式分发须自行配置并检查实际二进制的依赖声明。
- 仅提供 Debug 构建与合成测试的本轮验证；没有安装到设备、登录实际账号或触发验证码。测试预览 source set/内部回归 Activity 已从此快照排除。

官方 wrapper JAR 的 SHA-256：`81a82aaea5abcc8ff68b3dfcb58b3c3c429378efd98e7433460610fecd7ae45f`。工具归属和完整声明见 `LICENSES/Gradle-8.13-*`。源码公开版不会与既有个人加固 APK 逐字节一致。
