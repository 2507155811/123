# TVLive 分析与吸收

仓库：https://github.com/Thatgfsj/TVLive
静态分析快照：8bb7c126137be6b7d7e46a7c0363065f30a98ace。没有编译或运行其 APK。

TVLive 使用 Kotlin、Compose、Hilt、StateFlow 与 Media3，最低 Android 7 / API 24。手机与电视入口分开，支持遥控器频道/数字键与手机手势。README 宣称 MIT，但快照没有独立 LICENSE 文件，因此此次仅参考设计，未复制源码。

## 关键发现

- ChannelRepository 内置频道名称，sources 都为空；REMOTE_SOURCE_URL 也是空字符串。它不是现成直播源库。
- 支持 IPTV TXT、M3U 分组，按频道规范化名字合并，来源主机去重和最多 8 个地址；本地文件优先，并缓存远程目录。
- 播放器记录最佳源、尝试备用源，有换台防抖、缓冲与进度停滞监控。默认 CCTV8 不适合所有用户，因此星幕 TV 保留首页入口。
- 所谓测速读取 HLS 清单后对第一个 ts/m3u8 地址发 HEAD，返回毫秒耗时，不是 Mbps。它对复杂主清单、相对路径与不用 .ts 后缀的分片支持有限，不能保证解码可用。
- BootReceiver 直接 startActivity 不保证 Android 10+ 后台启动成功；星幕 TV 保留用户开关、系统设置说明与通知回退。

## 星幕 TV 已吸收

同内容最多 8 个直接播放地址；成功 READY 后记忆所用地址。当前地址重试两次，失败后尝试此内容的备用地址，全部失败显示可遥控的重试界面。直播 20 秒缓冲/进度停滞监控，暂停不会触发恢复；直播 HLS 目标延迟 12 秒以留出缓冲，换台 350 ms 防重复。无备用地址时不会虚构来源，也不会擅自换到其他频道。

导入增加 M3U、IPTV TXT、XSPF、JSON 与单个公开 HTTP/HTTPS 播放地址，合并已有目录；同名、同栏目、同国家与同分组的地址合并为备用源。HLS 分片清单不是频道目录，需要导入清单 URL。数据导入不执行 JavaScript、JAR、爬虫与外部 XML 实体。1.2 新增 TVBox 标准 API 和直播列表数据导入，仍不执行插件。
