# OChat

OChat 是一款 Android AI 对话客户端，可自行配置模型提供商、接口地址和 API Key。

## 功能

- 支持 OpenAI Chat Completions、OpenAI Responses、Anthropic Messages 和 Google Gemini 原生协议。
- 流式回复、模型切换、按模型能力选择思考强度，以及接口返回的可读思考内容展示。
- 对话历史搜索、重命名、置顶、归档、分支和 ZIP 备份恢复。
- 图片与文件附件，Markdown、数学公式、语法着色代码块，以及 HTML / SVG 静态预览。
- Tavily 联网搜索、支持的 Responses 接口内置搜索，以及来源展示。
- 多会话生成、后台生成、消息队列和长会话分页。
- 浅色与深色主题、主题配色和思考强度动画配色。

模型能力、附件类型、搜索和思考内容的可用性取决于所选模型及服务接口。HTML / SVG 预览不执行 JavaScript，不能替代完整浏览器。

## 开始使用

1. 进入设置，创建模型提供商。
2. 填写接口地址和 API Key，并选择服务对应的接口协议。
3. 获取模型列表或手动添加模型 ID，保存供应商。
4. 返回对话页选择模型，开始对话。

API Key 在应用内配置，无需写入源码。消息和所选附件会发送至配置的模型服务；启用联网搜索时，相关查询还会发送至配置的搜索服务。请使用自己信任的接口地址。

对话备份可能包含私人消息与附件。API Key、真实对话、附件、配置导出、日志及未脱敏截图均不应提交到公开仓库。

## 构建与测试

最低支持 Android 7.0（API 24）。当前工程使用 Android SDK 37、Android Gradle Plugin 9.3.3 和 Gradle Wrapper 9.5.0。Gradle daemon 的 JVM 要求为 JDK 25（见 `gradle/gradle-daemon-jvm.properties`），Java 源码与字节码目标为 11。

使用兼容上述构建版本的 Android Studio 打开工程，安装 Android SDK 37 后同步 Gradle。确保 JDK 25 可用；Wrapper 可根据工程内的 daemon JVM 配置自动下载匹配的工具链。首次构建需要联网下载工具链与依赖；SDK 的本机路径通过 Android Studio 或未提交的 `local.properties` 配置。

Windows PowerShell：

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
```

macOS / Linux：

```sh
chmod +x gradlew
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
```

连接设备或启动模拟器后，可运行设备回归测试：

```sh
./gradlew :app:connectedDebugAndroidTest
```

Windows 使用 `.\gradlew.bat :app:connectedDebugAndroidTest`。JVM 测试与设备回归中的模型和搜索验证使用模拟数据，不需要真实模型 API Key。

Debug APK 输出到 `app/build/outputs/apk/debug/`，测试及 Lint 报告位于 `app/build/reports/`。这些产物不会随源码提交。

## 发布构建

使用 Android Studio 的 **Build → Generate Signed Bundle / APK → Create new** 创建发布密钥，将密钥保存在工程目录之外并备份。复制根目录的 `keystore.properties.example` 为 `keystore.properties`，在本机填写四个字段：

- `storeFile`：密钥文件的绝对路径，或相对于工程根目录的路径。
- `storePassword`：密钥库密码。
- `keyAlias`：签名密钥别名。
- `keyPassword`：签名密钥密码。

配置使用 Java Properties 格式。Windows 路径请使用 `/`；值中的反斜杠需要写成 `\\`。不要将密钥或密码粘贴到问题反馈、日志或公开仓库。`.gitignore` 已排除本机 `keystore.properties` 及签名密钥文件，公开模板不包含凭据。

填写完整配置后生成签名 APK：

```sh
./gradlew :app:assembleRelease
```

Windows 使用 `.\gradlew.bat :app:assembleRelease`。产物位于 `app/build/outputs/apk/release/`，请将签名后的 `app-release.apk` 附加到 GitHub Release。后续版本继续使用同一发布密钥，并递增 `versionCode`。

未创建本机签名配置时，Release 构建保留未签名产物 `app-release-unsigned.apk`，不能直接安装或作为正式 APK 分发。Debug 构建和默认 GitHub CI 不需要发布密钥。

## GitHub 仓库

仓库名称统一使用 **OChat**。`.gitignore` 排除本机 SDK 配置、IDE 状态、构建产物、日志和签名凭据，保留 Gradle Wrapper。

[Android 工作流](.github/workflows/android.yml)在 push、pull request 或手动触发时执行 Debug 构建、JVM 单元测试和 Lint。设备回归测试需单独运行，已知限制见下文。

项目许可证尚待作者选择，当前未添加许可证文件。

## 工程结构

- `app/src/main/`：应用代码与资源，应用 ID 为 `com.changyu.ochat`。
- `app/src/test/`：JVM 单元测试。
- `app/src/androidTest/`：Android 设备回归测试。
- `docs/`：各次实现和验证记录；其中的测试结果只对应记录日期。

## 已知限制与反馈

当前已记录一项 Markdown 链接长按问题：长按结束后可能意外打开链接，对应 `InteractionRegressionTest#actualMarkdownTouchOpensCapturedBrowserAndCopyUsesRawCode`。复现和历史验证情况见[模型排序与联网上下文修正记录](docs/model-order-web-search-2026-11-06.md#验证和边界)。

上下文 token 与费用为估算值，模型的实际输出上限及计费以服务端为准。使用默认 token 配置前，应按模型能力调整参数。

反馈问题时，请附上应用版本、Android 版本、接口协议、模型 ID、复现步骤和脱敏后的错误信息。
