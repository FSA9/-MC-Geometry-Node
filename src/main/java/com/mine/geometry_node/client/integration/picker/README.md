# 游戏原生选择会话

| 文件 | 职责 |
| --- | --- |
| `VanillaInventoryPicker` | 临时打开原版容器 Screen，返回物品或槽引用；负责恢复先前 Screen、确认/取消与一次性回调。 |
| `EntityTemplatePickerController` | 临时回到世界选择实体并请求模板快照；关联响应请求，取消/断线时恢复宿主并隔离旧响应。 |
| `DataLibraryEntityPickerController` | 将实体模板捕获与 UUID 配对，提供数据仓库的实体引用选择流程。 |

这些是游戏输入/Screen 与可复用 UI 的具体连接，放在 integration 而非 Area 或 feature 中。选择会话不是长期实体登记容器；实体索引归 `feature/entity/index`。新选择方式须明确输入拦截、恢复屏幕、取消以及网络晚到回调，不额外保存另一份选中业务对象。
