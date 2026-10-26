# 资产工作区装配

共享浏览器在 `ui/components/integration/asset`，资产仓库和文件服务在 `feature/asset`；这里将它们连接到实际工作区文档和 Area。

| 文件 | 职责 |
| --- | --- |
| `AssetBrowserRepositories` | 启动时注入图 TAG 读取器，避免通用资产仓库反向依赖图文档。须在使用仓库前初始化。 |
| `AssetCacheRegistration` | 统一注册功能缓存和 UI 缩略图缓存，不依赖某个 Area 的存在。 |
| `WorkspaceAssetBrowserBindings` | 为资产 Area 创建属性、文档动作和传输适配。 |
| `WorkspaceAssetBrowserActions` | 实现共享浏览器的窄文档动作协议，复用 `integration/graph/GraphDocuments`。 |
| `AssetFilePropertiesTarget` | 将选中的本地图文件适配为公共图属性目标；文件 IO 与 UI 成功通知分别调度。 |
| `AssetBrowserTransferController` | 上传/下载预检、覆盖决策、新图临时文件和传输完成接线。 |

控制器随 Area 失效时取消其请求/回调，已提交的文件操作不因此撤销。文档引用刷新和缓存维护在独立 UI 执行器运行；只有刷新旧窗口的部分受 owner 代次限制。新图临时文件是 AutoCloseable 任务结果，回调被丢弃也须清理；提交给传输服务后由传输完成回调清理。
