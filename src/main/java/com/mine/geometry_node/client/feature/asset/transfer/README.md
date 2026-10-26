# 客户端资产传输

本目录负责上传下载作业的执行状态。文件选择、冲突确认、进度面板和下载后是否开图由外层装配，不进入传输服务。

| 文件 | 职责 |
| --- | --- |
| [ClientAssetTransferRequest.java](ClientAssetTransferRequest.java) | 一项传输的方向、本地路径、远端路径、冲突策略和 purpose；构造时校验、规范化输入。 |
| [ClientAssetTransferPlanState.java](ClientAssetTransferPlanState.java) | 跟踪提交前的远端计划请求，提供 request、handle、cancel、reset；计划请求与实际作业是两种生命周期。 |
| [ClientAssetTransferService.java](ClientAssetTransferService.java) | 提交作业、串行调度文件、处理回执、本地 I/O 与下载提交；发布活动作业和有界成功、失败历史，支持显式重试。 |

## 使用与生命周期

1. 调用方构造非空、方向相同的请求列表，必要时先取得远端计划并完成冲突选择，再调用 `submit()` 获得作业 UUID；同一作业不能混合上传与下载。
2. `subscribe()` 观察全局快照；`onCompletion(jobId, ...)` 观察指定作业的完成。回调经客户端线程投递，UI 自行切线程并检查自己的有效代次。
3. 服务按队列执行单个活动文件，每个文件使用一个完整网络 payload。下载收到内容后进入本地提交阶段；完成状态应结合文件结果与 failure 判断，不能仅以回调触发代表成功。
4. 关闭快照订阅只取消观察，不撤回已经提交的作业。服务没有对外提供作业取消接口；退出窗口不等于中止文件传输。

连接重置时，`resetConnection()` 将未终结文件标记为断线失败并结算旧作业，清除请求索引、重试记录和历史，发布空快照。应用级 `close()` 再关闭执行资源。成功和失败历史各自受数量限制；清理失败历史也移除相应重试请求。

## 依赖与扩展入口

传输服务依赖 core 的传输模型、I/O 与网络协议，以及客户端线程适配，不依赖图编辑器。新用途应在 core 的 `AssetTransferPurpose` 和服务端处理链路中定义，随后构造专用请求；不要用远端路径文本代替 purpose 授权语义。

`feature.datalibrary` 复用本服务传输服务端权威的数据字典 JSON，使用固定路径和专用 purpose。资产 Area 的计划、冲突对话框和完成后动作由 `integration.asset.AssetBrowserTransferController` 装配。新增调用方应分别管理自己的计划请求、临时文件和 UI 订阅，保持完成后的资源清理不依赖窗口仍然可见。
