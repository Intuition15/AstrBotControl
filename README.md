# AstrBot 控制台（Android）

一款运行在 Android 16（API 36）上的 AstrBot 控制台客户端。在手机上填写 AstrBot 服务器的 **IP 和端口** 即可连接 AstrBot Dashboard，远程管理插件的安装/卸载/启停/更新、系统与各功能配置、平台机器人、模型提供方、定时任务、日志、备份、会话、技能、API 密钥、工具/MCP、人设、知识库、子代理，并可进行对话测试。

> 兼容 AstrBot v3.5 与 v4（插件管理走两版通用的 `/api/plugin/*` 接口，其余功能使用 v4 的 `/api/v1/*` 接口）。

---

## 功能一览

| 模块 | 说明 |
| --- | --- |
| 连接设置 | 填写 IP + 端口（默认 6185）+ 用户名/密码登录，支持 HTTPS、TOTP 两步验证、记住密码 |
| 状态概览 | AstrBot 版本、运行时长、消息统计、CPU/内存、存储占用、重启核心、清理存储 |
| 插件管理 | 已装插件列表、启用/停用、更新、重载、卸载、详情、README、插件配置（表单化编辑） |
| 插件市场 | 浏览 AstrBot 插件市场、搜索、一键安装（可忽略版本检查） |
| 插件安装 | 输入 Git 仓库地址安装；或直接上传本地 `.zip` 插件包安装 |
| 配置管理 | **表单化**：系统配置/配置档按开关、输入框、下拉等控件编辑（由后端 schema 驱动，对齐 Web 前端），另保留「JSON 高级编辑」入口 |
| 平台管理 | **表单化**：平台类型下拉选择 + 按平台模板生成的参数表单（敏感字段自动掩码） |
| 提供方管理 | **表单化**：提供方类型（OpenAI/Anthropic 等）下拉选择 + API Key/Base URL/模型等表单，开关启停 |
| 实时日志 | 历史日志 + SSE 实时流（自动重连、按级别过滤、自动滚动、ANSI 清理） |
| 定时任务 | 创建/编辑/启停/立即执行/删除 Cron 任务（支持单次执行） |
| 会话管理 | 查看会话、会话组，批量设置提供方/服务 |
| 技能管理 | 技能列表与启用/停用 |
| 备份管理 | 创建/下载/导入/删除备份（zip） |
| 系统更新 | 检查更新、更新核心、更新 Dashboard、pip 安装包 |
| API 密钥 | 创建（含权限范围/有效期）/吊销/删除 |
| 工具与 MCP | 工具启停、MCP 服务器列表与新增 |
| 人设 / 知识库 | Persona 增删改、知识库增删与文档查看 |
| 子代理 | 子代理配置查看与编辑 |
| 对话测试 | 在控制台内与机器人对话（SSE 流式回复） |

---

## 构建（APK）

### 方式一：Android Studio（推荐）

1. 用 Android Studio（至少 Ladybug / 2024.2.1 以上，含 SDK 36 支持）打开本目录 `AstrBotControl`。
2. 首次打开会自动同步 Gradle（使用项目内的 Gradle Wrapper 8.14.2）。
3. 菜单 `Build → Build App Bundle(s) / APK(s) → Build APK(s)`，或点击 Run 直接安装到手机。

### 方式二：命令行

```bash
# 需要 JDK 17+，并设置 ANDROID_HOME 指向 Android SDK（含 platforms;android-36）
./gradlew assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

---

## 使用说明

1. **准备 AstrBot 服务器**
   - 启动 AstrBot，确认 Web 控制台（Dashboard）可访问：浏览器打开 `http://服务器IP:6185`（端口可在 `data/cmd_config.json` 中修改，默认 6185）。
   - 确保服务器防火墙允许手机访问该端口；同一局域网直接填内网 IP，跨网络需公网 IP/端口映射或内网穿透。

2. **安装并登录**
   - 安装 APK 后打开应用，填写服务器 IP、端口（默认 6185）、用户名与密码（与网页控制台相同）。
   - 可先点「测试连接」验证可达性，再点「登录」。

3. **使用各功能**
   - 底部导航：状态 / 插件 / 配置 / 日志 / 更多。
   - 「更多」中包含平台、提供方、定时任务、会话、技能、备份、更新、API 密钥、工具/MCP、人设、知识库、子代理、对话测试、连接设置。

