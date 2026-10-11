# 附件连接方式分析与 Aero VPN 1.7.0 改动

分析对象：上传的 `base.apk.1`，应用标识 `org.vpndigger`，名称 `WroldPass Free VPN`，版本 2.0.3 / 203，最低 API 21、目标 API 31。文件 SHA-256：`0a013f4209c5efb2e592cd9c2e6f5170890d20450e1c421f8ba86ef6361f9249`。仅进行 APK ZIP、DEX 指令和资源静态分析，没有安装或执行附件，没有获得真实服务器连通性证据。

## 连接流程的证据

1. `com.worldpass.app.vpn.service.TunService` 继承 Android `VpnService`。代码调用 `VpnService.prepare` 请求系统授权，并通过 `VpnService.Builder` 设置地址、路由、DNS、MTU、按应用允许/排除规则、非阻塞 TUN，再建立隧道接口。
2. `com.worldpass.app.vpn.core.bridge.Bridge` 加载 `libbridge.so` 与 `libclash.so`，通过 JNI 调用 `nativeLoad`、`nativePatchSelector`、`nativeQueryTunnelState`、`nativeStart*` / `nativeStopTun` 等方法管理内核。附件只有 armeabi-v7a 的原生库，与 Aero 的 64 位内核构建不直接兼容。
3. DEX 中可见 `VpnService.protect(int)` 与 `setUnderlyingNetworks`，用于避免代理自身连接进入 VPN 回环，并通知底层网络变化。`nativeNotifyDnsChanged`、`nativeSuspend`、`nativeReset`、`nativeHealthCheck`、`nativeHealthCheckAll` 显示生命周期、DNS 和健康检查均有独立的控制入口。
4. `assets/simple.yml` 包含 `select`、`url-test`、`fallback` 代理组，测试周期 300 秒；`assets/vpn.yml` 包含 `select`、`autofast`。配置测试地址为 Google / Bing。配置存在不证明用户实际选择了自动组，也不能证明它在当前网络稳定。
5. 资源设置本地 SOCKS 端口、HTTP 端口和 REST 控制端口。`simple.yml` 控制器为 127.0.0.1:9292，`vpn.yml` 为 127.0.0.1:9595。规则含本地网络直连与按域名分流。它是代理内核加手机 VPN 接口，而不是把 Clash YAML 当作 OpenVPN 配置。

## 节点来源与限制

DEX 引用 `https://raw.githubusercontent.com/sharmajv/vpn/main/config.json` 和 jsDelivr 对应镜像，GitHub 仓库链接为 `https://github.com/sharmajv/vpn`。分析时配置与 GitHub 仓库 API 均返回 404，因此不能拿它作为有效节点源。还发现一个 HTTP 私有 API 字符串 `http://api.82book.com/v3`；未调用该接口，也未将其当成公开节点服务。静态字符串不能证明当前后台业务、是否免费、节点地区或许可。

附件未提供可确认的完整源码许可，因此 Aero 未复制其中的原生库、反编译源码、账号或密钥。开源客户端不等于服务端免费，也不等于公开代理订阅授权可用。

## 应用到 Aero 的改进

| 事项 | Aero 1.7.0 行为 |
| --- | --- |
| 连接与检测分离 | 保留批量 TCP 探测；选中连接后才建立手机 VPN。端口可达不等于可以上网。 |
| 短暂验证失败 | 内核仍报告 CONNECTED 时，外部 HTTPS 验证失败不再单独触发主动断开，明确显示未验证；手动测速可重新验证。 |
| 生命周期 | 延续断开确认、释放旧内核、同一节点退避重试；手动断开仍生效。 |
| 节点选择 | 保持用户要求的手动选择，不恢复自动换节点。 |
| 新免费来源 | 新增 RiseupVPN 官方 LEAP v3 匿名 API，使用签发的客户端证书及现有 OpenVPN 内核，不接入未知 Clash 订阅。 |

## Riseup 来源的验证边界

官方开源项目为 [LEAP Bitmask Android](https://github.com/leapcode/bitmask_android)，参考提交 `b16726af735bd71d6ddcf45259d868a3a337b2c7`。提供者资源声明匿名、免费、OpenVPN，API 为 `https://api.black.riseup.net:4430/3/`，路径 `config/eip-service.json` 与 `cert`。只解析支持的 TCP / OpenVPN 公网网关，排除 CN/HK/MO；实际候选来自 API，绝不将上游测试样本当作真实节点。

API 使用上游固定提供者 CA、证书有效期校验及正常域名验证，未关闭 TLS 验证。CA 指纹、许可证与期限见 [第三方说明](../third-party/leap/NOTICE.md)。该 CA 2026-11-02 到期；届时需要更新应用中的可信提供者证书，不会忽略过期。匿名证书与私钥只存手机应用私有缓存，不上传至 GitHub，缓存最多六小时并检查证书期限。

云端网络拒绝访问该 API，无法验证实时目录或实际手机连接。新增来源属于完成接入、配置测试通过，不能声称已经取得任何当前可用的 Riseup 节点。API 不可达时显示来源暂不可用，其余来源仍可搜索；免费节点的可用性和稳定性最终取决于手机网络及服务端。
