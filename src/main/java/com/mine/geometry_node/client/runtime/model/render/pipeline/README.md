# 提交、透明排序与材质状态

`ModelStaticRenderer` 是模型绘制编排入口，使用 `ModelRenderInstanceSource` 取得必要的服务快照。主 pass 的实例仍显式传入，生命周期负责连接 source；renderer 不是第二个模型实例仓库。

不透明/MASK 路径使用驻留索引缓冲和材质批次；`ModelStaticPipelines` 为原版 fallback、驻留后端及 Iris 桥接提供有界变体。`drawResidentOpaque` 在可用的非 GPU pass 阶段建立模型深度，透明绘制分别位于普通透明阶段及需要时 Iris 后处理结束后的阶段，不能随意合并。

`ModelTransparentScene` / `ModelTransparentBsp` 在 worker 上准备资产局部透明几何与静态 BSP，跨节点边界维持几何整体；`ModelTransparentOrder` 和 `ModelTransparentRenderer` 复用驻留顶点、更新视点相关索引顺序。排序不替换材质着色，不在排序时重新照明。

Iris 材质和临时 GL 修改分开：

- `ModelIrisAlphaPolicy/Source` 保留已观测的 alpha 约定；延迟 collector 必须把材质约定保持到真实 draw，不能只在提交前改一次全局值。
- `ModelIrisAlphaTest`、`ModelIrisBlendOverride`、`ModelOpaqueBlendOverride`、`ModelIndexedBlendOverride` 和 integer/alpha/mask uniform override 在宿主完成 setup 后作用于真实绘制，并在异常时恢复原状态。
- `ModelIrisGeometryLayout/Tangents` 提供 Iris 顶点布局与切线约定转换，不让可选 Iris 类型成为公共资产依赖。
- `ModelSundialMaterialSampling/MaskCoverage` 只修正已知 Sundial 的独立模型 sampler 和 MASK 路径，保留宿主几何的采样语义。

GL 状态、resident draw 和 program 观测属于渲染线程；CPU 透明准备属于 worker。新增状态 override 必须恢复实际 GL 值，不能假定宿主缓存会替自己恢复；程序/世界变化沿 `refreshIris`、`pollIrisLifecycle`、`clearSceneResources` 和 `closeShadowResources` 清理，查询缓存清理不等同于资产驱逐。
