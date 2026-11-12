# DeepSeek 文档输入与 OChat 1.1.1（2026-11-11）

## 原因与实现

DeepSeek 官方 Responses 文档明确说明不支持原文件输入。OChat 之前把 PDF 作为 input_file 发送，只有接口报错时才执行 PDF 图像重试，无法处理接口正常回复但未读取附件的情况。接口返回成功不能作为附件已经被理解的证据。

本次增加发送前的文档转换，只匹配 api.deepseek.com 官方域名及 Chat Completions、Responses、Anthropic 协议，不按模型名称改变第三方接口的行为。

- deepseek-flash 和官方支持的 deepseek-v4-flash / deepseek-v4-flash-vision-exp 别名：PDF 全部页面转换为图像，首次请求即携带页面内容。
- Pro 等文本模型：使用 PDFBox Android 本地提取 PDF 文字层，按原页码发送；明确说明图片和图表未发送，空白页或扫描页带有不可读取标记。
- 当前附件整份 PDF 没有可提取文字时，文本模型的请求在本地停止，提示选择 deepseek-flash 或其他视觉模型。历史不可读取附件明确标记，不阻断新的纯文字问题。
- 文本模型的直接图片输入同样明确报错。旧版原始 Office / 文本文件快照转换为文字，旧图片文件块转换为图像输入，未知二进制文件不会被当成乱码文字发送。
- HTTP 客户端对尚未转换的官方文件块进行校验，避免再次把不支持的输入交给服务端。
- PDF 文字提取使用磁盘临时缓冲，一次遍历页面；不增加固定的 PDF 文件大小、页数或文本截断门槛。模型上下文预算继续生效。
- 规范化 PDF 字体映射产生的 CJK 兼容字符；ASCII、全角代码字符及其他符号保持原值。
- 原始会话附件保留，可切换其他模型重新读取。取消和完成时清理本次转换文件。

本版不增加 OCR，也不自动切换模型。Pro 无法凭文字层理解原图片或图表；需要完整视觉信息时应选择支持视觉的模型。

参考：

- [DeepSeek Responses API](https://api-docs.deepseek.com/guides/responses_api/)
- [DeepSeek Vision](https://api-docs.deepseek.com/guides/vision/)
- [DeepSeek 模型能力与官方别名](https://api-docs.deepseek.com/quick_start/pricing/)
- [PDFBox Android](https://github.com/TomRoush/PdfBox-Android)

## 验证

执行 testDebugUnitTest、指定设备回归、lintDebug、archiveReleaseApk，最终 Gradle 返回 BUILD SUCCESSFUL。

- JVM：134 项通过，0 失败、0 跳过。
- Medium_Phone / API 37：71 项相关设备测试通过，0 失败、0 跳过。
- Lint：0 错误、59 项警告。
- Android Studio Gradle 同步成功，新增生产类的 IDE 检查没有错误。
- git diff --check 通过。

新增回归覆盖官方域名精确匹配及伪域名、模型视觉能力和官方别名、未转换文件块拦截、真实中英文 PDF 正文、旧版快照、空白页和扫描页页码、全页视觉转换、历史附件、取消与临时文件清理，以及通过真实后台服务在首次成功响应前携带正文或图像。保留并运行之前的 60 项 PDF、文件、分享和后台生成回归。

首轮测试发现中文字体映射将“文”提取成康熙部首兼容字符，修复后包含原断言的完整测试通过。相关模型请求均使用本地模拟接口，没有使用真实 DeepSeek API Key，也没有验证真实厂商的收费、限流或上下文行为。

## 安装包

- 版本：1.1.1；versionCode：3。
- 归档：release-artifacts/OChat-1.1.1.apk。
- 大小：14,804,947 字节（14.12 MiB）。
- SHA-256：70d956cff75e113b4290646743fc426fa3b1749e8dc3d35ab00023b2f8aa1b6a。
- apksigner 验证成功，v2 签名；使用与旧版相同的发布证书。
- 已有 1.1.0 归档保留。本次仅生成本地安装包，没有提交、推送或发布 GitHub Release。
