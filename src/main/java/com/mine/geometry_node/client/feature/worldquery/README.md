# 客户端世界对象查询会话

本包目前只有 [ClientWorldObjectRepository.java](ClientWorldObjectRepository.java)，为世界对象列表、标签编辑和自定义数据编辑提供连接内查询会话。对象类型、查询、结果页及编辑规则来自 `core.engine.system.worldquery`；具体表格、选择与属性面板由 UI 管理。

## 结构与依赖方向

| 入口 | 职责 |
| --- | --- |
| `ClientWorldObjectRepository.INSTANCE` | 跟踪所有活动 Session、查询与修改请求，适配客户端线程和连接代次。 |
| `newSession()` / `Session` | 每个使用者独立拥有查询位置、结果快照、监听、标签请求及按需数据槽。 |
| `Snapshot` | 发布查询、结果页、loading 和 sequence，保留覆盖范围、截断、类型和维度等服务端信息。 |
| `DataSnapshot` | 发布字典值及 fetched、available、editable、loading、pending、message，区分未读取、不可用和正在保存。 |

依赖方向为 `ui/editor/datalibrary 等消费方 → 本包 → core.worldquery / network、Minecraft / NeoForge`。跨线程消费发布的快照，传输和变更通知在 Minecraft 客户端线程执行；UI 自行转到所属线程并检查生命周期。

## 查询与关闭

`init()` 幂等注册标签和数据回执、tick 超时与断线监听；查询结果页的回执由 `ClientNetworkReceiverRegistry` 统一路由。使用者创建 Session，注册 change listener，再通过 `query()` 发起查询。新查询用请求 ID、连接 generation 和查询 scope 隔离旧结果，不允许迟到回执替换当前列表。

Session 由创建它的使用者关闭。`close()` 幂等移除该 Session 的请求跟踪、数据槽和监听；已投递的工作也必须通过 closed / generation / request 检查。关闭本地跟踪不等于服务端回滚已发送修改，也不能依赖关闭后的编辑完成回调继续驱动 UI。

断线会增加连接代次，清除在途请求和数据槽，并向仍由使用者持有的 Session 发布断线空页；Session 不会替代所有者自动销毁。查询、标签和数据请求由 tick 驱动超时结算。

## 标签与字典编辑

标签编辑仅对当前结果页中具有服务端 target reference 的条目开放，使用 `canEditTags()` 和 `isTagUpdatePending()` 区分能力与忙碌状态。`addTag()` / `removeTag()` 保留 expected 标签及 scope 校验，成功后的刷新沿用现有会话流程。

字典使用 `requestData()` 按需读取，`refreshData()` 显式重读，`replaceData()` 同时校验当前 target、expected 值和 revision。重复读取与在途保存会合并或阻止竞争；只读 reference 不允许保存。数据槽有数量上限，不得把 Session 当作无界全量缓存。

需要让字典编辑器独立于表格换页或关闭存活时，使用 `forkDataSession(entry)`。它保留当前已授权目标但不共享表格查询，不能再调用 `query()` 替换目标；编辑器关闭时仍须独立关闭这个 Session，服务端继续验证目标与 revision。

## 扩展与验证

活动 Session、监听和 pending 写入是正在使用的业务状态，不是可随全局缓存清理丢弃的缓存。新增查询条件或对象类型先扩展 core 查询及服务端协议，再透传快照；新增编辑能力沿用 target reference、revision 与会话 scope 校验，避免另建可写镜像。

回归入口为 `src/test/java/com/mine/geometry_node/client/feature/worldquery/` 中的 `ClientWorldObjectRepositoryTest` 与 `ClientWorldObjectDataRepositoryTest`，覆盖过期查询、独立编辑会话、断线和字典提交边界。本目录无需额外拆分子文档。
