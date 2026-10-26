# HUD 展示

本目录负责游戏画面上的轻量绘制，读取 `client/runtime` 快照，不创建工作区 Area，也不拥有世界业务状态。

| 子目录 | 关键文件与职责 |
| --- | --- |
| `marker` | `MarkerHudRenderer` 将 `runtime/marker/ClientMarkerStore` 的标记快照投影到屏幕；`MarkerRenderContext` 封装绘制输入；`ClientMarkerRenderer` 是绘制协议，Registry 按 `MarkerType.rendererId()` 选择实现，`DefaultMarkerRenderer` 提供默认图形。 |
| `model` | `ModelHud` 展示模型服务、LOD、光照等诊断信息，读取 `runtime/model` 的状态与指标；可见性/诊断状态仍归 `ModelHudState` 等运行时 owner。 |

绘制发生在游戏 HUD 的渲染回调中，使用已发布状态和提供的图形上下文；这里不修改 MUI View 树，不阻塞等待网络/文件 IO，不释放运行时的模型缓存、纹理或世界数据。HUD 主题取 `UiThemes.HUD`；内容/标记类型颜色保留其业务含义。

新增标记视觉实现并注册到 `ClientMarkerRendererRegistry`；类型定义留在 core。新增模型诊断展示放 `model`，采样和累计指标放 runtime。工作区可复用的标记图标/提示预览进入 `components/integration/valuepreview`。
