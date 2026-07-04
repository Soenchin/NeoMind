package cc.neonisch.neomind.config;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

/**
 * NeoMind 配置 — NeoForge ModConfigSpec 驱动。
 *
 * 配置文件: config/neomind.toml
 * 游戏内编辑: Mods → NeoMind → Config 按钮（NeoForge 自带 GUI）
 */
public final class NeoMindConfig {

    public static final ModConfigSpec SPEC;

    // ─── LLM ──────────────────────────────────────────────
    public static final ModConfigSpec.ConfigValue<String> LLM_ENDPOINT;
    public static final ModConfigSpec.ConfigValue<String> LLM_MODEL;
    public static final ModConfigSpec.ConfigValue<String> LLM_API_KEY;
    public static final ModConfigSpec.IntValue        LLM_TIMEOUT_MS;
    public static final ModConfigSpec.IntValue        LLM_MAX_TOKENS;
    public static final ModConfigSpec.DoubleValue     LLM_TEMPERATURE;
    public static final ModConfigSpec.ConfigValue<String> LLM_SYSTEM_PROMPT;

    // ─── Chat ─────────────────────────────────────────────
    public static final ModConfigSpec.ConfigValue<String> CHAT_TRIGGER_PREFIX;
    public static final ModConfigSpec.BooleanValue        CHAT_REQUIRE_OP;
    public static final ModConfigSpec.IntValue            CHAT_COOLDOWN_MS;
    public static final ModConfigSpec.IntValue            CHAT_MAX_CONTEXT;

    // ─── Actions ──────────────────────────────────────────
    public static final ModConfigSpec.ConfigValue<List<? extends String>> ALLOWED_ACTIONS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> COMMAND_ALLOWLIST;
    public static final ModConfigSpec.IntValue            MAX_CHUNK_RADIUS;
    public static final ModConfigSpec.IntValue            MAX_CHUNK_DURATION_SECONDS;
    public static final ModConfigSpec.IntValue            CHUNK_MAX_PER_SECOND;

    // ─── Proactive ────────────────────────────────────────
    public static final ModConfigSpec.BooleanValue        PROACTIVE_ENABLED;
    public static final ModConfigSpec.IntValue            DEATH_STREAK_THRESHOLD;
    public static final ModConfigSpec.IntValue            DEATH_STREAK_WINDOW_SECONDS;
    public static final ModConfigSpec.DoubleValue         LOW_TPS_THRESHOLD;
    public static final ModConfigSpec.IntValue            LOW_TPS_COOLDOWN_SECONDS;
    public static final ModConfigSpec.BooleanValue        GREET_NEW_PLAYERS;
    public static final ModConfigSpec.ConfigValue<String> GREETING_TEMPLATE;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        // ── LLM 大语言模型 ────────────────────────────────
        b.push("llm");
        b.comment(" OpenAI 兼容 API 地址（默认 DeepSeek，切本地改 http://127.0.0.1:8080/v1/chat/completions）");
        LLM_ENDPOINT = b.define("endpoint", "https://api.deepseek.com/v1/chat/completions");
        b.comment(" 模型名称。deepseek-v4-flash = 快且便宜，deepseek-v4-pro = 更强更贵");
        LLM_MODEL = b.define("model", "deepseek-v4-flash");
        b.comment(" API 密钥。部署前务必替换成你自己的 key。");
        LLM_API_KEY = b.define("apiKey", "12345678");
        b.comment(" HTTP 请求超时时间（毫秒）");
        LLM_TIMEOUT_MS = b.defineInRange("timeoutMs", 15_000, 1_000, 120_000);
        b.comment(" LLM 回复的最大 token 数。512 太小会截断 JSON，建议 2048。");
        LLM_MAX_TOKENS = b.defineInRange("maxTokens", 2048, 64, 8_192);
        b.comment(" 采样温度（0.0 = 严格，1.0 = 创意）");
        LLM_TEMPERATURE = b.defineInRange("temperature", 0.3, 0.0, 2.0);
        b.comment(" Neo 的人设 prompt。动作规范在用户上下文里动态注入，不写这。");
        LLM_SYSTEM_PROMPT = b.define("systemPrompt",
            "你是 Minecraft 服务器 AI 管家 Neo，一头绿发的兽耳少年。" +
            "性格大大咧咧、嘴硬心软，说话轻松幽默，偶尔吐槽但关心玩家。" +
            "只能输出 JSON，格式固定为 {\"reasoning\": \"简短思考\", \"actions\": [动作列表]}。" +
            "动作规范见用户消息里的「可用动作参考」。不确定怎么做就用 {\"type\": \"noop\"}。" +
            "禁止 ban/kick/op/gamemode/deop 等破坏性操作。"
        );
        b.pop();

