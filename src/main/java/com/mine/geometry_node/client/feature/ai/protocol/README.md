# 提供商中立协议与工具契约

这里是不可变协议值和校验规则，主要依赖 JDK 与 Gson，不依赖 Minecraft 窗口、图文档、HTTP 服务或具体模型 SDK。

| 文件 | 职责 |
| --- | --- |
| [AiProtocol.java](AiProtocol.java) | 消息角色与内容、工具调用/结果、turn、usage、continuation、错误及流事件。集合做不可变拷贝，可变 JSON 在构造和访问边界复制，防止调用方改写协议状态。 |
| [ProviderCapabilities.java](ProviderCapabilities.java) | 显式记录聊天、流式、工具、并行工具、强制/严格工具和 reasoning 的支持状态；未声明项为 `UNKNOWN`，只有 `SUPPORTED` 才算支持。 |
| [ToolContract.java](ToolContract.java) | 工具定义关联的效果、风险、权限模式及授权判定，供上层接入策略使用。这里只提供判定，不创建确认框或管理工作区。 |
| [ToolSchemaValidator.java](ToolSchemaValidator.java) | 对外统一入口，返回带路径、代码与说明的 schema/参数校验诊断。 |
| [ToolSchemaDefinitionValidator.java](ToolSchemaDefinitionValidator.java) | 包内实现：检查所支持的 JSON Schema 词汇及定义结构。 |
| [ToolArgumentValidator.java](ToolArgumentValidator.java) | 包内实现：根据已验证的 schema 检查参数值。 |

命令定义先经过 schema 定义校验，具体调用再做参数值校验。这里实现的是项目支持的严格公共子集；增加新关键字时必须同时考虑定义与参数校验，以及所有调用方的投影能力，不能直接假设支持完整 JSON Schema。

流事件是数据契约；组装 turn、调度工具和呈现文本属于使用方。`ReasoningDelta` 不应被界面当作可展示的隐藏思维链。工具名、错误码和字段形状是协议兼容边界，增加或修改时检查命令目录、MCP 映射与客户端适配。
