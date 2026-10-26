# Client 目录导航与职责边界

客户端按状态、资源及生命周期的所有者组织。功能服务、游戏运行时、呈现和具体装配分别归属 `feature`、`runtime`、`ui`、`integration`。不要因为调用者位于某个窗口，就把共享能力放进该 Area；也不要仅靠移动目录掩盖依赖方向。

## 功能服务

| 目录 | 内容与选用规则 |
| --- | --- |
| `feature/asset` | 资产模型、目录仓库、文件结构事务、路径策略、预览产物缓存、远端请求与传输；不绘制缩略图或持有窗口。 |
| `feature/datalibrary` | repository 契约、连接快照、网络 gateway、映射与传输。图引用和仓库窗口共享服务。 |
| `feature/entity` | 实体编辑请求、登记索引和外观快照；`edit`、`index`、`preview` 分别保留自己的协议和生命周期。 |
| `feature/faction`、`feature/worldquery` | 阵营访问及世界对象查询与修改，不拥有具体窗口。 |
| `feature/scopeddata` | GLOBAL／维度公共 data 的目录与编辑会话，不拥有存储或具体窗口。 |
| `feature/graph/document` | 文档注册、文件身份、加载、保存、revision 和共享开图流程。 |
| `feature/graph/edit` | 图修改命令、历史和通知，不依赖 View；真实尺寸计算通过 `FrameBoundsPolicy` 注入。 |
| `feature/graph/persistence` | 图 JSON、元数据读写、原子文件存储、文档 claim 和结构事务。 |
| `feature/graph/query`、`metadata` | 供 AI 与 UI 共用的查询与 TAG 能力适配。 |
| `feature/scene` | 场景修改、确认、预览、冲突和历史。 |
| `feature/ai` | CLI/MCP 协议、命令目录、参数及补丁校验、传输；工作区适配在 `integration/ai`。 |
| `feature/terminal` | 终端会话、PTY、进程、模拟器和输入编码；不依赖 AI 或 UI。 |

功能之间通过真实能力合作，例如仓库使用资产传输、图文件使用资产结构事务。`feature` 是功能分类，不强制每个功能都有相同的 repository/service/model 层。见 [功能边界](feature/README.md)。

## 游戏运行时

| 目录 | 所有权 |
| --- | --- |
| `runtime/game` | 编辑相机、视口输入、区块、帧调度和世界渲染；具体宿主与界面绘制通过适配接入。 |
| `runtime/model` | 客户端模型加载、实例、CPU/GPU 资源、渲染、兼容和诊断数据。 |
| `runtime/light` | 灯光状态、绘制、材质适配和诊断采集；采集会话拥有世界引用和查询资源。 |
| `runtime/render` | 蓝图动态视觉效果及调试绘制。 |
| `runtime/behavior`、`collision`、`marker` | 对应功能的客户端运行状态与世界能力。 |
| `runtime/dialogue`、`quest` | 对话和任务会话、预览状态与网络动作；实际屏幕由呈现接口连接。 |
| `runtime/cache` | 客户端运行资源与通用缓存系统的适配。 |

`runtime/model/render/lod/cluster` 是有 README 和构建排除规则的归档实验，不作为活动扩展入口。模型与灯光的世界资源管理不属于 UI，即使它们最终产生画面。

## UI

| 目录 | 所有权 |
| --- | --- |
| `ui/MainUI`、`ui/shell` | 入口与具体工作区装配，注册 Area/侧栏、管理顶栏与全局弹层。 |
| `ui/workspace` | 区域布局、窗口协议、surface、拖放和生命周期；不导入具体 editor。 |
| `ui/workspace/state` | 带作用域的 MEMORY/PERSISTENT 界面状态，不代替业务存储。 |
| `ui/overlay` | 弹层协议、句柄、挂载及 `OverlayHost`，不查找具体 shell。 |
| `ui/document` | 图标签激活、视图默认状态及到单一文档注册服务的 UI 门面；不再持有修改核心。 |
| `ui/editor/*` | 真实工作区 Area 及其专用界面协调；可复用能力不能藏在 Area 中。 |
| `ui/components` | 可复用控件，按 base → composite → integration 分层。 |
| `ui/components/integration/asset` | 共享资产浏览、导航、选择弹窗、缩略图与任务呈现，供 Area 和 picker 共用。 |
| `ui/components/integration/entity/editor` | 实体编辑弹窗；业务请求、登记和外观快照位于 feature/entity。 |
| `ui/screen` | 游戏内对话、商店和任务等实际屏幕与样式。 |
| `ui/hud` | marker、模型诊断等 HUD 的布局和绘制。 |
| `ui/settings`、`shortcut`、`theme`、`utils` | 设置呈现、事件适配、主题及坐标工具；不存放业务服务。 |

完整组件导航见 [components/README.md](ui/components/README.md)。`UiContextMenu` 和 `ModalWindowView` 是通用菜单和模态窗口外壳；业务提供内容与动作，基础组件统一负责输入隔离和焦点。

## 公共能力与装配

