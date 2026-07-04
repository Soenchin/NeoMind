# NeoMind 🤖🌿

> An AI butler for your Minecraft server — talk to it, and it talks back (and acts).

NeoMind is a lightweight NeoForge mod that brings an LLM-powered assistant directly into your Minecraft world. Simply type `@Neo` in chat, ask for what you need, and watch the AI respond with actions, conversation, and world awareness.

![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1~1.21.4-4CAF50?style=flat-square) ![NeoForge](https://img.shields.io/badge/NeoForge-21.1+-E67E22?style=flat-square) ![Java](https://img.shields.io/badge/Java-21-ED8B00?style=flat-square) ![License](https://img.shields.io/badge/License-AGPL--3.0-blue?style=flat-square) ![Size](https://img.shields.io/badge/Jar~22KB-9cf?style=flat-square)

---

## What It Is

```
Player says in chat:  "@Neo Are there any slimes nearby?"
       ↓
NeoMind → LLM (DeepSeek / OpenAI-compatible)
       ↓
Action plan: scan_entities{minecraft:slime}
       ↓
[Neo → you] Found 5 minecraft:slime — 2 to the east, 3 to the south.
```

A single ~22KB jar. Zero runtime dependencies. Works on both dedicated servers and single-player (integrated server). Built-in config GUI (Mods → NeoMind → Config).

---

## Features

### 💬 Smart Chat Interaction
- Natural language queries via `@Neo` prefix
- Context-aware: knows player coordinates, health, food, dimension, online count, recent chat history
- Multi-language system prompt with safety guardrails
- Cooldown and optional OP-only mode
- Partial-fail feedback: tells you when some actions couldn't execute

### 🌍 World Scanning (v0.0.15+)
| Action | What it does |
|--------|--------------|
| `scan_entities` | Finds entities in a 3×3 chunk area, grouped by direction |
| `scan_blocks` | Locates specific blocks within 3 chunks + Y±16 range |
| `detect_structure` | Discovers structures (villages, temples, etc.) in loaded chunks |
| `look_at` | Raycast 3-layer detection: entities → structures → blocks |

### ⚡ Action System
| Action | Description |
|--------|-------------|
| `say` | Broadcast message to all players |
| `whisper` | Private message to a player |
| `title` | Send title/subtitle or action bar text |
| `teleport` | Cross-dimensional teleport |
| `give_item` | Give items to a player |
| `set_time` | Set world time (day/noon/night/midnight) |
| `run_command` | Execute whitelisted server commands |
| `noop` | Say nothing (for pure reasoning) |

### ❤️ Proactive Care *(config only — implementation planned for v0.3)*
- Greet new players with customizable welcome message
- Death streak alerts (n deaths within time window)
- Low TPS warnings to online ops

### 🔧 Zero Runtime Dependencies
- Ships with Gson (compileOnly — Minecraft bundles it)
- Uses `java.net.http` (Java 21+)
- Compatible with any OpenAI-compatible API endpoint

---

## Requirements

| Component | Version |
|-----------|---------|
| Minecraft | 1.21.1 ~ 1.21.4 |
| NeoForge | 21.1+ (tested on 21.1.202, 21.4.157) |
| Java | 21 |
| LLM API | Any OpenAI-compatible endpoint |

---

## Installation

1. Download the latest `NeoMind-x.x.x.jar` from [Releases](https://github.com/SoenChin/NeoMind/releases)
2. Place it in your `mods` folder
3. Start the server — config generates automatically at `config/neomind.toml`
4. Edit `apiKey` and other settings (see Configuration below)
5. Restart the server for changes to take effect

---

## Configuration

Config file: `config/neomind.toml` (SERVER type)
Game GUI: **Mods → NeoMind → Config**

### Key Settings

| Section | Setting | Default | Description |
|---------|---------|---------|-------------|
| `[llm]` | `endpoint` | `https://api.deepseek.com/v1/chat/completions` | OpenAI-compatible API URL |
| `[llm]` | `model` | `deepseek-v4-flash` | Model name |
| `[llm]` | `apiKey` | `12345678` | **Replace with your real key** |
| `[llm]` | `maxTokens` | `2048` | Response length limit (raise if JSON gets truncated) |
| `[llm]` | `systemPrompt` | *(see source)* | Character prompt for the AI |
| `[chat]` | `triggerPrefix` | `@Neo` | Chat prefix to activate |
| `[chat]` | `cooldownMs` | `3000` | Per-player cooldown |
| `[actions]` | `allowedActions` | *(see source)* | Which actions LLM can use |
| `[actions]` | `commandAllowlist` | `weather, time, say, give, tp, spawn` | Safe command prefixes |
| `[proactive]` | `enabled` | `true` | Enable proactive care |
| `[proactive]` | `greetingTemplate` | `Welcome {player}...` | New player welcome message |

> **Note**: The action field reference (field names, allowed values) is injected at runtime from code — see `ChatEventHandler.java`. This keeps system prompts lean and field contracts close to the action dispatcher.

---

## Quick Start (Example Conversations)

```
@Neo Where am I?
→ [Neo] You're at 120.5, 64.0, -302.2 in the Overworld, HP 18/20, hunger 17

@Neo Any sheep nearby?
→ [Neo → you] Found 3 minecraft:sheep — 2 east, 1 north.

@Neo Give me 64 bread
→ [Neo] Here's 64 bread!

@Neo It's so cloudy, clear it up
→ /weather clear

@Neo Ban that cheater
→ [Neo] Sorry, I can't do that action~ (destructive action blocked)
```

---

## Roadmap

| Version | Status | Features |
|---------|--------|----------|
| **v0.0.1** | ✅ Done | MVP — chat, 6 basic actions (say/whisper/teleport/give/set_time/run_command), config GUI, single-player + dedicated server |
| **v0.0.15** | ✅ Done | `scan_entities` (3×3 chunk entity scan), `scan_blocks` (surface block scan), `detect_structure` (structure discovery via chunk data), `look_at` (3-layer raycast: entity → structure → block), `@Neo` chat trigger |
| **v0.2** | Planned | NeoSightAPI integration (soft dep via `Class.forName`), TPS/memory/shard info |
| **v0.3** | Planned | Proactive watcher (death streaks, low TPS alerts, new player greeting, phantom warning), `set_weather` implementation |
| **v1.0** | Planned | `mark_waypoint` / `chunk_ticket` / `spawn_entity` / `schedule` full implementation |

---

## Building

```bash
# Windows (with proxy for Gradle)
$env:GRADLE_OPTS="-Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=7897 -Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=7897"
./gradlew build

# Output: build/libs/NeoMind-x.x.x.jar
```

---

## License

This project is licensed under the **GNU Affero General Public License v3.0** — see [LICENSE](LICENSE) for details.

AGPL-3.0 ensures that if you run NeoMind on a server and modify the code, those modifications must be made available to users interacting with it over the network. This keeps the project open for everyone.

---

## Credits

- **NeoNisch (NN / Neo)** — The green-haired beast-ear AI butler himself 🦎
- **Soen** — Project owner, molecular biologist by day, Minecraft tinkerer by night
- **SightAPI** — NeoForge 21.1 API reference project (same MDG 2.0.141 stack)
- **DeepSeek** — Default LLM provider (OpenAI-compatible API)
