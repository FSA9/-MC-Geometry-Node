# 引擎 Metadata 集成

这里连接纯 Java 的 [Metadata 能力内核](../../metadata/README.md) 与 Minecraft、节点值及各领域的权威存储。`core/metadata` 定义 Tag/Data 契约，`engine/metadata` 实现引擎集成，二者职责不同。

| 位置 | 职责 |
| --- | --- |
| 根包 `BuiltinMetadataCapabilities` | 组合并注册内置适配器；不维护对象数据。 |
| `state` | `OwnerMetadata` 保存不可变、可持久化的所有者值；`RuntimeMetadata` / `RuntimeTags` 保存随运行时实例存在的状态；`MetadataOwnerThread` 检查当前服务端所有者线程。 |
| `value` | 图值快照、合法性策略、NBT 编解码、比较与修订散列；不选择所有者或调用领域服务。 |
| `target` | 将节点目标和通用资源引用分发到已注册的 Metadata 适配器；它是跨领域装配层。 |

对象适配器跟随实际拥有数据的领域，避免所有 Metadata 实现都堆在一个公共目录：

| 所有者 | 适配器位置 |
| --- | --- |
| 实体 | `system/entity/EntityMetadataAdapter` |
| 阵营 | `system/faction/service/FactionMetadataTargets` |
| 标记 | `system/marker/MarkerMetadataTargets` |
| 场景对象 | `system/scene/SceneMetadataTargets` |
| 结构放置 | `system/schematic/placement/PlacementMetadataTargets` |
| Scoped state，包括 GLOBAL 和维度数据 | `graph/scoped/ScopedMetadataTargets` |
| 资料库 | `system/datalibrary/service/DataLibraryMetadata` |

依赖方向是注册入口／目标分发 → 领域适配器 → 共享状态与值规则 → 能力内核。`state` 可以使用 `value` 编解码；领域适配器和资源实现不能为了线程检查反向调用 `target`，应调用 `MetadataOwnerThread.requireCurrent`。`target` 中引用领域服务属于集成职责，不能把这些依赖带入共享状态。

新增对象的 Tag/Data 能力时，在所有者领域实现适配器，再交给注册入口组合。新增用户 Tag 或 Data key 不需要注册。GLOBAL 和维度只注册 Data；实体、支持的资源及自定义阵营按契约提供 Tag/Data，所有权、权限与持久化规则仍由原领域决定。

层级用于表达实际依赖，不要求每个领域再套一个只有适配器的 `metadata` 子目录。共享空间驻留判断独立位于 `engine/residency`：它只观察区块和实体可用性，不拥有 Metadata，也不管理资源定义、强加载票据或删除。

`MetadataArchitectureTest` 检查共享层依赖、领域适配器与分发层的方向、驻留层独立性和包路径；原有行为测试继续验证持久化、实例失效、原子更新与值隔离。

本轮主代码与测试源码编译、JAR 构建检查及 610 项相关回归通过，包含 4 项 Metadata 架构检查。验证沿用 system 重构的临时排除配置，另排除了需要当前 FML Loader 的 `ScopedDataCatalogViewTest`；未启动游戏。原有旧碰撞测试及依赖游戏环境的排除范围见 [system 验证说明](../system/README.md)。