---

## 技术说明

- 语言/框架：Kotlin + Jetpack Compose (Material 3)，单 Activity + Navigation Compose。
- 网络：OkHttp 4（REST + multipart 上传 + SSE 流式日志/对话），`org.json` 动态解析（对字段结构变化鲁棒）。
- 配置存储：Jetpack DataStore（保存服务器地址、账号与登录 Token；Token 用于 Bearer 认证）。
- 明文流量：默认允许 HTTP 明文（局域网内 AstrBot 控制台通常无 TLS），见 `res/xml/network_security_config.xml`。
- 主要 API：
  - 登录：`POST /api/auth/login` → `data.token`
  - 插件（兼容 v3.5/v4）：`GET /api/plugin/get`、`POST /api/plugin/on|off|update|uninstall|reload|install|install-upload`、`GET /api/plugin/market_list`
  - 其余：`/api/v1/stats`、`/api/v1/config-profiles`、`/api/v1/bots`、`/api/v1/providers`、`/api/v1/cron/jobs`、`/api/v1/logs/history|live`、`/api/v1/backups`、`/api/v1/updates/*`、`/api/v1/api-keys`、`/api/v1/skills`、`/api/v1/tools`、`/api/v1/mcp/servers`、`/api/v1/personas`、`/api/v1/knowledge-bases`、`/api/v1/subagents/config`、`/api/v1/chat/*` 等。

## 安全设计

针对"公网暴露"与"凭据泄露"风险，本应用做了如下加固：

1. **凭据加密落盘**：登录 Token 与密码使用 **Android Keystore + AES/GCM-256** 加密后存入 DataStore，密钥保存在系统级安全硬件/Keystore 中，应用进程无法导出；即使备份出数据文件也无法直接读取明文。
2. **公网连接提示（不拦截）**：AstrBot 控制台本身仅支持 HTTP 直连，公网 IP 场景照常可用；应用会在连接页展示安全提示（建议反向代理 HTTPS / 强密码 / 两步验证），并支持手动勾选 HTTPS（适用于已配置反向代理 TLS 的服务器）。
3. **不记录敏感信息**：应用不写任何日志输出 Token/密码；API 密钥创建后仅在弹窗中显示一次，与官方控制台行为一致。
4. **明文流量说明**：`network_security_config.xml` 允许明文是为了直连 AstrBot 的 HTTP 控制台；若服务器已配置 HTTPS 反向代理，请勾选「使用 HTTPS」（OkHttp 默认校验证书链）。
5. **服务器侧建议**：不要把 AstrBot 控制台直接暴露到公网；若必须，请配置反向代理 + TLS、强密码、两步验证，并限制管理端口来源 IP。

> 提示：release 签名凭据**不存放在代码库中**。构建 release 时请在 `local.properties`（已 gitignore）或环境变量中配置 `RELEASE_KEYSTORE` / `RELEASE_STORE_PASSWORD` / `RELEASE_KEY_ALIAS` / `RELEASE_KEY_PASSWORD`；未配置时仅 `assembleDebug` 可用。请自行生成并妥善保管你的签名密钥，泄露或丢失都会影响应用更新与安全。

## 发布流程（每次更新产出两个 APK）

| 产物 | 用途 | 签名 |
| --- | --- | --- |
| `AstrBotControl-vX.Y.Z.apk` | 自用安装 | 个人签名（`release.keystore`） |
| `AstrBotControl-vX.Y.Z-release.apk` | 发布到 GitHub Releases | 发布签名（`release_public.keystore`） |

- 自用版：`local.properties` 配置个人签名后 `./gradlew assembleRelease`
- 发布版：设置环境变量 `RELEASE_KEYSTORE=release_public.keystore` / `RELEASE_STORE_PASSWORD` / `RELEASE_KEY_PASSWORD` 后 `./gradlew assembleRelease`
- 两把密钥凭据均不入库；发布密钥务必妥善保管（丢失将无法为已发布版本提供更新）

## 免责声明

本项目为第三方客户端，与 AstrBot 官方无关。AstrBot 接口可能随版本变化，如遇接口不兼容，请升级 AstrBot 或反馈问题。
