# 引擎功能系统

`system` 按业务领域划分，领域内部再按模型、持久化、执行和对外适配等真实职责分层。公共模型和协议可以同时被客户端和服务端使用；这里不依赖客户端实现或具体 UI。

## 目录职责

本次检查了原有全部 21 个顶层目录及其子目录。实体能力聚合后，保留以下 15 个领域。

| 领域 | 职责和子目录 |
| --- | --- |
| `asset` | 通用资产身份、内容散列、元数据和路径契约。`reference` 管引用和解析；`type` 管类型识别、生命周期与内置能力装配；`repository` 管权限、仓库路径、文件事务与缓存。 |
| `asset/preview` | 根包是预览契约和服务；`generator` 是生成接口与实现，`generator/schematic` 是结构缩略图算法；`store` 是派生缓存及维护。这里的预览是资产缩略图，不是实体外观。 |
| `asset/transfer` | 根包是传输服务和限制；`model` 是传输状态、策略和快照；`io` 是传输的原子文件提交。通用内容散列归 `asset`，通用有界执行器归 `core/utils/io`。 |
| `datalibrary` | `model` 管文档、目录树、值和指纹；`storage` 管 JSON、诊断和原子文件写入；`service` 管权威快照、元数据及远程仓库；`runtime` 管蓝图写入与等待。 |
| `dialogue` | 根包管理运行时、会话、等待及交易执行；`model`/`model/shop` 管页面模型；`presenter` 管聊天与网络展示通道；`richtext` 管文本解析；`storage` 管交易次数。 |
| `entity` | 聚合原生 Minecraft 实体能力，根包保留共享创建入口。具体子域见下表。 |
| `faction` | `model` 管阵营、关系、职位和成员规则；`storage` 管文件与 JSON；`service` 管编辑、发布和实体成员适配。阵营自身拥有 Metadata，`service/FactionMetadataTargets` 负责适配。 |
| `image` | 运行时图片的格式、校验、引用、配置和服务，与资产缩略图生成不同。当前规模无需额外分层。 |
| `marker` | 根包管理实例服务、持久化及 `MarkerMetadataTargets` 所有者适配；`model` 管地址、锚点、受众和实例；`type` 管类型契约与注册。 |
| `model` | 共享 CPU 模型与碰撞内核，不是通用资产仓库。子包职责见下文。 |
| `quest` | 根包管理任务、条件与事件；`model` 是不可变领域数据；`graph` 从节点图提取条件概览；`status` 管状态注册；`storage` 管实体任务附件；`screen` 管屏幕会话及视图装配。 |
| `scene` | 根包管理权威场景文档、服务、类型验证、原生实体及 `SceneMetadataTargets` 适配；`property` 管属性契约、节点输入和检查器 schema；`geometry` 管几何类型及变换；`volume` 管区域和碰撞运行时。属性 schema 也用于服务端校验。 |
| `schematic` | `execution` 管结构执行请求与任务；`placement` 管放置记录、持久化、调试同步和 `PlacementMetadataTargets` 适配；`projection` 管临时投影定义与运行时。 |
| `scopeddata` | GLOBAL／维度 PUBLIC 数据的远程权限、目录、读取、CAS 与连接限速；复用 scoped state 存储，不再保存一份数据。 |
| `video` | 视频资源格式契约，目前只有格式表，保留明确的资源类型边界。 |
| `viewport` | 根包管理编辑相机、观察者、区块追踪和短期租约；`entity` 管视口原生实体编辑、恢复及移动命令。它归视口会话所有，不并入通用实体编辑服务。 |
| `worldquery` | 根包管理跨领域查询调度、收集、内置数据源和宿主适配；`model` 是查询协议及不可变结果；`provider` 是扩展接口；`metadata` 是查询命中后的 Tag/Data 编辑。 |

## 实体领域

| 子目录 | 拥有的职责 | 主要边界 |
| --- | --- | --- |
| `appearance` | 安全外观捕获、清理、NBT 编解码与预算 | 被编辑、索引和预览共用，不依赖它们的服务或请求协议。 |
| `classification` | 类型特征、默认值与实例覆盖 | 定义实体分类规则，不维护编辑会话。 |
| `edit` | 属性、装备、绑定图的读取、验证及修改事务 | 协调器、适配器、会话和内部事务类型保持同包。 |
| `index` | 显式注册、加载索引、最后观测快照及持久化 | 卸载快照不是实体重生或完整实体存档。 |
| `preview` | 外观请求、限流及服务端响应 | `protocol` 只管预览目标和请求／响应预算；外观数据规则归 `appearance`。 |
| `chunkloading` | 实体 UUID 所有的强加载配置、票据恢复及持久化 | 不承接相机的临时区块租约；后者归 `viewport`。 |
| `display` | 原生 Display 的姿态、pivot 及变换控制 | 不与 `model/math` 的 CPU 模型变换混合。 |

