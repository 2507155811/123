# Aero VPN — 安卓免费境外 VPN 搜索与连接

简洁浅色界面，内置 OpenVPN 原生内核，**无需安装另一个 VPN 客户端**。
支持 Android 8.0+ 的 ARM64 手机，以及 x86_64 模拟器。

## 功能

- 通过 HTTPS 获取日本筑波大学 VPN Gate 的动态免费节点目录，不使用虚构地址。
- 默认过滤地区代码 CN、HK、MO，过滤重复、异常地址和不安全配置。
- 搜索国家（支持常见国家的中文名）、IP、节点名称；按地区筛选。
- 推荐 / 延迟 / 速度排序、本地收藏、刷新以及一小时离线缓存。
- 选择节点后在应用内连接、取消或断开；首次使用需安卓系统 VPN 授权。
- 显示内核返回的真实连接状态与累计流量；不会把“正在连接”显示为“已连接”。

节点数量随目录变化，应用没有人为设置小数量上限。国家、延迟与速度是目录报告值，
不等于独立地理验证或本机测速。免费志愿者节点可能离线、拥堵或记录访问。
应用没有账号、广告或统计 SDK；缓存和收藏保存在本机。

## 构建

1. 安装 JDK 21、Android SDK 36、Build Tools 35.0.0、NDK 29.0.14206865 和 CMake 3.22.1。
2. 在本目录运行 `python3 scripts/prepare_engine.py`，获取固定版本的内置 VPN 内核及其子模块。
3. 在本机通过 Android Studio 打开项目，或设置 `ANDROID_HOME` 后运行：

   ```sh
   ./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
   ```

提供的完整源码压缩包已包含准备好的 `.engine/`，解压后也可离线使用这些内核源码。

4. APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。这是开发签名安装包，正式发布需使用自己的签名配置。

在本云工作区可用已验证的工具链：

```sh
cd /workspace/123
python3 scripts/bootstrap_cloud.py
python3 scripts/prepare_engine.py
python3 scripts/cloud_gradle.py :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

工具链安装脚本验证官方归档的校验和，保留 TLS 校验，通过系统信任库使用云代理。
它不会修改 HOME，也不会保存代理凭据。`local.properties` 和 `.engine/` 是忽略的本地生成文件。
如果 Oracle 的 latest JDK 归档更新导致固定校验和不匹配，脚本会停止；需核验官方新版本后更新固定版本。

## 验证

11 项单元测试覆盖地区排除、中文搜索、CSV 引号、去重、异常记录、未知延迟、内网地址、
远端匹配、内联证书要求及脚本/插件注入拒绝。

真实 VPN Gate HTTPS 获取和缓存读取已验证；APK 的两个 ABI 均包含原生 VPN 库和可执行文件。
安装和启动已在 Android 9 与 Android 15 模拟器验证。云模拟器不能直接解析节点源的 DNS，
界面验证使用宿主机通过 HTTPS 下载的真实节点列表作为离线缓存，并在界面标注缓存。
应用只接受 HTTPS 节点源，没有降低证书或包校验要求。
模拟器中的 IP 搜索、收藏保存与节点选择已验证；原生 OpenVPN 程序已运行并输出正确版本。
云模拟器没有硬件加速，系统界面出现卡顿；系统 VPN 授权与内核启动已验证；所选节点的 TCP 连接被拒绝，真实隧道尚未连通验证。
连接时额外启用标准的 TLS 服务端证书用途检查（`remote-cert-tls server`），不降低证书校验。

不要把节点目录可访问等同于每个 VPN 节点都可连接。真实手机上的连接质量依赖节点在线状态、
运营商网络以及安卓系统授权。

## 源码与许可证

本项目遵循 GPLv2 及内置 OpenVPN for Android 的链接例外条款，见 LICENSE 和
THIRD_PARTY_NOTICES.md。内核固定版本与所有兼容性覆盖集中在 `scripts/prepare_engine.py`。
发行 APK 时须同时按许可证要求提供完整对应源码；仅分发安装包不够。

### 1.1.0 更新

- 节点连接超时、DNS 和 TLS 错误使用中文提示；刷新按钮保持单行。
- 支持通过系统文件选择器导入 VPN Gate 原始 CSV 列表（最大 12 MB），继续执行境外地区及配置安全校验。可在能够访问官方网站的网络下载 `https://www.vpngate.net/api/iphone/` 的原始内容，保存为 CSV 后传到手机导入。导入不是第二个在线节点源，不能解决节点服务器自身不可达的问题。
- 缓存可浏览 24 小时，超过一小时须更新后连接；更新失败保留现有列表。
- 此版没有内置过期节点或使用未经验证的镜像。真实 VPN 隧道连通仍需在手机网络测试。

### 1.2.0 节点获取更新

- 首次启动先加载安装包中的真实节点快照，不再依赖首次成功连接 VPN Gate。快照保存原始采集时间；七天后不再接受，超过一小时连接前明确提示可能已失效。
- 同时查询官方 HTTPS API、本项目 GitHub Raw 和 GitHub 下载入口，采用第一个通过格式、地区及配置校验的结果。三个渠道分发同一 VPN Gate 目录，不是三个独立 VPN 服务商。较旧的备用列表不会覆盖较新的本地列表。
- GitHub Actions 每小时计划更新 `nodes/vpngate.csv`，也可手动触发 `Refresh public VPN Gate directory`；GitHub 可能延迟计划任务，失败时保留旧列表。管理员需要在仓库中启用 Actions；此更新不需要 VPN 账号或密钥。
- `python3 scripts/update_nodes.py --bundle` 在构建前同时更新公开目录和 APK 快照。仅运行不带参数的脚本更新在线目录，不会重新生成 APK。
- GitHub 获取渠道也可能在手机网络不可达。离线节点能被搜索不代表能建立隧道；本版本不承诺可连接节点数量，仍需真机测试。
