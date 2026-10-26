# 声明式图补丁契约

本目录定义 AI 图修改的输入语言与局部验证，不持有 session、工作区或历史，也不执行提交。依赖 `core.node` 的节点/端口定义和 Gson；事务实现位于 [integration.ai.graph](../../../integration/ai/graph)。

| 文件 | 职责 |
| --- | --- |
| [GraphPatch.java](GraphPatch.java) | 补丁的 session、固定 scope、期望 revision、幂等键、操作列表及节点/端口/frame/动态分支引用。最多 256 个操作；位置须为有限数，具体 ID 与 alias 引用互斥。 |
| [GraphPatchCodec.java](GraphPatchCodec.java) | 按 `op` 显式选择操作类型并编解码 JSON，避免依赖 Gson 对 sealed 接口的隐式反射。 |
| [GraphPatchContractValidator.java](GraphPatchContractValidator.java) | 在接触活动图之前检查引用顺序和 alias 所有权：引用只能使用前序操作已声明的 alias，节点与 frame 使用各自命名空间。 |
| [GraphPatchJsonLimits.java](GraphPatchJsonLimits.java) | 对字符串形式的补丁进行解析前边界检查，限制嵌套深度和字符串长度。 |
| [InputValueCodec.java](InputValueCodec.java) | 保守地将 JSON 转为端口存储值；验证精确整数、有限浮点、三维向量和数值上下界。`ANY` 不推断数字存储类型。 |
| [PortEditCapabilityResolver.java](PortEditCapabilityResolver.java) | 根据端口类型和 `UIHint` 判定是否允许补丁写入；按钮、物品槽、实体模板及自定义控件等能力默认拒绝。 |

## 支持的操作与引用

操作包含节点增删/移动、连线增删、输入值及 SELECT 值、节点属性、frame 增删/属性、动态分支、组虚拟端口和端口重命名。`SetSelectValue` 使用稳定 `optionId`，显示标签不能替代真实值；上下文令牌和旧值比较交由事务适配继续验证。

新对象可在同一补丁中先声明 alias，再由后续操作引用；生成端口和动态分支也有明确引用形式。`ScopeRef` 绑定规划时的组作用域，之后切换可见标签或组不能让请求自动改投其他目标。

## 与提交层的边界

调用顺序为限制检查、codec、契约验证，再进入图相关的目标绑定、规划和编译。session 是否存在、scope 是否仍有效、revision/旧值是否相等、端口是否连接以及幂等键是否冲突，必须由 `GraphPatchTransactionService` 等适配检查。结构合法不等于可以提交。

新增操作时同时更新 sealed 操作集合、codec、alias 验证、工具说明和 integration 规划/提交逻辑。修改值类型时同时维护能力判定与 codec，防止某条入口绕过端口语义。JSON 字段和 `op` 字符串属于对外协议，不能随类名改动。
