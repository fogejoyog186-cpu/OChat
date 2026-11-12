# 新对话模型与原生工具（2026-11-11）

## 行为

- 显式新建和冷启动的新对话继承上次活动对话的供应商与模型。全局提示词、采样和搜索默认值仍用于新对话，思考强度从 Auto 开始；不复制旧对话的覆盖配置或凭据。
- 复用空白占位对话也会更新模型。上次模型被删除时回退到该供应商的可用模型，供应商不存在时使用全局默认配置。旋转和恢复正在生成的对话不触发新建。
- 供应商协议选择下显示地址说明。OpenAI 官方示例包含 /v1；Anthropic 推荐根地址并自动补齐 /v1/messages；Gemini 原生协议推荐根地址并自动补齐 /v1beta/models/...。Anthropic 实际请求仍包含 /v1，已有版本路径或完整 Messages 地址继续兼容。
- 切换协议仅替换已识别的官方示例地址，不覆盖自定义网关地址。
- 重命名后立即更新顶部标题，不等待异步持久化回调。

## 搜索与工具

| 协议 | 内置搜索 | 可选网页读取 | 可选代码执行 |
| --- | --- | --- | --- |
| OpenAI Responses | web_search | 通过搜索工具访问网页 | code_interpreter |
| Anthropic Messages | web_search_20250305 | web_fetch_20250910 | code_execution_20260521 |
| Gemini generateContent | googleSearch | urlContext | codeExecution |
| OpenAI Chat Completions | web_search_options，需要搜索专用模型 | 无通用托管工具字段 | 无通用托管工具字段 |

设置 → 联网搜索与工具中可选择自动、模型内置、Tavily。自动模式按协议选择，普通 Chat Completions 模型使用 Tavily，gpt-5-search-api 使用内置搜索。保留旧版搜索预览模型 ID 的兼容识别不代表厂商仍提供这些模型。旧的 Responses 搜索偏好迁移为通用“模型内置”。

网页读取和代码执行分别开启，默认关闭，在供应商服务端执行。工具是否可用、组合限制、地区、权限和费用由模型服务决定。未支持的协议不发送对应工具；HTTP 错误保留接口细节并提示工具能力限制，不自动重发收费请求。

DeepSeek 官方域名按官方能力处理：Pro 的 Responses / Anthropic 可使用内置搜索；Flash 自动模式使用 Tavily，手动强制内置搜索会明确报错。不会向官方 DeepSeek 发送其忽略或不支持的代码执行和网页读取声明。第三方网关保持供应商自己的能力。

来源只收集接口返回的结构化引用，不把正文里的任意链接当成搜索证据。支持 Chat 嵌套 annotations、Claude 搜索及网页读取结果与增量引用、Gemini groundingChunks / URL metadata，以及 Responses 来源。搜索、网页读取状态与最终生成状态独立保存，工具输入/输出简要记录位于消息详情。Google 搜索建议按需在禁用脚本、文件访问和网络子资源的 WebView 中展示，外部链接通过现有安全链接处理。

Claude 的 pause_turn 在完整收到 message_stop 后续传；原样保留思考签名、加密块、工具内容和 container ID，只追加 assistant 消息，不伪造客户端执行结果。每轮累计用量并扣减剩余输出预算，最多续传 10 次；取消和传输中断不会触发下一轮。这不包含自定义客户端函数或 MCP，也不保存跨用户回合的代码容器会话。

Tavily 未配置或失败时仅关闭本次外部搜索，保留独立工具开关。Tavily 与原生网页读取并用时合并来源。

## 验证

- JVM：143 项通过，0 失败、0 跳过。
- Medium_Phone / API 37：分两批执行 26 项与 65 项设备测试，合计 89 项不同测试通过（2 项搜索服务测试重复验证），0 失败、0 跳过。
- Lint：0 错误、62 项警告；包括已有依赖、资源、国际化及无障碍提醒，本次新增工具开关文字也有国际化提醒，未宣称警告全部来自历史代码。
- 新增生产类的 IDE 检查无错误；git diff --check 通过。
- 覆盖冷启动和显式新建的模型继承、旋转、删除模型回退、协议提示、工具设置确认保存和旋转恢复、各协议请求字段、结构化来源与危险链接过滤、Claude 分轮续传/用量/预算/取消/EOF、Gemini 代码结果、Tavily 失败与来源合并，并回归 PDF、分享、后台生成、模型选择和思考档位。

首轮回归发现重命名依赖异步落盘才刷新标题，补上立即刷新后保留原断言通过。所有模型请求均使用本地模拟接口，没有使用真实 API Key；厂商实际权限、收费、限流和模型组合限制尚需用户自己的服务验证。本次没有运行包含已知 Markdown 链接长按问题的整个设备套件。

版本保持 1.1.1 / versionCode 3。assembleRelease 成功，签名包输出到 app/build/outputs/apk/release/app-release.apk，apksigner 验证 v2 签名通过，证书与旧版一致。已有 release-artifacts/OChat-1.1.1.apk 历史归档保留；历史归档不包含本次工具改动。没有提交、推送或发布 GitHub Release。

## 官方参考

- [OpenAI Web search](https://developers.openai.com/api/docs/guides/tools-web-search)
- [OpenAI Code Interpreter](https://developers.openai.com/api/docs/guides/tools-code-interpreter)
- [Anthropic 服务端工具及续传](https://platform.claude.com/docs/en/agents-and-tools/tool-use/server-tools)
- [Anthropic Web search](https://platform.claude.com/docs/en/agents-and-tools/tool-use/web-search-tool)
- [Anthropic Web fetch](https://platform.claude.com/docs/en/agents-and-tools/tool-use/web-fetch-tool)
- [Anthropic Code execution](https://platform.claude.com/docs/en/agents-and-tools/tool-use/code-execution-tool)
- [Gemini generateContent REST](https://aistudio.google.com/api-reference/generate-content)
- [Gemini 工具](https://aistudio.google.com/docs/tools)
- [Gemini 搜索](https://aistudio.google.com/docs/google-search)
- [DeepSeek Responses](https://api-docs.deepseek.com/guides/responses_api/)
- [DeepSeek Anthropic 兼容](https://api-docs.deepseek.com/guides/anthropic_api/)
