# 归档的 CPU Cluster 实验

这里保留旧 CPU cluster 层级实验作为研究材料。Java 源文件及匹配的 `render/lod/cluster` 测试均由 Gradle 活动 source set 排除；当前模型 LOD 位于同级 `../model`，导航见 [LOD 说明](../README.md)。

归档实现没有接入当前导入、渲染、命令、HUD 或性能报告。重新启用前须重新审查内存增长、代表三角形代理、层级选择、阴影投影和驻留回退，不能仅恢复 import 就作为产品扩展入口。

`integration/*.java.disabled` 是当时 renderer/asset 装配的历史文本，不编译。它们保留原包名和引用，以便阅读旧接线，与当前迁包后的代码不必一致；不要把这些历史路径当成活动依赖遗漏。

当前 `render/geometry` 中基于源索引的 cluster bounds 仍用于视锥剔除，与本实验相互独立。局部光照 BVH 继续使用完整源几何。此目录本身不拥有运行中的世界、worker 或 GPU 资源。
