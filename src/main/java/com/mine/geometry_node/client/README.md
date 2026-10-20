# Client 目录导航与职责边界

新增代码先按状态、资源及生命周期的所有者选择目录。具体 View、菜单和窗口放在 UI；连接级快照、请求、文件及进程能力放在功能服务；跨功能或 Minecraft/ModernUI 的装配放在 integration。不要因调用者位于某个编辑器，就把共享能力放进那个编辑器。

## 功能与服务

| 目录 | 内容与选用规则 |
| --- | --- |
| `ai` | CLI/MCP 协议、命令目录、图查询和补丁事务。`ai/graph/editor` 是文档应用层适配器，不是第二套图模型或历史。 |
| `asset` | 资源文件、路径、预览缓存、远端请求及传输。界面只呈现状态和组织用户动作。 |
| `datalibrary` | 数据仓库 repository 契约、连接快照、网络 gateway、映射与传输；Graph 引用和仓库窗口共享同一服务。 |
| `graph/persistence` | 图 JSON、元数据、图文件身份、读写与保存。 |
| `graph/query` | 不需要 View 的节点搜索、注释文本和本地化查询，供 AI 与界面共用。 |
| `graph/metadata` | 图文件/文档的 tag 能力适配。 |
| `config` | 配置模型、校验、草稿、存储及发布；显式保存失败不确认草稿、不发布候选值。 |
| `history` | 通用历史会话与状态；Graph、场景各自提供业务快照和应用语义。 |
| `input/binding` | 与 ModernUI 无关的键值、作用域、绑定解析和匹配语义；`input` 的顶层管理器负责游戏输入。 |
| `network` | 客户端接收注册与请求关联，不拥有具体窗口状态；`network/request` 的连接代次隔离排队回调。 |
| `platform` | 工作目录、文件位置和客户端运行环境适配。 |
| `terminal` | 终端会话、PTY、进程、模拟器和输入编码；不依赖 AI 或 UI。 |
| `integration/terminal` | 终端与 MCP 启动、取消和资源释放的装配。 |
| `integration/picker` | 共享的原版物品、实体引用、实体模板选择会话，分别保留各自协议。 |
| `integration/game` | 将具体工作区宿主接入运行时视口策略。 |

## 配置和缓存路径

本模组配置统一保存到游戏目录的 `config/geometry/`（开发环境为 `run/config/geometry/`）：

- `geometry_node_config.json`：客户端设置，由 `ClientPaths` 提供位置。
- `geometry_node_editor_session.json`：编辑器布局和持久界面状态，同样由 `ClientPaths` 提供位置。
- `geometry_node-server.toml`：通过 NeoForge 注册相对子路径 `geometry/geometry_node-server.toml`；保留 SERVER 配置同步和世界级覆盖语义。

不再读取或生成 `.config/`，不提供旧目录回退或自动迁移。其他模组的配置仍由其自身管理。`geometry_nodes/caches/` 是可重建缓存（资产预览默认为 `asset_previews/`，模型为 `model/`，灯光为 `light/`），独立于配置；`.cache/jij/` 属于模组加载器。

## 运行时与模型

| 目录 | 所有权 |
| --- | --- |
| `runtime/game` | 编辑视口输入、相机、区块、帧发布和渲染。宿主识别由窄策略提供。 |
| `runtime/light` | 灯光状态、绘制、材质适配和诊断采集；诊断会话负责自己的世界引用与查询资源。 |
| `runtime/render` | 蓝图动态视觉效果及调试绘制。 |
| `runtime/behavior`、`collision`、`marker` | 对应功能的客户端运行状态与表现。 |
| `runtime/dialogue`、`quest` | 游戏运行时的对话与任务，包括其游戏内 UI。它们不是工作区编辑器，因此不强制迁入 `ui/editor`。 |
| `model` | 客户端模型加载及生命周期；`resource`、`runtime`、`render`、`compat`、`diagnostic` 按 CPU/GPU 资源和呈现所有权划分。 |
| `scene` | 场景修改、确认、预览、冲突和历史。 |
| `worldquery` | 各窗口独立的只读世界对象查询与刷新。查询与修改的超时语义不同，保持独立。 |

