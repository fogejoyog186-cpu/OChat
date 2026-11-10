# 文档输入与系统分享修正

## 已确认的问题

- 文件读取上限原为 3 MiB，附件合计上限为 8 MiB；Office 预览另有独立的 3 MiB 限制。
- 后台服务准备附件时，缺少快照和预处理缓存会取到 null，随后调用 isEmpty；异常被替换为“附件未发送”文本，模型仍被调用。这条路径与文件是否包含图片无关，也能影响小 PDF。
- 切换协议后，Chat Completions 的 file 和 Responses 的 input_file 没有双向规范化，部分文件块被拒绝或丢失。
- 普通文本以 data: 开头、或内容恰好是 data URI 时，可能被误判为文件；以 [ 开头的合法 JSON 也曾被当成附件错误。

## 实现

PDF 发送原始文件，保留扫描页、图片、图表和文本。对 PDF MIME 或扩展名优先识别，核查文件头，流式 Base64 编码；不把 PDF 降级为仅提取文字。四种协议分别使用 file、input_file、document.source、inlineData。未知或无效内容块明确报错，本次附件失败时不再继续调用模型。

附件原始数据合计须小于 50000000 字节，最多 10 个。Office 和文本采用有界流式提取，UTF-8 / UTF-16 BOM 可识别；DOCX、PPTX、XLSX 支持正文提取，XLSX 解析共享字符串。正文最多 200000 字符，截断明确标记。Office 累计解压上限 64 MiB，拒绝 DTD / 外部实体和过多条目。旧版 DOC / XLS / PPT 须先转换。

大快照存入应用私有目录，SharedPreferences 和 Activity 状态只保留引用。请求 JSON 流式写出，避免整个大请求再次转为字符串。会话、分支、发件队列、检查点及活动草稿引用均受保护；无引用快照保留 24 小时，后台清理最多每小时运行一次。备份不携带私有快照地址；原文件丢失时可从快照恢复，确实不可用的附件明确标记。

## 系统分享

MainActivity 接收 ACTION_SEND / ACTION_SEND_MULTIPLE，支持 EXTRA_STREAM、ClipData 和 content URI，按 URI 去重并复制临时授权文件。输入区为空且配置有效时自动发送；已有草稿、部分导入失败、模型或会话变更时保留输入等待手动发送。后台生成中的同一会话使用现有队列。旋转或重建保留导入任务和文件，不重复发送。

分享后清除外部 extras、ClipData、selector 和授权 flags，同时保留页面启动 filter；消费状态随 Activity 保存。这样既不重放原始分享，也不会因替换 action / category 使生命周期跟踪失配。

## Best Practices and Security Alignment Update

影响：新增公开的文档分享入口，未增加存储或媒体运行时权限。只有 MainActivity 为接收分享而公开；后台服务和附件 FileProvider 继续不导出。

相关文件：AndroidManifest.xml、IncomingShare.java、MainActivity.java；附件持久化与备份涉及 AttachmentSelection.java、AttachmentSnapshots.java、SessionRepository.java 和 ConversationBackup.java。

实现要求：只接收文档 URI 和文字，不采用外部 modelId、sessionId、resumeGeneration、API 地址或嵌套 Intent；拒绝 file / HTTP URI、应用自身私有 Provider 和缺少读取授权的 Provider。临时 URI 复制到应用私有存储。快照名和目录做规范化检查，导入备份清除伪造的快照引用及删除标记，取消恢复只删除本次新建文件。

关键统一 diff 摘录（完整改动见仓库 diff）：

```diff
- if(running!=null&&getIntent().getBooleanExtra("resumeGeneration",false))
+ if(running!=null&&resumingGeneration(getIntent()))
```

```diff
+ <action android:name="android.intent.action.SEND" />
+ <action android:name="android.intent.action.SEND_MULTIPLE" />
+ <category android:name="android.intent.category.DEFAULT" />
+ <data android:mimeType="application/*" />
+ <data android:mimeType="text/*" />
+ <data android:mimeType="image/*" />
```

## 官方依据与边界

[Gemini PDF 文档处理](https://ai.google.dev/gemini-api/docs/generate-content/document-processing)：原生视觉支持 PDF 内的文本、图片、扫描页与图表，文件上限 50 MB / 1000 页。[文件输入方式](https://ai.google.dev/gemini-api/docs/file-input-methods)说明内嵌文件限制和 Files API 的用途。

[OpenAI PDF 输入](https://developers.openai.com/api/docs/guides/file-inputs)要求支持视觉的模型，PDF 文本与页图像一起处理；[Claude PDF 支持](https://platform.claude.com/docs/en/build-with-claude/pdf-support)使用 document 内容块处理文本与页图像。

此修正验证 OChat 实际发出的字节和协议格式，不代表任意第三方代理、文本模型或任意加密 / 损坏 PDF 都能识别。模型页数、上下文、请求大小和服务端能力限制仍适用。没有添加本地 OCR 或大型文档渲染依赖，也没有访问真实收费模型接口。

## 验证

JVM：123 项测试通过。Android 17（API 37）Medium_Phone 模拟器：49 项定向设备回归测试全部通过，无跳过；Debug 应用和测试 APK 编译、Lint 均通过。

设备测试包括 DocumentPayloadRegressionTest、PdfProtocolWireRegressionTest、PdfServiceAttachmentRegressionTest、IncomingShareRegressionTest、AttachmentStorageRegressionTest，以及原有附件输入、附件展示、后台生成和 FinalFlowRegressionTest。PDF 测试创建真实含位图的 PDF：小于 3 MiB 的文件通过四种协议的流式 / 非流式实际 HTTP 请求捕获并逐字节比对；大于 3 MiB 的扫描件可渲染且编码保持原样，多个 PDF 超过旧 8 MiB 合计限制也保持文件块。

分享测试验证冷 / 热启动、读取授权、草稿保护、自动队列、伪造路由参数被忽略及重建后不重复。存储测试验证原文件丢失后的备份恢复、丢失快照的明确标记、文本 data URI 原文、路径限制及共享引用的清理保护。测试使用本地模拟接口，不需要真实模型 API Key。

首轮设备安装因模拟器预装较高版本号被拒绝，通过保留数据的 -d 安装解决。首轮分享测试发现替换启动 Intent 的 filter 导致 ActivityScenario 失配；修正后完整重跑 49 项通过。此记录不覆盖仓库中其他未运行的设备测试。
