# 资产目录仓库

这里把本地目录、收藏和远端目录转换为公共浏览结果。导航历史、选择、排序控件和刷新代次由调用方维护。

## 读取协议

`AssetRepository.browse(AssetBrowseRequest, onResult)` 返回可取消的 `AssetRequest`，结果按接口约定送到 Minecraft 客户端线程。UI 仍需切换到自己的线程，并在投递后检查视图是否已销毁、位置或请求代次是否已变化。

| 文件 | 职责 |
| --- | --- |
| [AssetLocation.java](AssetLocation.java) | `Local` 表达目录或收藏路径快照；`Remote` 规范化远端目录，并携带是否允许创建目录。 |
| [AssetQuery.java](AssetQuery.java) | 规范化名称搜索和标签词，`includeTags` 控制是否附带标签数据。 |
| [AssetListing.java](AssetListing.java) | 返回成功状态、实际位置、资产条目和按条目键索引的标签；读取失败与成功的空列表是不同结果。 |
| [AssetRepositoryOperation.java](AssetRepositoryOperation.java) | 描述浏览、创建、上传、下载、管理能力；能力检查不替代服务端授权。 |
| [AssetRepositoryRegistry.java](AssetRepositoryRegistry.java) | 按 `AssetSourceKind` 注册仓库，拒绝同一来源重复注册；默认安装本地和远端实现。 |

## 两种实现的行为

`LocalAssetRepository` 使用共享的单线程守护执行器扫描文件，取消时中断任务并阻止已排队结果投递。普通浏览读取当前层；名称或标签搜索递归查找并跳过符号链接；收藏浏览使用请求中的路径快照。类型可见性、来源能力和收藏能力仍参与筛选。

本地图标签经 `TagReader` 读取。无参仓库在读取时委托注册表中当前安装的 reader；默认 reader 返回空标签，工作区必须在首次浏览前通过 `integration.asset.AssetBrowserRepositories.init()` 安装真实读取器。测试或独立调用方可直接构造 `LocalAssetRepository(TagReader)`。图标签搜索保留多个标签词均须匹配的语义。

`RemoteAssetRepository` 委托 `RemoteAssetClient` 请求目录，按最新权限能力报告 `supports()`，并筛选名称、类型与来源。当前远端实现不提供标签搜索，带标签条件时返回成功的空结果；不能将它描述成本地递归搜索的远端版本。取消会撤销请求跟踪并阻止后续常规回调。

## 扩展与验证入口

新增来源需要一起扩展 `AssetSourceKind`、受封闭接口约束的 `AssetLocation` 及对应仓库，再注册到 `AssetRepositoryRegistry`；只增加过滤条件时优先扩展查询和现有实现。仓库只依赖资产模型、路径和远端协议，不应知道 picker、Area 或文档打开器。

标签行为的现有回归入口是 `src/test/java/com/mine/geometry_node/client/feature/asset/repository/AssetTagQueryTest.java`；公共 UI 的边界与过期结果保护测试位于对应的 `client/ui/components/integration/asset` 测试树。
