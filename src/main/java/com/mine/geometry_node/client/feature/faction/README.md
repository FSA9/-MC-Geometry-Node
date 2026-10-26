# 客户端阵营目录与命令

本包目前只有 [ClientFactionRepository.java](ClientFactionRepository.java)，负责连接级阵营目录镜像和命令串行化。阵营模型、规则、持久化格式与服务端权限在 core；阵营编辑控件位于 `ui/components/integration/faction`，本包不引用具体 UI。

## 数据与依赖

`snapshot()` 返回最近发布的 `FactionCatalog`；调用方同时读取 `loaded()`、`loading()`、`error()` 并通过 change listener 刷新展示。目录来自服务端，发布时避免旧 revision 覆盖已有的新目录。错误使用消息键，由界面翻译。

依赖方向是 `UI → ClientFactionRepository → core.engine.system.faction、core.network、client.network.request`，线程和连接适配使用 Minecraft/NeoForge。发送前按 core 的 `WorldObjectDataValues` 校验载荷，不能直接把可变 UI 草稿放入命令队列。

## 命令与连接生命周期

- `init()` 幂等注册网络接收器、断线监听和连接级内存缓存 `geometry_node:faction.catalog`。
- `refresh()` 合并重复 READ；创建、更新、删除、关系和阶级命令进入有界 FIFO，一次只有一个在途请求。首次写入前如无已加载目录，先读取服务端 revision。
- 每次修改使用最后权威目录的 revision。限流回执按有界退避重试；写超时可能已经在服务端提交，因此只补读目录，不自动重放该写入。
- `disconnect()` 增加 generation、重置请求跟踪，结算在途及排队命令并清空目录。过期回执和延迟重试不能跨连接生效。

仓库随进程存在，数据随连接存在。窗口销毁时移除自己的 change listener，不重置全局仓库。命令通过 `CompletableFuture<Void>` 结算，关闭窗口不表示撤销已经提交的命令。

缓存清理在加载或提交期间保留正在使用的目录；空闲时清空镜像并通知监听者，有观察者时重新刷新。全局清理不能切断已提交命令的回执。

## 扩展与验证

新增阵营操作先补 core 的规则、请求处理与权限校验，再在仓库中增加明确方法，沿用快照化载荷、串行队列和 revision 校验。新增展示只订阅同一仓库，避免另一份可写阵营目录。

`src/test/java/com/mine/geometry_node/client/feature/faction/ClientFactionRepositoryTest.java` 使用可控 transport 与客户端队列覆盖命令、刷新和连接隔离；本包体量较小，由本 README 覆盖全部实现。
