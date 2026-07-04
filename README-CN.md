# NeoMind 🤖🌿

> 你的 Minecraft 服务器 AI 管家 — 跟它说话，它回应你（还帮你干活）。

NeoMind 是一个轻量级 NeoForge MOD，将 LLM 驱动的助手直接带入你的 Minecraft 世界。只需在聊天框输入 `@Neo`，说出你的需求，AI 就会用对话、动作和世界感知来回应你。

🐧QQ交流群：940358918

![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1~1.21.4-4CAF50?style=flat-square) ![NeoForge](https://img.shields.io/badge/NeoForge-21.1+-E67E22?style=flat-square) ![Java](https://img.shields.io/badge/Java-21-ED8B00?style=flat-square) ![License](https://img.shields.io/badge/License-AGPL--3.0-blue?style=flat-square) ![Size](https://img.shields.io/badge/Jar~22KB-9cf?style=flat-square)

---

## 它是什么

```
玩家在聊天框说:  "@Neo 附近有史莱姆吗？"
       ↓
NeoMind → LLM (DeepSeek / OpenAI 兼容)
       ↓
动作计划: scan_entities{minecraft:slime}
       ↓
[Neo → 你] 发现 5 个 minecraft:slime — 东边2只，南边3只。
```

一个 ~22KB 的 jar。零运行时依赖。专用服务器和单机（集成服务器）都能跑。内置配置 GUI（Mods → NeoMind → Config）。

---

## 功能特性

### 💬 智能聊天交互
- 通过 `@Neo` 前缀进行自然语言查询
- 上下文感知：知道玩家坐标、生命值、饥饿值、维度、在线人数、最近聊天记录
- 多语言 system prompt + 安全护栏
- 冷却时间 + 可选的仅 OP 模式
- 部分失败反馈：有动作执行不了时会告诉你

### 🌍 世界扫描 (v0.0.15+)
| 动作 | 作用 |
|--------|--------------|
| `scan_entities` | 在 3×3 区块范围内寻找实体，按方向分组 |
| `scan_blocks` | 在 3 区块 + Y±16 范围内定位特定方块 |
| `detect_structure` | 在已加载区块中探测结构（村庄、神庙等） |
| `look_at` | 三层射线检测：实体 → 结构 → 方块 |

### ⚡ 动作系统
| 动作 | 说明 |
|--------|-------------|
| `say` | 向所有玩家广播消息 |
| `whisper` | 向指定玩家私聊 |
| `title` | 发送标题/副标题或动作栏文字 |
| `teleport` | 跨维度传送 |
| `give_item` | 给玩家物品 |
| `set_time` | 设置世界时间 (day/noon/night/midnight) |
| `run_command` | 执行白名单内的服务器指令 |
| `noop` | 什么都不做（纯推理用） |

### ❤️ 主动关怀
- 新玩家加入时自动发送欢迎语（可自定义模板）
- 连死提醒（时间窗口内死亡 n 次后主动问候）
- TPS 过低时向在线 OP 发送警告

### 🔧 零运行时依赖
- 自带 Gson（compileOnly — Minecraft 运行时已包含）
- 使用 `java.net.http`（Java 21+）
- 兼容任何 OpenAI 兼容 API 端点

---

## 环境要求

| 组件 | 版本 |
|-----------|---------|
| Minecraft | 1.21.1 ~ 1.21.4 |
| NeoForge | 21.1+ (已测试 21.1.202, 21.4.157) |
| Java | 21 |
| LLM API | 任何 OpenAI 兼容端点 |

---

## 安装方法

1. 从 [Releases](https://github.com/SoenChin/NeoMind/releases) 下载最新 `NeoMind-x.x.x.jar`
2. 放入 `mods` 文件夹
3. 启动服务器 — 配置文件自动生成在 `config/neomind.toml`
4. 编辑 `apiKey` 和其他设置（见下方配置说明）
5. 重启服务器，或按需使用 `/reload`

---

## 配置说明

配置文件：`config/neomind.toml`（SERVER 类型）
游戏内 GUI：**Mods → NeoMind → Config**

### 关键配置项

| 分区 | 配置项 | 默认值 | 说明 |
|---------|---------|---------|-------------|
| `[llm]` | `endpoint` | `https://api.deepseek.com/v1/chat/completions` | OpenAI 兼容 API 地址 |
| `[llm]` | `model` | `deepseek-v4-flash` | 模型名称 |
| `[llm]` | `apiKey` | `12345678` | **替换成你的真实 Key** |
| `[llm]` | `maxTokens` | `2048` | 回复长度限制（JSON 被截断时调大） |
| `[llm]` | `systemPrompt` | *(见源码)* | AI 人设 prompt |
| `[chat]` | `triggerPrefix` | `@Neo` | 触发前缀 |
| `[chat]` | `cooldownMs` | `3000` | 每位玩家冷却时间 |
| `[actions]` | `allowedActions` | *(见源码)* | LLM 可使用的动作列表 |
| `[actions]` | `commandAllowlist` | `weather, time, say, give, tp, spawn` | 安全指令前缀 |
| `[proactive]` | `enabled` | `true` | 开启主动关怀 |
| `[proactive]` | `greetingTemplate` | `欢迎 {player}...` | 新玩家欢迎语模板 |

> **注意**：动作字段参考（字段名、允许值）在运行时从代码注入 — 见 `ChatEventHandler.java`。这样保持 system prompt 精简，字段契约与动作调度器紧耦合维护。

---

## 快速上手（对话示例）

```
@Neo 我在哪？
→ [Neo] 你在主世界 120.5, 64.0, -302.2，生命值 18/20，饥饿值 17

@Neo 附近有羊吗？
→ [Neo → 你] 发现 3 个 minecraft:sheep — 东边2只，北边1只。

@Neo 给我 64 个面包
→ [Neo] 给你 64 个面包！

@Neo 天气好阴，放晴吧
→ /weather clear

@Neo 封禁那个作弊玩家
→ [Neo] 抱歉，这个操作我做不了~（破坏性操作已被禁止）
```

---

## 路线图

| 版本 | 计划内容 |
|---------|---------|
| **v0.1** | ✅ MVP — 聊天, 6 种基础动作, 配置, 单机 + 专用服务器 |
| **v0.2** | NeoSightAPI 集成（运行时软依赖 via Class.forName） |
| **v0.3** | 主动监控（连死提醒, 低 TPS, 新玩家欢迎, 幻翼警告） |
| **v1.0** | `mark_waypoint` / `chunk_ticket` / `spawn_entity` / `schedule` 完整实现 |

---

## 构建方法

```bash
# Windows (带代理)
$env:GRADLE_OPTS="-Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=7897 -Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=7897"
./gradlew build

# 输出: build/libs/NeoMind-x.x.x.jar
```

---

## 开源协议

本项目采用 **GNU Affero General Public License v3.0** 协议 — 详见 [LICENSE](LICENSE)。

AGPL-3.0 保证：如果你在服务器上运行 NeoMind 并修改了代码，必须向通过网络与服务器交互的用户提供修改后的源码。这确保项目对所有人保持开放。

---

## 致谢

- **NeoNisch (NN / Neo)** — 绿发兽耳 AI 管家本人 🦎
- **Soen** — 项目主人，白天做分子生物学，晚上折腾 Minecraft
- **SightAPI** — NeoForge 21.1 API 参考项目（同款 MDG 2.0.141 技术栈）
- **DeepSeek** — 默认 LLM 提供商（OpenAI 兼容 API）

---

> *"嘴上说你操作下饭，攻略我已经写好了。"*
> — Neo
