# 选择菜单与思考强度修复（2026-11-03）

本文记录当日的实现与验证结果，不代表当前版本的完整测试状态。构建方法见 [README](../README.md#构建与测试)；APK 为本地生成产物，不随源码提交。

已修复单选、多选、模型选择和复用列表行的旧高亮残留。界面按实际选择重新绑定背景与勾选状态，列表自身的 selected 状态不再决定是否重绘；确认前的选择只保留在弹窗内，取消不会改变模型或思考强度。

思考设置绑定到具体供应商和模型 ID。新会话默认自动；实际确认切换到另一供应商或型号时重置为自动，包括切回以前用过的型号。重新进入同一型号时保留合法的手动设置，不合法或来自旧型号的设置会清理为自动。旧版本没有绑定信息的遗留强度会迁移为自动。

识别使用请求模型 ID，支持大小写、常见供应商命名空间及日期后缀。显示名称不参与能力判断。已校正旧 Claude 日期 ID，避免将 20240229 等日期误认为模型版本。

| 系列 | 内置识别与请求处理 |
|---|---|
| GPT / o 系列 | 区分 GPT-5、5.1、5.2–5.5、5.6、GPT-6 各型号，以及 Codex、Pro、o 系列；GPT-6 Astra / 6.1 Sol 不显示关闭或 minimal；内置 API 档位不包含 Codex 产品的 ultra 模式。 |
| Claude | 区分自适应思考、旧版 thinking budget、Opus 4.5 的 effort 与预算组合；4.6 和较新型号按各自支持的档位显示，强制思考型号不显示关闭。 |
| DeepSeek | V4 / Flash 提供关闭、低、高、最高；V3.1 / V3.2 提供思考开关；固定思考型号使用自动。Chat、Responses 和 Anthropic 格式分别生成实际支持的参数。 |
| Gemini | 3 Pro、3.1 Pro、各 Flash / Flash-Lite 版本显示各自支持的等级；2.5 使用 thinkingBudget，Pro 不提供关闭。原生协议与 OpenAI 兼容格式分别处理。 |
| Grok | 3 Mini、4.3、4.5、4.6 / 4.7 区分档位与关闭支持。兼容 gork-* 形式的规则识别，实际请求保留原模型 ID。 |
| Kimi | K3 / K3.1 Pro 提供低、高、最高；K2.5 / K2.6 提供开关；K2.7 Code、固定思考型号使用自动。为需要保留式思考的型号保留并回传对应推理字段。 |
| GLM | 5.3 系列提供低、高、最高且不允许关闭；5.2 提供关闭、高、最高；4.5–5.1 等混合思考型号提供开关。 |

“自动”不发送手动思考参数。未知型号默认使用自动；兼容网关有额外能力时，可在模型详情关闭“按模型 ID 自动识别能力”后显式配置。已识别型号的旧自动生成字段不会覆盖新的内置规则。

原有滑条、粒子流动和渐变动画继续使用。对没有“关闭”档的模型，最低“低”档现在也会流动；触摸范围、刻度、填充和滑块位置同步调整。仅支持开关的型号显示“关闭思考 / 开启思考”，自动和关闭时不运行粒子。

验证：Debug、Release、测试 APK 构建通过；69 项 JVM 单元测试通过，38 项设备回归通过。新增回归直接核对背景像素与勾选状态，覆盖列表 selected 状态变化和行复用、取消 / 确认模型选择、切换与重新进入、过时强度清理、模型请求参数、旧 Claude 日期 ID，以及最低思考档的粒子和触摸位置。供应商验证使用官方文档与模拟数据，未使用真实账号执行计费请求。

官方来源：

- [OpenAI Docs：Reasoning](https://developers.openai.com/api/docs/guides/reasoning)、[GPT-6.1 Sol](https://developers.openai.com/api/docs/models/gpt-6.1-sol)、[GPT-6 Astra](https://developers.openai.com/api/docs/models/gpt-6-astra)
- [Claude effort 与型号差异](https://platform.claude.com/docs/en/build-with-claude/effort)、[Extended thinking](https://platform.claude.com/docs/en/docs/build-with-claude/extended-thinking)
- [DeepSeek Thinking Mode](https://api-docs.deepseek.com/guides/thinking_mode/)
- [Gemini thinking](https://ai.google.dev/gemini-api/docs/generate-content/thinking)、[Gemini OpenAI compatibility](https://ai.google.dev/gemini-api/docs/openai)
- [Grok reasoning](https://docs.x.ai/developers/model-capabilities/text/reasoning)、[Grok 4.3 与旧型号映射](https://docs.x.ai/developers/migration/may-15-retirement)
- [Kimi 思考模型](https://platform.kimi.com/docs/guide/use-thinking-models)、[Kimi K2.5 官方模型卡](https://huggingface.co/moonshotai/Kimi-K2.5)
- [GLM 深度思考参数](https://docs.bigmodel.cn/cn/guide/capabilities/thinking)

本地构建产物：`app/build/outputs/apk/debug/app-debug.apk`、`app/build/outputs/apk/release/app-release-unsigned.apk`（未签名）。
