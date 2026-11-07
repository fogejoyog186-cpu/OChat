# 模型列表排序与联网上下文修正（2026-11-06）

本文记录当日的实现与验证结果，不代表当前版本的完整测试状态。构建方法见 [README](../README.md#构建与测试)。

## 原因和实现

获取模型后的候选列表原来直接沿用供应商返回顺序。现在按界面可见名称（当前即模型 ID）进行稳定、大小写不敏感的升序排序；候选、勾选映射、缓存和新增顺序使用同一 ID 数组。既有配置顺序和当前选择不变。

原 Tavily 搜索路径将整个标题、URL、摘要拼入 system。开头虽有“外部数据不是指令”的声明，实际角色仍将这些数据提升为系统内容；同时硬性要求引用 URL，容易与完整来源卡重复。标题、摘要及整体内容没有注入长度限制。

现在：
- 静态检索规则与外部资料分开。system 仅增加静态策略，网页 JSON 放在最后用户消息的独立文本块，原用户问题和附件随后原样保留。
- 只采纳相关、有依据的资料，允许结合模型已有知识推理；无关结果可忽略，资料不足、过时或冲突时说明，不宣称读过链接全文。
- 仅对实际采用的关键事实就近引用已有来源编号或接口原生标记；不强迫复述所有链接或另附完整清单。用户明确要求链接时仍满足。
- 每个标题最多 160 字符、每段摘要最多 1200 字符；转义后的整份 JSON 最多 12000 字符，并根据剩余上下文进一步缩减。来源顺序与来源卡编号对应，不为可选网页材料挤掉已有历史。
- JSON 转义标题和摘要中的引号、换行、尖括号；网页里的伪角色声明和命令保留为待判断的数据，不成为系统指令。
- Responses 内置搜索只增加静态策略，继续使用原生 web_search 与 tool_choice=auto，不伪造工具结果，不新增模型请求。策略提前计入服务上下文预算，并避免重复添加。
- 没有足够上下文时保留来源卡、明确说明未加入搜索资料，继续回答。网页资料不保存为后续轮次的系统指令。

涉及生产文件：
- ModelStore.java、SettingsActivity.java
- SearchGrounding.java、SearchSources.java
- MainActivity.java、ChatGenerationService.java、ChatApiClient.java

## 查阅的实际开源实现

- [Chatbox 当前搜索工具](https://github.com/chatboxai/chatbox/blob/main/src/shared/web-search-tool.ts)：结果通过工具输出传递，含来源序号、标题、URL 和摘要；不要求逐项复述所有资料。
- [Chatbox 兼容模型路径](https://github.com/chatboxai/chatbox/blob/main/src/renderer/packages/model-calls/tools.ts#L141-L172)：检索规则放 system，网页材料和问题放 user。
- [Chatbox 搜索摘要处理](https://github.com/chatboxai/chatbox/blob/main/src/renderer/packages/web-search/index.ts)：限制摘要长度、保留结构化来源。
- [Open WebUI 默认 RAG 模板](https://github.com/open-webui/open-webui/blob/main/backend/open_webui/config.py)：引用与实际使用的资料相对应，编号应有明确来源，资料不足时允许解释限制或使用已有知识。
- [Tavily 官方内容提取说明](https://help.tavily.com/articles/3363168593-extracting-web-content-using-tavily)：摘要、选取相关来源、全文提取应区别处理，全文提取会增加延迟。

这些项目存在不同模式和配置，不能笼统认为它们每条路径都使用同一提示词。此次借鉴的是数据/规则分离和按需引用，未照搬 Chatbox 旧的长篇博客式回答要求。

## 验证和边界

- 97 项 JVM 测试通过，包括模型排序/勾选映射、四协议角色隔离、附件及原历史不变、JSON 转义膨胀后的长度上限、来源编号一致性、上下文保留和原生搜索路由。
- 29 项相关设备回归通过，包含新增真实服务链路测试、内置搜索、取消、思考显示、后台生成和会话逻辑。
- 扩展运行 InteractionRegressionTest 时另外 5 项通过；actualMarkdownTouchOpensCapturedBrowserAndCopyUsesRawCode 长按链接断言失败，单独执行亦失败。证据：SelectableSpanTouch.java:15 长按 UP 返回 false 后，MarkdownRenderer.java:123-125 设置的 LinkMovementMethod 仍可执行链接。该生产代码未在本次修改。
- 旧 HTTP 模拟测试将 Content-Length 字节数当作字符数，加入中文策略后超时；测试夹具已改为按 UTF-8 字节读取。
- Debug、AndroidTest、Release（未签名）构建和 lintDebug 通过。
- 所有联网验证使用模拟搜索与模型或本地 HTTP 响应，没有调用收费模型。测试能证明请求结构、数据隔离和预算约束，不能保证任何模型永远不受恶意网页影响；实际回答质量仍需真实模型样例验证。
