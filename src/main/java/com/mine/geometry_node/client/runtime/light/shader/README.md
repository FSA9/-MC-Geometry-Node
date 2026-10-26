# 灯光着色器适配和程序缓存

这里区分运行中的 GL 程序、可重建的接口查询、磁盘二进制缓存。它们不能通过一个 clear 混为同一生命周期。

- `NodeLightShader` 为已知光影包 receiver 选择有签名保护的补丁；不改磁盘上的 shaderpack 文件。
- `NodeLightTerrainShader` 只处理已知原版地形程序签名；与 Iris 光影程序分开。
- `NodeLightPrograms` 负责灯光自己的全屏程序编译与失败清理。
- `NodeLightIrisPrograms` / `NodeLightNativePrograms` 复用相应路径的已链接程序；Iris 仍建立自己的 uniform、sampler 和 image 绑定。`NodeLightShaderBuilds` 保存有界的最近编译观测，`NativeLightPreloads` 保留宿主正常的首次绘制编译行为。

## 新增光影包适配

`adapter/LightShaderAdapter` 定义 `matches` 和 `patch`；需要成对顶点源信息时可覆盖双参数 patch。当前 `LightShaderAdapters.all()` 是确定顺序的不可变列表，显式包含 Bliss、SEUS、Kappa、Photon、Sundial 实现。`LightShaderPatch` 和 `LightShaderOptions` 供这些适配器完成插入和选项处理。

适配器必须检查目标版本/源码结构，不能明确插入时返回 null，由调用链保留不支持路径；不要靠无界字符串替换宣称支持。扩展时同步接入显式列表和相应 shader 测试，保留原始程序的其他计算及宿主状态恢复。

## 二进制只是编译的可选加速

`NodeLightProgramCache` 以驱动 vendor/renderer/version、GLSL 版本、命名空间和实际源码共同生成键，校验大小、头、checksum、支持格式与链接结果。缺失、损坏或驱动拒绝时回退到 GLSL 编译，不把缓存命中作为渲染正确性的前提。

缓存目录保持为游戏目录下 `geometry_nodes/caches/light/shaders`，注册 ID 为 `geometry_node:runtime.shader_binaries`。`registerCache` 可由客户端初始化调用，也在首次编译前惰性初始化。

GL 二进制提取、装载与链接属于渲染线程；磁盘统计、清理及排队保存交给公共 `CacheExecutors`。`ShaderBinaryCacheStore` 以 generation 隔离旧写入，用户清理后旧队列不能重新填回缓存。清磁盘缓存不删除正在使用的已链接程序，程序释放由对应 renderer/宿主生命周期承担。
