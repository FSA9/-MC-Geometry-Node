# 资产预览回调与生命周期

`LocalAssetPreviewProvider` 通过 `ClientAssetCallbacks` 在 Minecraft 客户端线程交付结果；`RemoteAssetPreviewProvider` 沿用 `ClientAssetPreviewService` 的同一回调契约。

`AssetPreviewView` 的订阅、挂载和卸载由 MUI UI 线程管理。provider 回调只调用 `View.post()`，在 UI 队列执行时校验订阅 generation，再创建缩略图、增删子视图和切换 fallback 可见性。不能在 provider 回调线程读取 generation 后直接修改视图树。

`preload()` 可以在挂载前订阅，结果由 View 的待挂载队列保留；挂载不重复订阅。detach 先递增 generation、关闭订阅，再移除缩略图并恢复 fallback。已入队或取消后才到达的旧结果因此失效，重新挂载会创建新一代订阅。

回归测试见 `src/test/java/com/mine/geometry_node/client/ui/editor/asset/preview/AssetPreviewViewTest.java`：使用真实 MUI Looper 和 HeadlessRoot，覆盖跨线程交付、队列执行前的 detach、重新订阅、取消后的迟到回调，以及挂载前 preload。

此修改修复冻结调查中确认的线程契约违规；不据此认定本次冻结的根因或冻结问题已经解决。
