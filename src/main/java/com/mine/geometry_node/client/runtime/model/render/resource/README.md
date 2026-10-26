# 上传预算、纹理和 GPU 退休

资源所有权最终落在模型资产与它持有的纹理/缓冲上，预算账本和队列负责何时允许使用与释放，不替代模型服务的实例 lease。

| 文件组 | 边界 |
| --- | --- |
| `ModelTextureBackend` | worker 解码图片/mipmap，渲染线程上传并拥有独立纹理与 sampler，不修改共享 sampler。 |
| `ModelTextureDecodeCache`、`ModelLabPbrBaker` | 一次 preparation 的 CPU 图像复用与 glTF → LabPBR 烘焙；CPU 图像有显式 close，颜色/数据通道的色域语义不同。 |
| `ModelUploadQueue`、`ModelMaterialUploadQueue` | 有界帧上传；材质 staging 在完成/失败前仍由调用方持有。单项超字节预算可在独占时推进，避免合法大资产永远卡住。 |
| `ModelIrisUploadBudget` | 多消费者共享的本帧上传门限，每个消费者只能作有界推进。 |
| `ModelRenderBudget`、`ModelResidencyLedger` | CPU/GPU/纹理预算和实际驻留；只驱逐不活动 GPU entry，不能把预算不足当成删除模型定义。 |
| `ModelGeometryRetention`、`ModelGpuUseFence` | 保护当前/上一帧使用的几何，跟踪最后 GPU 使用；旧 fence 的完成不能覆盖较新的使用记录。 |
| `ModelGpuBudgetException` | 表达可因 fence 退休后重试的分配限制，供上层报告或延期。 |

解码数据、NativeImage 和 staging 由创建/接收它们的 preparation 或上传拥有者关闭，取消/异常也必须走释放路径。GPU 的纹理、sampler、缓冲与 fence 在渲染线程创建和销毁；从本帧列表删除不代表 GPU 已读完，退休前仍占用账本预算。

统一缓存操作通过 `ModelCacheIntegration` 排队进入客户端线程；闲置 GPU clear 不影响活动实例，统计也包含等待 fence 的占用。新增上传通道要同时接入预算、失败释放和取消检查，不能只增加一个异步 future 后把原生对象交给 GC。
