# 资产遮挡与局部受光

本目录围绕完整源几何的遮挡与有界光采样组织，不能把距离降级后的显示 LOD 直接当成唯一遮挡几何。局部灯光开关也不控制太阳阴影，后者在 [shadow](../shadow/README.md)。

| 类组 | 数据归属 |
| --- | --- |
| `ModelLightOcclusionBvh/Scene`、`ModelOcclusionAlpha` | worker 准备资产局部、可感知 alpha 的遮挡数据；实例共享该数据，查询把线段两端变换到资产坐标。alpha 近似不在渲染线程读纹理或细分。 |
| `ModelLightSamples`、`ModelLightFieldLayout`、`ModelSpatialLightCache` | 有界光值与空间 tile 计划、世界亮度失效；不枚举整个大建筑或强制加载区块。 |
| `ModelPlacedLightField/Volume`、`ModelLocalLightProviders`、`ModelHeldLightPosition` | 放置光源/手持光的位置与提供者；材质 emissive 颜色本身不自动注册世界光源。 |
| `ModelLightingSettings/Quality`、`ModelBvhAutoQuality` | 全局默认与按实例 ID 的 receiver 设置；自动质量不能把实例从遮挡 atlas 中删除。 |
| `ModelIrisHeldLight`、`ModelNativeLight`、`ModelResidentLight` | Iris/native 受光资源和实际 draw 绑定；静态顶点可保持驻留，原版 UV2 按绘制补充。 |
| `ModelBvhLightShader/Resolution`、`ModelPackLightAdapters` | 已知光影包局部光 prepass 和同帧分辨率策略，保留原 composite 的 GI/太阳光路径。 |
| `ModelSeusPlacedLight/ProgramRegistry`、`ModelSundialHeldLight/PlacedLight`、`ModelLightingReceiverTag` | 指定包的 emitter、局部光和 receiver 标记适配；未知源码保持原样。 |
| `ModelLightingProfiler` | 渲染线程 A/B 采样，报告范围不等于整个世界光照成本。 |

CPU 遮挡树和 alpha preparation 可在导入 worker 构建；世界亮度、每帧 receiver 选择、GPU atlas/program/uniform 操作属于渲染线程。设置按实例 ID 存储，不能以共享 asset identity 替代，否则一个实例的覆盖会污染其他实例。

实例移除时由模型服务删除对应设置、附着光源；世界/资源变化由 `ModelStaticRenderer` 及模型生命周期清理场景与受光派生资源。shaderpack 的程序只按相应适配所有权借用/注册，`ModelSeusProgramRegistry` 不保留 Iris pipeline 对象。扩展 receiver 时明确哪些数据随资产共享、哪些逐实例、哪些逐世界，不把它们全部塞进静态 GPU 缓存。
