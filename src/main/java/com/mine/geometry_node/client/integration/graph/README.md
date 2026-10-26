# 图文档宿主装配

`GraphDocuments` 是本目录唯一入口，将 `feature/graph/document` 的文档能力与实际宿主连接：

- `loader()` 注入图编辑器真实 `FrameBoundsCalculator`，避免功能内核依赖 View 或使用简化尺寸替代实际策略。
- `openLocal()` 使用调用者的 `AssetTaskController`，复用已打开文档；排队加载期间发生并发注册时同样优先复用已有文档，保留未保存修改。
- `openController()` 给共享远程开图控制器提供当前连接、本地草稿定位、IO 调度、下载和 UI 分派；目标选择仍由调用者提供。

文档集合、claim、dirty、保存和关闭归 `feature/graph/document`。`ui/document/DocumentManager` 负责标签激活；每视口相机/选择不在本目录维护。宿主销毁须关闭其 open controller；过时下载允许完成到磁盘，但不得打开旧目标。不要为每种开图入口复制下载、解析和注册流程。
