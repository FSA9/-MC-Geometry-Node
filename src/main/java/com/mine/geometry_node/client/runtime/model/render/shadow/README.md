# 模型的太阳阴影路径

`ModelShadowDistancePolicy` 控制模型太阳阴影与 caster 距离，独立于 BVH 局部灯光。`ModelShadowCulling` 只有在边界完全包含等确定条件下才跳过常规检查，不确定时保留正常 cluster 剔除。

`ModelShadowTessellation` 在 worker 上静态细分，用较短边缓解非线性 shadow warp 的光栅误差。SEUS 路径由 `ModelSeusShadowDepth/Payload/Voxel` 处理解析覆盖、深度、输入宽度及已知 voxel 编码限制；Sundial 路径由 `ModelSundialShadowDepth/Raster/Uv/Coverage` 处理对数投影、UV sentinel 和 MASK 像素覆盖。

`ModelIrisShadowUv` 与 `ModelShadowUniformUploads` 只在真实模型 shadow draw 的作用域内补齐 uniform/UV，不能影响其他宿主 draw。GL 状态和上传属于渲染线程，必须在宿主 shader setup 之后应用并按作用域恢复；细分与几何准备不在此阶段重复执行。

资源由模型资产及 `ModelStaticRenderer` 的 shadow 生命周期拥有，resource reload、Iris pipeline 变化、世界清理通过 `closeShadowResources` 等现有边界释放。新增光影包实现要保留未知程序的完整重建/回退路径，不能让已知版本优化进入未验证源码。