`model/render/lod/cluster` 是有 README 和构建排除规则的归档实验，不作为活动扩展入口。

## UI

| 目录 | 所有权 |
| --- | --- |
| `ui/MainUI`、`ui/shell` | 入口和具体装配；注册内置编辑器/侧栏，管理顶栏及全局弹层实现。 |
| `ui/workspace` | 区域布局、窗口协议、surface、拖放和生命周期；不导入具体 editor 实现。 |
| `ui/workspace/state` | 带作用域的 MEMORY / PERSISTENT 界面状态；不代替业务数据存储。 |
| `ui/overlay` | 共享弹层协议、句柄、挂载与 `OverlayHost` 能力；不查找具体 shell。 |
| `ui/document` | 打开文档、文件引用、会话和选择 ID。 |
| `ui/document/edit` | Graph 修改应用层：EditorContext、GraphController、命令和历史适配。尺寸通过 FrameBoundsPolicy 注入，AI 试算复用同一策略。 |
| `ui/editor/*` | 工作区中的具体编辑器及其专用协调、View、菜单内容；跨编辑器通用能力不能藏在这里。 |
| `ui/settings` | 全局设置窗口、页面、配置项编辑和宿主适配。 |
| `ui/components` | 可复用控件，按 base → composite → integration 分层，见[完整组件列表](ui/components/README.md)。 |
| `ui/shortcut` | ModernUI 事件到绑定语义的适配及快捷键分派。 |
| `ui/theme`、`utils` | 主题、坐标与缩放工具；不要成为存放业务服务的杂物目录。 |

`UiContextMenu` 是通用级联菜单，`ModalWindowView` 是可复用模态窗口外壳，二者均在 composite。选择框、文本框及按钮的手势由组件拥有；容器只在子控件没有消费事件时启动自己的动作。

## 依赖约定

- UI 使用服务接口；服务不能为了读取数据或完成请求反向依赖某个窗口。
- components 的 base 不依赖 composite/integration；composite 不依赖 integration；所有组件不依赖具体 editor/shell。宿主协议与 Area 生命周期契约可以被组件使用。
- 文档修改应用层不依赖具体编辑器或 ModernUI View；GraphSession 在创建时获得布局能力，不使用全局静态策略兜底。
- config、input/binding、terminal、history、datalibrary 和 graph/query 不依赖 ModernUI。
- 抽取接口必须有真实的所有权或依赖方向问题；不为单次跨编辑器导航创建通用事件总线，不按文件行数拆类。
- 移动 Java 类时同步 package、所有调用方、测试和文档，不保留旧包名转发实现。资源 ID、网络格式、存储路径和持久化键保持兼容。

目录及关键依赖由 `ClientArchitectureTest` 检查。完整审查记录和验证入口见项目根目录 `docs/client/architecture-review.md`、`docs/client/architecture.md`。

单元测试在 build 下的 test-runtime 工作目录中运行；源码、着色器和外部测试素材通过显式项目根目录读取，避免在项目根目录生成 config。

## 发布依赖与加载器缓存

- MUI、Architectury 为外部必需依赖；Sodium、Iris 为客户端可选依赖，版本范围集中在 `gradle.properties`，由模组元数据声明。它们不包含在本模组 JAR 中。
- 普通 Java 库 QuickHull3D、LWJGL Meshoptimizer、pty4j 和 Kotlin 标准库保留 Jar-in-Jar。Meshoptimizer 的跨平台原生库同时随包分发。MixinExtras 由当前 NeoForge 提供，不重复打包。
- Iris 内部的 GLSL Transformer、JCPP、ANTLR 和 Sodium 内部 JAR 由各自模组管理；声明外部依赖不会改变第三方模组的内嵌结构。
- 当前 FML 11.0.13 将 `JIJ_CACHEDIR` 固定为 `.cache/jij`，早于本模组初始化解包。保留加载器原生目录（开发环境为 `run/.cache/jij/`），不设置目录联接或修改加载器。仅本模组自身的业务缓存统一放入 `geometry_nodes/caches/`。
