# OChat

OChat 是一款 Android AI 对话客户端，可自行配置模型提供商、接口地址和 API Key。

## 功能

- 支持 OpenAI Chat Completions、OpenAI Responses、Anthropic Messages 和 Google Gemini 原生协议。
- 流式回复、模型切换、按模型能力选择思考强度，以及接口返回的可读思考内容展示。
- 对话历史搜索、重命名、置顶、归档、分支和 ZIP 备份恢复。
- 图片与文件附件，Markdown、数学公式、语法着色代码块，以及 HTML / SVG 静态预览。
- Tavily 联网搜索，以及 Responses、Anthropic、Gemini 和支持搜索的 Chat Completions 模型的内置搜索与来源展示。
- 可选的服务端网页读取和代码执行，工具记录可在消息详情中查看；实际支持范围取决于协议和模型。
- 新对话继承上次对话所选模型，思考强度从 Auto 开始。
- 多会话生成、后台生成、消息队列和长会话分页。
- 浅色与深色主题、主题配色和思考强度动画配色。

模型能力、附件类型、搜索和思考内容的可用性取决于所选模型及服务接口。HTML / SVG 预览不执行 JavaScript，不能替代完整浏览器。

PDF 默认以原文件流式发送，保留扫描页、图片和图表；OChat 不设置固定的 PDF 文件大小上限。接口明确拒绝 PDF 或请求体过大且尚未返回内容时，会尝试将全部页面转为图像兼容发送一次。模型接口的页数、上下文和视觉能力仍会影响最终处理；加密、损坏或设备无法打开的文件会明确报错。PDF 预览支持流式文件来源，并可在原文件失效后使用已保存的快照翻页。DOCX、XLSX、PPTX 和文本文件在本地提取文字（最多 200000 字符，超出时明确标记截断），旧版 DOC / XLS / PPT 请先转换格式。

模型详情中的「PDF 转文字」和「图片转文字」分别配置开关与识别模型。开启 PDF 转文字时，仅将 PDF 通过系统组件逐页转图交给指定视觉模型；开启图片转文字时，仅处理独立图片附件。未开启的附件类型保持原发送方式。两项可选择不同模型，结果转为文字交回原模型回答，不会将整段对话发给识别服务。识别结果按附件内容和识别模型缓存，原始附件保留。旧版统一识别设置仅继承为 PDF 转文字，图片转文字默认关闭。识别会产生相应服务调用费用；失败、空结果或输出不完整时停止。未知二进制格式、损坏或加密文件不会被当成已识别文档。

已撤掉 PDFBox 和其加密库依赖，不再本地提取 PDF 文字层。DeepSeek 官方接口不支持原文件输入：未开启 PDF 转文字时，`deepseek-flash` 及兼容别名仍接收 PDF 的全部页面图像；Pro 等文本模型读取 PDF 需先开启 PDF 转文字并指定视觉模型。第三方供应商按各自的文件支持能力处理。详见 [DeepSeek 官方说明](https://api-docs.deepseek.com/guides/responses_api/)。

可在文件管理器或其他应用中选中文档，通过系统“分享”选择 OChat。分享文件只会添加到输入区并保留已有草稿，必须点击发送才会调用模型或加入消息队列。

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

填写完整配置后生成并归档签名 APK：

```sh
./gradlew :app:archiveReleaseApk
```

Windows 使用 `.\gradlew.bat :app:archiveReleaseApk`。归档产物位于 `release-artifacts/OChat-<versionName>.apk`，可直接附加到 GitHub Release；该目录已被 Git 忽略，普通构建或 clean 不会清理它。标准构建产物仍位于 `app/build/outputs/apk/release/`。后续版本继续使用同一发布密钥，并递增 `versionCode`。

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
