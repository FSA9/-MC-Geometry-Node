# 模型绘制后端导航

服务提供不可变帧输入，`ModelStaticRenderer` 组织实际提交和 resident draw；CPU 准备与 GPU 生命周期分离。此目录可使用 Minecraft 渲染器及 OpenGL，不创建 ModernUI 对象。

| 子树 | 说明 |
| --- | --- |
| [`pipeline`](pipeline/README.md) | 主/透明/阴影提交、Iris 材质语义及绘制作用域的状态恢复。 |
| `geometry` | `ModelRenderAsset`/`ModelRenderInstance` 与下述源几何准备、批次和上传范围。 |
| [`resource`](resource/README.md) | 纹理、sampler、上传预算、驻留账本与 GPU fence。 |
| [`bvh`](bvh/README.md) | 资产局部遮挡、空间光样本、实例受光设置及已知光影包适配。 |
| [`shadow`](shadow/README.md) | 太阳阴影策略、非线性投影和 MASK 覆盖，不等同于局部光遮挡。 |
| [`lod`](lod/README.md) | 活动的逐模型对象 LOD，以及明确排除构建的归档 cluster 实验。 |
| `diagnostic` | `ModelRenderMetrics` 的 CPU 提交计数，`ModelDrawGpuTimings` / `ModelShaderGpuTimings` 的非阻塞 GPU 时间戳。 |

## geometry 内的准备与借用

`ModelRenderAsset` 连接不可变 CPU preparation 与渲染线程拥有的纹理、驻留索引几何；`ModelRenderInstance` 是一帧的输入，资产 lease 仍由模型服务持有。worker 负责 `ModelOpaqueBatches` 合并相邻同材质不透明几何、`ModelLightPartitions` 产生有界采样单元、`ModelPackSmoothNormals` 准备已知光影包所需法线。

`ModelClusterBuilder`/`ModelClusterAsset` 只建立引用源索引范围的剔除 metadata，不复制/简化顶点，也不是归档的 CPU cluster LOD。`ModelGeometryChunks` 与 `ModelGeometryUploadRanges` 划分有界上传和已驻留前缀；chunk 数量不规定最终 draw 数量。`ModelCullingBounds` 和 `ModelQualityDistance` 在帧内按正确坐标空间做可见性与整模型距离判断。

## 指标也有生命周期

CPU 提交计数不代表 GPU 执行时间或整个游戏帧成本。GPU timing 按批次或受控 shader draw 插入查询，异步 poll；clear samples 与释放 query 对象分别由已有生命周期管理。报告应注明范围，缺少观测不能填成零成本。

外部扩展通常从 `ClientModelService` 的实例入口、`ModelRenderInstanceSource` 注入契约，或有签名保护的受光/阴影适配入手。不要旁路服务重新持有资产，也不要在 worker 上调用 renderer 的 GL 入口。
