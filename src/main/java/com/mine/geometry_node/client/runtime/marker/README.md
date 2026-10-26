# 世界标记的数据与位置采样

此包只包含 `ClientMarkerStore`。它按 `MarkerAddress` 接收服务端 snapshot/upsert/remove，维护目标位置、上一位置和更新时间；不拥有 renderer 注册表或图标控件。

`renderSnapshot(Minecraft, partialTick)` 返回当前维度中活动标记的只读 `RenderSnapshot(payload, position)` 列表。实体锚点优先取仍存在且存活的实体眼睛位置；实体不可用时回退到远端坐标插值，插值窗口为 100 ms。插值和实体解析留在 runtime，屏幕投影、边缘箭头、距离标签归 `ui/hud/marker`。

集合更新和采样均使用同步方法；实体与世界访问仍需在 Minecraft 客户端/渲染调用链内执行，锁不提供跨线程访问世界的授权。logout 由 `GeometryNodeClient` 调用 `clear()`，清掉当前连接的数据；本 store 不拥有纹理或 GPU 对象。

扩展有两个明确入口：公共侧通过 `api.MarkerRegistrationContext` 注册 `MarkerType`；客户端初始化通过 `ui.hud.marker.ClientMarkerRendererRegistry` 绑定 `rendererId`。同一个 `ClientMarkerRenderer` 同时提供 HUD `render` 与可覆盖的 `createPreview`，默认预览仍是位置图标；不要为编辑器再建立另一套预览注册表。