`entity` 与 `client/feature/entity` 在编辑、索引和预览的命名上对应，但不要求目录镜像。客户端领域拥有连接级请求队列和缓存，核心领域拥有权威操作与公共契约。

`faction/service/EntityFactions` 和 `quest/storage/EntityQuestAttachment` 分别实现阵营成员与任务持久化规则，因此仍由阵营和任务领域拥有。不能仅凭类名包含 Entity 就迁入实体公共层。

## 模型领域

- `asset`：不可变模型、网格、场景、身份、清单和预算。
- `io`：格式无关的字节源、资源提供者、路径和取消契约。
- `gltf`：GLB/glTF 容器、访问器、场景、几何、材质、纹理及导入预算。
- `runtime`：加载句柄、仓库、租约、实例及姿态的生命周期。
- `collision`：共享碰撞几何、配置和变换；`generation` 管构建、缓存发布及内部算法；`runtime` 管世界实例、空间查询、运动求解和采样。
- `capability`、`diagnostic`、`math`：能力证据、结构化错误和公共数学表示。

实体 Metadata 的权威适配器 `EntityMetadataAdapter` 位于 `entity` 根包；它与阵营、场景等领域的适配器共同实现 `core/metadata` 能力契约。共享状态、值规则与跨领域分发的层级见 [engine/metadata](../metadata/README.md)。空间驻留观察归 `engine/residency`，不与实体强加载或资源资产仓库混合。

## 依赖与扩展规则

1. 共享模型、纯计算和规则不反向依赖请求服务、编辑会话或 UI。新增能力先确定所有者，再选择包名。
2. 世界编辑授权归 `core/engine/security/WorldEditPermissions`。资产权限可委托这个共享策略；阵营、场景、实体、公共数据访问不依赖资产权限实现。
3. 通用有界 IO 归 `core/utils/io/BoundedIoExecutor`；资产预览和传输共享的散列归 `asset`，其他业务不借用 `transfer/io` 的内部工具。
4. `worldquery` 是跨领域查询装配层，可以依赖各领域服务。`entity/index` 使用 `worldquery/model` 的不可变查询快照；模型层不反向依赖实体索引或查询执行层。这里没有为减少包级依赖数量另造一份存档 DTO。
5. `AssetTypeCatalog` 是内置资产能力装配入口，对生成器和生命周期实现的依赖属于装配职责。新增第三方能力使用现有注册契约。
6. 保留包内实现边界。对话会话、实体编辑事务和碰撞算法协作簇不为拆目录而扩大可见性，也不添加旧包名转发层。

本次迁移只调整 Java 归属和上述职责边界，不改变世界数据路径、NBT/JSON 字段、缓存标识、图类型 ID、网络消息 ID、Metadata 所有权或授权规则。原有功能和存档沿用现有契约。

`SystemArchitectureTest` 检查包路径一致性、客户端依赖隔离、实体共享层依赖方向、查询模型边界和共享授权的归属；业务回归测试继续验证编辑、索引、预览、结构和任务解析行为。

## 本轮验证

主代码编译、测试源码编译和 JAR 构建通过；58 个测试类共 455 项相关回归全部通过，其中包含 4 项新增架构检查和 4 项新增任务图解析检查。覆盖实体编辑／索引／预览、对应协议与客户端请求、结构投影、资产预览缓存、资料库、世界查询和阵营。JAR 中已核对新类齐全，迁移前的类不存在；源码和活跃文档无遗留旧路径。

验证使用临时 Gradle init 配置，未修改项目构建配置。测试源码编译排除了已有的 `GeometryCollisionPushConstraintsTest`、`GeometryCollisionPushMathTest`、`GeometryCollisionPushShapeTest`、`GeometryCollisionPushSyncTest`、`GeometryCollisionDynamicSupportTest`、`PacketGeometryCollisionTest`，它们引用当前已不存在的碰撞接口。

普通 JUnit 未执行依赖 Minecraft/FML 的 `EntityEditorNamesTest`、`EntityEditorAttributeRuntimeTest`、`EntityTypeTraitRuntimeTest`、`EntityFactionsViewTest`、`FactionEditorsTest`、`FactionCatalogViewTest`、`FactionDataDictionaryBindingTest`、`FactionTagsViewTest`、`FactionEditorMetadataTest`，以及 `WorldObjectsViewTest.factionMembersReuseEntityColumnsEnforceFixedScopeAndIsolateState`。其中本轮尝试执行的后三个阵营界面类在 FML／SharedConstants 初始化阶段失败，加入临时排除后其余回归全部通过。未启动游戏进行界面验证。