- `platform`：工作目录、服务器身份、Minecraft 客户端线程和操作系统文件浏览适配。
- `network`：接收注册和连接代次隔离的请求关联，不持有窗口状态。
- `config`：配置模型、元数据、校验、草稿、存储和发布；保存失败不能确认或发布草稿。
- `input/binding`：不依赖 ModernUI 的键值、作用域和匹配；顶层管理器适配游戏输入。
- `history`：跨图和场景复用的历史会话与状态。
- `integration`：具体宿主、任务执行、UI 呈现和跨功能生命周期的连接，见 [装配边界](integration/README.md)。

## 依赖与生命周期约定

- `feature` 与 `runtime` 不依赖 `ui`、ModernUI 或具体 integration；UI 和装配可以使用功能能力。
- components 不依赖具体 editor/shell；base 不依赖 composite/integration，composite 不依赖 integration。
- 图的文档集合只有一个权威所有者；UI 标签与每视口状态不能再维护第二份文档业务集合。
- 图编辑使用显式注入的真实 `FrameBoundsPolicy`，AI 试算继承相同策略，独立持有试算图、历史和监听者。
- pending 请求和已排队回调属于连接代次；断线后未准入回调失效。完成、超时、取消最多有一个赢家。
- 缓存、请求队列、共享文件和 GPU 资源仍按原生命周期释放，不随使用它们的单个窗口关闭而销毁。
- 通用功能通过结果和事件反馈，具体成功/失败通知由呈现或命令适配决定。
- 移包同步更新 package、调用方、测试和文档，不保留旧包转发类；资源 ID、协议、文件路径和持久化键不随 Java 包名改变。

`ClientArchitectureTest` 检查目录和依赖边界，包括全限定名引用；行为测试验证文档历史、文件 claim、断线、取消、预览、窗口和运行资源。规则通过不等于完整游戏验收。重构记录见 [boundary-refactor.md](../../../../../../../docs/client/boundary-refactor.md)。

## 配置、缓存与发布依赖

配置统一在游戏目录 `config/geometry/`：客户端配置 `geometry_node_config.json`，工作区 `geometry_node_editor_session.json`；服务端配置注册为 `geometry/geometry_node-server.toml`，仍保留 NeoForge SERVER 同步与世界级覆盖语义。不读取旧 `.config/`。

本模组可重建缓存位于 `geometry_nodes/caches/`，与配置及服务器持久数据分离；加载器的 `.cache/jij/` 不归本模组管理。缓存 ID、作用域和设置项由统一缓存契约处理。

MUI、Architectury 为外部必需依赖；Sodium、Iris 为客户端可选依赖，不打进本模组。QuickHull3D、LWJGL Meshoptimizer、pty4j、Kotlin 标准库保留 Jar-in-Jar；Meshoptimizer 平台原生库随包分发。MixinExtras 使用 NeoForge 提供的版本。第三方模组自身内嵌库不由本模组搬迁。

运行依赖与渲染适配的已有约定：

- 当前 FML 11.0.13 将 `JIJ_CACHEDIR` 固定为 `.cache/jij`，早于本模组初始化解包。保留加载器原生目录（开发环境为 `run/.cache/jij/`），不设置目录联接或修改加载器。仅本模组自身的业务缓存统一放入 `geometry_nodes/caches/`。
- FML 11.0.13 动态创建游戏模块层，没有原生访问授权入口；仅在 JVM 参数中加入 `--enable-native-access=sodium` 无法授权这个后加载模块。`SodiumChunkMeshBufferMixin` 将 Sodium 0.9.x 区块缓冲中受限的 `MemorySegment.reinterpret` 替换为 LWJGL 直接缓冲的有界 `MemorySegment.ofBuffer` 视图，保持原分配、重分配和释放所有权；不放宽全局原生访问检查。Sodium 缺席时由 `@Pseudo` 跳过。
- 调试面和线通过 Iris 公开 API 分别注册 BASIC、LINES。调试点保留原版 `debug_point` 着色器与 `POSITION_COLOR_LINE_WIDTH` 格式，不注册为 BASIC，避免丢失 `gl_PointSize`；提交调试点批次期间通过 Iris 1.11 的 `ImmediateState.bypass` 明确使用原版着色器，在 finally 中恢复旧状态，避免 Iris 将这项有意保留的管线报告为缺少映射。反射字段按需缓存，仅作用于同步点批次提交；Iris 缺席时直接执行原绘制。Iris 对成功匹配面、线输出的 `okay` WARN 属于其正常日志。

## 自定义 DATA

DATA 是模组注册能力挂载的字典，独立于原版 NBT、物品数据组件和 TAG。仓库条目 DATA 随仓库文档保存；世界对象通过已有能力适配器存储。不在 UI 为不支持的对象创建另一套数据。

表格显示有界摘要，完整字典按需查询；侧栏预览最多八项，管理弹窗按二十项分页，支持键/值搜索、类型筛选、标量编辑、字典/列表 JSON 编辑和有界树预览。通用管理组件和实体编辑中的嵌入组件共享编辑能力；未完成的新条目不提交，已经完成的实时修改由服务端检查权限与预期版本。切换对象、关闭或断线仍隔离旧回调。整数输入不带步进按钮，未知注册值保持只读。
