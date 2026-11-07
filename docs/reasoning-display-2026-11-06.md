# 思考内容接收与显示修复（2026-11-06）

本文记录当日的实现与验证结果，不代表当前版本的完整测试状态。构建方法见 [README](../README.md#构建与测试)；提到的截图和报告为本地生成产物，不随源码提交。

## 修复范围

原有解析器能接收到部分思考字段，但生成服务没有及时发布思考更新，聊天页也没有阅读入口。现在思考内容实时进入默认折叠的「正在思考 / 思考过程 / 思考摘要」区域，与最终回答分别保存。展开内容使用现有 Markdown 渲染器及主题配色，折叠时不解析完整文本；没有思考内容的消息不显示空区域。

- Chat Completions：接收 reasoning_content、reasoning、可读的 reasoning_details；兼容 content 开头的 <think> 块，支持标签跨流式分片。正文中的代码示例保持原样。
- Responses：接收 reasoning_summary_text、reasoning_summary_part 和最终 output 中的 summary。按 output_index / summary_index 归并，规范化完整快照替换流式草稿；重复 sequence_number 不重复追加。
- Anthropic Messages：接收初始 thinking 块和 thinking_delta。
- Gemini generateContent：将 thought=true 的文本与最终回答分开。
- 只读取接口提供的可读文本，不显示 signature、redacted_thinking 或 encrypted_content。

已知支持的 GPT Responses 模型自动请求 reasoning.summary=auto；Gemini 思考模型请求 includeThoughts=true。Auto 保留服务端默认强度，明确关闭思考时不额外请求摘要。Claude 是否开启思考继续遵循模型和用户现有配置。接口没有返回的思考内容不会被补写或推断。

思考文本随消息保存，切换会话及读取历史时恢复。展开状态在屏幕重建后保留；后台生成继续更新。停止时即使尚未生成正文，也会保存已有思考。输出预算耗尽、只有思考没有正文时，提示「尚未生成最终回答」，并保留截断状态及思考内容。

## 验证

- 88 项 JVM 单元测试全部通过，新增协议、摘要快照、分片标签、不可读块排除和请求字段用例。
- 31 项设备回归全部通过，覆盖思考先于正文、折叠阅读、重建、后台、切换会话、停止保留、无思考普通回复、仅思考预算耗尽，以及既有分支和搜索功能。
- Debug、AndroidTest、未签名 Release 构建及 lintDebug 通过。
- 使用协议样例和模拟传输验证，没有调用收费模型接口。
- 已检查截图：app/build/reports/reasoning-display.png。

## 官方协议参考

- [OpenAI Reasoning summaries](https://developers.openai.com/api/docs/guides/reasoning)
- [OpenAI Responses streaming events](https://developers.openai.com/api/reference/resources/responses/streaming-events)
- [Anthropic streaming](https://platform.claude.com/docs/en/build-with-claude/streaming)
- [Gemini thinking](https://ai.google.dev/gemini-api/docs/generate-content/thinking)
