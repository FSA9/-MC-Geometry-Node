# 全局数据 UI

`GlobalDataView` 是数据库的分类宿主，顺序为实体、阵营、数据；分类实现
`GlobalDataSection`，不注册独立窗口。

`ScopedDataCatalogView` 按 `docs/ui/prototypes/database-entity-preview.html?page=global&globalCategory=data`
组织左右布局。左侧按全局 / 维度分组，只使用服务端目录返回的维度，支持主动提交名称或 ID 搜索。
导航行按 target 保留，回执只更新原行的数量和选中样式；未读取的数据不显示 0。
原版维度显示本地化名称，自定义维度尝试维度翻译键，否则显示完整 ID。
维度不提供标签、创建或删除操作。

右侧展示目标名称和维度 ID，借用 `ScopedDataDictionaryBinding` 和公共
`UiDataDictionaryEditor.createEmbedded(..., CommitMode.ON_FOCUS_LOSS)`。
字典搜索、新增、多选删除、分页、嵌套编辑和提交均由公共编辑器负责；没有父级 Apply 或离页确认。
读取未完成或失败时显示明确状态，首次读取成功前不挂载空字典。

每个已访问范围保留一个 editor，草稿、筛选、分页和滚动随实例保留。切换范围和隐藏分类先
`suspend()` 再隐藏；回到范围 `resume()`，不主动提交或清空未完表单。隐藏只移除目录展示监听，
不会关闭 session；最终销毁才 dispose editors 并关闭 session。已接受命令的完成由仓库和公共编辑器负责，
UI 不取消写入。

`onShow()` 显式强制刷新目录及选中目标，切换范围显式 `read(target, false)`。
快照监听不重复刷新目录或 reload；仅在目录确认选中维度已移除时先回退 GLOBAL，再确保读取一次，避免通知重入。
读取失败时提供重试按钮，重新请求目录与当前数据；正常状态不占据额外反馈行。
binding 同时捕获原 `UiStateScopes.current()`、repository generation 和 session 身份，
旧连接的控件不能写入新连接；scope / generation 改变会释放旧编辑器和 session。

`ScopedDataViewState.PAGE` 使用当前连接 scope 的 MEMORY 保存选中目标、已提交目录搜索与目录滚动，
`SPLIT` 使用 GLOBAL 的 PERSISTENT 保存目录宽度。业务数据、协议、CAS 和权限均不放在 editor 包。

相邻阵营实现见 `FactionCatalogView` / `FactionEditorView`，公共实体目录由 `EntityCatalogView` 复用。
生命周期、未知数量、稳定导航行、草稿保留与旧连接回调由该包对应 UI 测试覆盖。
