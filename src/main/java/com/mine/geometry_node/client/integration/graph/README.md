# 图文档宿主装配

`GraphDocuments` 将 `feature/graph/document` 的文档能力与实际宿主连接：

- `loader()` 注入图编辑器真实 `FrameBoundsCalculator`，避免功能内核依赖 View 或使用简化尺寸替代实际策略。
- `openLocal()` 使用调用者的 `AssetTaskController`，复用已打开文档；排队加载期间发生并发注册时同样优先复用已有文档，保留未保存修改。
- `openController()` 给共享远程开图控制器提供当前连接、本地草稿定位、IO 调度、下载和 UI 分派；目标选择仍由调用者提供。

`GraphSessionPropertiesTarget` 将已打开图会话接到 `ui/components/integration/graph/properties` 的公共属性协议，元数据编辑继续经过 `CmdSetGraphMetadata` 和原历史机制。资产文件适配也复用该入口，不依赖图编辑器 Area。

`GraphEditorPreviews` 从领域模型构造本地对话、商店和任务预览，分别供图节点提示和共享属性面板使用，调用 runtime 的既有预览入口；不执行图业务流程，不持有 Area 或 View。

文档集合、claim、dirty、保存和关闭归 `feature/graph/document`。`ui/document/DocumentManager` 负责标签激活；每视口相机/选择不在本目录维护。宿主销毁须关闭其 open controller；过时下载允许完成到磁盘，但不得打开旧目标。不要为每种开图入口复制下载、解析和注册流程。

`GraphAssetImports` 为拖入现有画布的资产读取 JSON：本地读盘和下载后的读盘在后台执行，临时下载文件无论成功、失败或目标消失均独立清理。它不创建文档、不写图、不持有 View；调用者回到 UI 线程后检查 owner、会话与图 scope，并使用拖入时捕获的画布坐标执行原粘贴命令。
