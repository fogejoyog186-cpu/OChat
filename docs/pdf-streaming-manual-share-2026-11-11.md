# PDF 流式输入与手动分享验证（2026-11-11）

## 改动

- 移除普通附件导入、PDF 读取、准备请求及历史恢复中的固定文件大小和累计字节门槛。一次最多 10 个附件的既有数量规则保留。
- 新选择的 PDF 保存为私有原文件快照，描述符只保存引用。JSON 请求按块读取文件并编码 Base64，不把整份 PDF 或整份请求复制成大字符串；Content-Length 使用 long 计数。
- Chat Completions、Responses、Gemini 和 Anthropic 协议转换保留文件引用，最终发送实际文件字节。旧的字符串快照仍可读取。
- 接口明确返回 PDF 不支持、文档解析失败或请求体过大，且尚未返回正文或思考内容时，尝试一次逐页图像兼容请求。保留所有页面和页码；失败时保留原接口错误及兼容请求错误。鉴权失败和已有部分输出的请求不重发。
- 兼容图像逐页写入临时文件，默认最长边 2048 像素，内存不足时降低渲染分辨率；不截取前几页。完成、失败或取消后清理临时文件。
- PDF 预览复用原文件快照；文件管理器提供流式来源时，先复制为可随机读取的临时文件，再进行翻页。关闭面板后清理临时副本，保留会话快照。
- 外部应用分享只将文件和附带文本放入输入区，保留已有草稿。冷启动、旋转屏幕、已有模型或模型正在生成时，都不会自动发送或自动入队；用户点击发送后才调用模型。
- 保留 content URI 校验、分享授权校验和私有文件隔离。备份 ZIP 的 150 MiB 累计资源保护、32 MiB JSON 保护及解压安全检查保留；它们独立于普通 PDF 导入和发送。

## 验证结果

最终执行 Debug 构建、全部 JVM 单元测试、以下设备回归以及 Lint，Gradle 返回 BUILD SUCCESSFUL。

- JVM：127 项通过，0 失败、0 跳过。
- Android：Medium_Phone / API 37，60 项通过，0 失败、0 跳过。
- Lint：0 错误，55 项警告。
- git diff --check：通过。

设备回归类：

```text
PdfStreamingRegressionTest
DocumentPayloadRegressionTest
PdfProtocolWireRegressionTest
PdfServiceAttachmentRegressionTest
IncomingShareRegressionTest
AttachmentStorageRegressionTest
AttachmentModelInputRegressionTest
AppearanceAndAttachmentsRegressionTest
AttachmentPresentationRegressionTest
BackgroundGenerationRegressionTest
FinalFlowRegressionTest
```

关键用例包括：有效且超过 50,000,000 字节的纯图片 PDF，原文件与快照 CRC 一致；流式 JSON 字节计数与 Content-Length 一致；四种协议的真实文件数据序列化；三页 PDF 全部保留；原文件失效后的快照预览和备份恢复；流式文件提供器预览并翻页；错误后仅重试一次；鉴权及部分输出不重试；冷启动和忙碌状态分享后等待手动发送。

设备测试的接口响应使用本地模拟数据，没有使用真实付费模型 API。已验证本地文件处理、协议序列化及服务重试流程，不能据此断言某个中转接口已通过端到端测试。首次设备运行被系统更新 WebView 杀进程中断，更新结束后完整重跑通过。

## 能力边界

不设置 OChat 本地 PDF 文件大小上限，并不等于服务端没有限制。请求仍使用所选协议的内嵌文件或图像输入，未新增厂商 Files API 上传流程。模型的视觉能力、请求体、页数和上下文限制，以及设备剩余空间、损坏或加密文件，仍可能导致失败；错误会显示，当前发送的 PDF 不会因本地累计字节门槛被静默跳过。兼容图像模式经过栅格化，细节精度与原始 PDF 不同。

参考官方文档：

- [Gemini PDF 文档处理](https://ai.google.dev/gemini-api/docs/document-processing)
- [Gemini 文件输入方式](https://ai.google.dev/gemini-api/docs/file-input-methods)

本记录只描述本次工作区改动与上述验证，不代表已生成或发布新的 Release APK。
