# 活动模型 LOD 与归档实验

当前可执行实现只在 `model/`；[`cluster/`](cluster/README.md) 是被 Gradle main/test source set 排除的历史 CPU 层级实验。渲染中仍然使用的源索引 cluster 剔除 metadata 位于 `render/geometry`，不属于这个归档实验。

`ModelLodPreparation` 在 worker 上做缓存和场景装配；`ModelLodSimplifier` 执行保留源顶点与开放边界的属性感知简化。`ModelLodMesh` 的 level 主要保存索引，借用原 preparation 的只读顶点/索引或拥有缓存载荷；顶点存储和 GPU 所有权仍属于资产。

`ModelLodObject` 对应带 mesh 的 glTF node。`ModelLodPolicy` 按对象实例距离选取，`ModelObjectLodSelection` 保持同一帧主 pass 与 shadow 的 requested/effective 选择一致；`ModelLodResidency` 在目标级别上传时选择完整可用级别，不混拼同一模型的不同 primitive 等级。

`ModelLodCache` 在游戏目录 `geometry_nodes/caches/model` 保存内容寻址的 `.mlod` 文件；只有受管文件参与统计和清理。它使用公共磁盘访问契约隔离清理与旧写入，校验/生成属于准备 worker，GPU 上载属于渲染队列。`ModelLodMetrics` 区分模型、对象和各 pass 的计数，不能把 source triangle 当成 selected triangle。

新增质量策略应接在活动 policy/selection 上，保留完整级别回退和帧间资源保护；恢复归档实验需单独审查成本与所有权，不能只解除 exclude 或恢复 import。