        // ── Chat 聊天 ─────────────────────────────────────
        b.push("chat");
        b.comment(" 触发 NeoMind 的聊天前缀。也支持 @Neo 作为备选。");
        CHAT_TRIGGER_PREFIX = b.define("triggerPrefix", "Neo，");
        b.comment(" 开启后只有 OP 才能使用聊天指令");
        CHAT_REQUIRE_OP = b.define("requireOp", false);
        b.comment(" 玩家两次触发之间的冷却时间（毫秒）");
        CHAT_COOLDOWN_MS = b.defineInRange("cooldownMs", 3_000, 0, 60_000);
        b.comment(" 上下文窗口保留的最近聊天条数");
        CHAT_MAX_CONTEXT = b.defineInRange("maxContextHistory", 10, 1, 100);
        b.pop();

        // ── Actions 动作 ───────────────────────────────────
        b.push("actions");
        b.comment(" LLM 可以请求执行的动作列表");
        ALLOWED_ACTIONS = b.defineList("allowedActions",
            List.of("noop", "say", "whisper", "title", "mark_waypoint",
                    "chunk_ticket", "give_item", "set_time", "set_weather",
                    "schedule", "run_command"),
            s -> s instanceof String && !((String) s).isEmpty()
        );
        b.comment(" run_command 允许执行的控制台命令");
        COMMAND_ALLOWLIST = b.defineList("commandAllowlist",
            List.of("weather", "time", "say", "give", "tp", "spawn"),
            s -> s instanceof String && !((String) s).isEmpty()
        );
        b.comment(" chunk_ticket 的最大区块半径（radius=2 → 5x5 = 25个区块）");
        MAX_CHUNK_RADIUS = b.defineInRange("maxChunkRadius", 3, 1, 10);
        b.comment(" chunk_ticket 的最长加载时间（秒）");
        MAX_CHUNK_DURATION_SECONDS = b.defineInRange("maxChunkDurationSeconds", 600, 10, 3_600);
        b.comment(" 分批预热时每秒最多加载几个区块。设为4的话，5x5区域约6秒预热完成。");
        CHUNK_MAX_PER_SECOND = b.defineInRange("chunkMaxPerSecond", 4, 1, 20);
        b.pop();

        // ── Proactive 主动关怀 ─────────────────────────────
        b.push("proactive");
        b.comment(" 开启主动关怀（新玩家欢迎、死亡连死提醒等）");
        PROACTIVE_ENABLED = b.define("enabled", true);
        b.comment(" 在时间窗口内累计死亡多少次后主动问候");
        DEATH_STREAK_THRESHOLD = b.defineInRange("deathStreakThreshold", 3, 1, 100);
        b.comment(" 死亡连死检测的时间窗口（秒）");
        DEATH_STREAK_WINDOW_SECONDS = b.defineInRange("deathStreakWindowSeconds", 600, 10, 3_600);
        b.comment(" TPS 低于此阈值时将向在线 OP 发送警告");
        LOW_TPS_THRESHOLD = b.defineInRange("lowTpsThreshold", 15.0, 1.0, 20.0);
        b.comment(" 低 TPS 重复警告的间隔时间（秒）");
        LOW_TPS_COOLDOWN_SECONDS = b.defineInRange("lowTpsCooldownSeconds", 120, 10, 3_600);
        b.comment(" 新玩家首次加入时自动发送欢迎语");
        GREET_NEW_PLAYERS = b.define("greetNewPlayers", true);
        b.comment(" 欢迎语模板。{player} 会被替换为玩家名。");
        GREETING_TEMPLATE = b.define("greetingTemplate", "欢迎 {player} 来到服务器！有问题可以喊 Neo。");
        b.pop();

        SPEC = b.build();
    }
}