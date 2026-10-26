# 运行资源的缓存注册适配

`ClientRuntimeCacheRegistration.register` 把运行时自己拥有的缓存接入 `core.cache.CacheRegistry.GLOBAL`，描述符属于 CLIENT 域、APPLICATION 生命周期。调用方提供统计和清理动作，并取得 `CacheRegistry.Registration`；本类不接管缓存内容。

统计与清理都通过 `Minecraft.schedule` 排队，**即使调用者已经处于渲染线程也不会内联执行**，避免设置面板在一次绘制中途驱逐正在使用的资源。返回的 `CompletionStage` 表达实际完成或异常，执行前已取消的任务不会继续操作缓存。

清理受公共缓存的 `supports` 规则限制，不把带作用域的请求擅自当成全局清理。清理前后会重新读取 retained 数量，报告仍被使用者保留的条目。接入新缓存时，提供的 `clear` 必须只清理可重建、可释放部分，不能借“清缓存”删除活动定义、租约或网络传输。模型另有更细的 [ModelCacheIntegration](../model/README.md)。
