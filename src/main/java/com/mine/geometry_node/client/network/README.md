# 客户端网络接收与请求关联

| 入口 | 职责 |
| --- | --- |
| `ClientNetworkReceiverRegistry` | 一次性注册客户端 S2C receiver，将 packet 交给对应 feature/runtime；必要时在入队前捕获连接代次，避免旧包延迟进入新连接。 |
| `request/ClientRequestTracker` | 可复用请求编号、pending、超时/取消/完成竞争及代次隔离。不解释业务 payload，也不持有窗口。 |

具体协议的请求/响应状态由各功能拥有，例如 `feature/entity`、`feature/datalibrary`、`feature/asset`；不要在此再维护第二份仓库快照。`ClientNetworkReceiverRegistry` 是具体接线入口，因此可依赖集成选择器；这不意味着通用 request 关联器可以依赖 UI。实际 packet 定义和编解码仍在 `core/network`。

接收、线程排队、完成分派须持续保留连接代次校验。清理连接时先使旧请求失效，不能只清空集合而允许已排队回调访问下一连接的状态。新增 receiver 必须明确业务线程与生命周期，不在网络回调里直接创建窗口。
