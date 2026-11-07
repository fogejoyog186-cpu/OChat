# 思考配色、默认预算、错误诊断与文件选择

日期：2026-11-04

本文记录当日的实现与验证结果，不代表当前版本的完整测试状态。构建方法见 [README](../README.md#构建与测试)；下述 APK、报告和截图为本地生成产物，不随源码提交。

## 本次修改

- 动画配色入口位于“设置 → 主题设置”，提供青色、GPT 蓝色、GPT 紫色、自定义纯色。蓝色为 #0285FF、紫色为 #9859EF；普通档不使用蓝紫渐变。自定义支持 HEX 和 RGB；沿用现有粒子与位移动画。配色草稿随主题页“保存并重启”一起保存，取消或返回不保存，旋转屏幕后保留草稿。旧 gpt 渐变设置自动解释为纯蓝色。
- 面板和主页仅显示英文档位，例如 low、xhigh、Auto；主页强度入口去掉重复的中文前缀，强度面板不再显示配色设置。实际请求仍由各协议适配器生成。
- 修复 ReasoningSheet.java 中误写为 1values.length 的编译错误。
- 新模型默认上下文窗口为 128000、输出上限为 1000000，缺失配置的回退值同步更新；保留现有显式设置。
- 输出上限是请求的最大预算。发送前估算输入量并保留安全余量，实际输出不超过剩余上下文。长历史保留最新一轮与系统/搜索指令，必要时省略较早轮次。模型自身的输出硬限制仍以供应商文档和模型配置为准；应用默认值不代表所有模型支持百万输出。
- HTTP 错误保留状态码、服务端 message / code / type / param、请求 ID，并显示实际请求中的模型、协议、token、思考、采样等参数。诊断不序列化输入消息、文件内容或请求头，密钥脱敏；错误正文读取和展示均有长度限制。
- 覆盖普通响应、SSE 中途错误、Responses response.failed、原生 Gemini 字段校验信息、非 JSON 网关错误、空流、请求流式却返回 JSON 等情况。部分回复中断后的原因也保存在消息详情。
- 添加附件仅保留“图片 / 文件”两个入口。新的“文件”直接通过 ACTION_GET_CONTENT chooser 选择系统文件应用或支持该协议的第三方管理器（如相应版本的 MT）；删除重复的文档入口。图片入口保留系统照片选择器。
- 对只有临时读取授权的 URI，在后台复制到应用私有附件目录，保留原文件名和 MIME；持久授权则继续使用原 URI。导入支持多选、去重、数量和大小限制，并报告具体失败原因。缺失 MIME 时根据扩展名补全；不申请所有文件访问权限。

## 验证

- :app:assembleDebug、:app:assembleDebugAndroidTest、:app:assembleRelease、:app:lintDebug 成功。
- 79 项 JVM 单元测试通过，包括本地 HTTP 服务模拟 400 参数错误、SSE 中途错误、错误 JSON、网关 HTML、空流、密钥脱敏与大输出预算。
- Android 17 模拟器 44 项设备回归通过，无跳过。覆盖仅两项附件入口、纯英文档位、强度面板无配色入口、主题配色确认/返回/旋转草稿、旧渐变配置迁移、临时文档原件删除后仍能读取私有副本、选择高亮、模型切换、后台生成和既有主要流程。两处界面截图均已目视检查。
- 本地界面截图：`app/build/reports/reasoning-appearance.png`（思考强度与英文档位）、`app/build/reports/reasoning-theme.png`（主题页配色）。
- 未使用真实服务账号调用，也未在安装 MT 的实体设备上联调；第三方入口取决于对应应用是否声明标准文件选择能力。

## 参考

- [OpenAI Docs：模型与思考控制](https://learn.chatgpt.com/docs/models?surface=web)及其[官方 Power 组件 CSS](https://learn.chatgpt.com/_astro/models.DSHQFdV0.css)、[组件代码](https://learn.chatgpt.com/_astro/CodexModelSwitcher.react.Cp0JVA4b.js)。已读取实际公开资源：普通 powerRange 背景是 #0285FF；只有 data-ultra=true 才使用蓝紫渐变，末端 #9859EF。本应用将蓝、紫作为独立纯色预设，修正先前把渐变用于普通档的实现。
- [Android：WindowInspector](https://developer.android.com/reference/android/view/inspector/WindowInspector#getGlobalWindowViews())。设备测试使用公开窗口检查接口和原生控件操作，避免旧 Espresso 在 Android 17 输入接口上的兼容问题。

## 产物

- Debug APK：`app/build/outputs/apk/debug/app-debug.apk`
- Release APK（未签名）：`app/build/outputs/apk/release/app-release-unsigned.apk`
