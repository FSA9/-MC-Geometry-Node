# 实体编辑的工作区连接

`WorkspaceEntityEditor` 为可复用实体编辑弹窗注入 `EntityGraphActions`：从公共服务端资产选择器选择图，复用绑定协议，并在工作区已有图编辑器打开图。它不再创建一套专用图编辑弹窗。

实际控件在 `ui/components/integration/entity/editor`，实时提交与拒绝保护在 `feature/entity/edit`，实体列表/登记状态在 `feature/entity/index`，外观预览在 `feature/entity/preview`。这些状态不会由本适配器复制。新增宿主应实现组件提供的能力接口，不让组件反向查找 MainUI 或具体 Area。
