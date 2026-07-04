package cc.neonisch.neomind.chat;

import cc.neonisch.neomind.action.ActionExecutor;
import cc.neonisch.neomind.command.SightApiClient;
import cc.neonisch.neomind.config.NeoMindConfig;
import cc.neonisch.neomind.llm.ActionPlan;
import cc.neonisch.neomind.llm.LLMClient;
import com.google.gson.JsonObject;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Listens for chat events with the trigger prefix and delegates to the LLM pipeline.
 *
 * Pipeline:
 *   chat triggered → build context → LLM call (background) → parse → execute (main thread)
 */
public final class ChatEventHandler {

    private static final Logger LOG = LoggerFactory.getLogger("neomind");

    private final ActionExecutor executor;
    private final MinecraftServer server;
    private final boolean sightApiAvailable;

    /** Recent chat messages (player → string), capped at maxContextHistory. */
    private final Deque<String> recentChat = new ArrayDeque<>();

    /** Per-player cooldown timestamps. */
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    /** Background thread for LLM calls. */
    private final ExecutorService llmThread = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "NeoMind-LLM");
        t.setDaemon(true);
        return t;
    });

    public ChatEventHandler(ActionExecutor executor, MinecraftServer server, boolean sightApiAvailable) {
        this.executor = executor;
        this.server = server;
        this.sightApiAvailable = sightApiAvailable;
    }

    @SubscribeEvent
    public void onChat(ServerChatEvent event) {
        String raw = event.getMessage().getString();
        ServerPlayer player = event.getPlayer();
        String playerName = player.getGameProfile().getName();

        // ── Always record chat for context history ──
        recordChat(playerName + ": " + raw);

        // ── Check trigger prefix ──
        String prefix = NeoMindConfig.CHAT_TRIGGER_PREFIX.get();
        if (!raw.startsWith(prefix)) return;

        String prompt = raw.substring(prefix.length()).trim();
        if (prompt.isEmpty()) return;

        // ── Permission check ──
        if (NeoMindConfig.CHAT_REQUIRE_OP.get() && !player.hasPermissions(2)) {
            player.sendSystemMessage(Component.literal("[NM] 只有 OP 才能使用聊天指令哦。"));
            return;
        }

        // ── Cooldown check ──
        long now = System.currentTimeMillis();
        Long last = cooldowns.get(player.getUUID());
        if (last != null && (now - last) < NeoMindConfig.CHAT_COOLDOWN_MS.get()) {
            player.sendSystemMessage(Component.literal("[NM] 冷却中，稍等一下~"));
            return;
        }
        cooldowns.put(player.getUUID(), now);

        // ── Build context ──
        String context = buildContext(player, prompt);

        // ── Fire LLM on background thread ──
        final String frozenContext = context;
        final String frozenPlayerName = playerName;

        llmThread.submit(() -> {
            ActionPlan plan = LLMClient.ask(
                    NeoMindConfig.LLM_ENDPOINT.get(),
                    NeoMindConfig.LLM_MODEL.get(),
                    NeoMindConfig.LLM_API_KEY.get(),
                    NeoMindConfig.LLM_TIMEOUT_MS.get(),
                    NeoMindConfig.LLM_MAX_TOKENS.get(),
                    NeoMindConfig.LLM_TEMPERATURE.get(),
                    NeoMindConfig.LLM_SYSTEM_PROMPT.get(),
                    frozenContext
            );

            if (plan == null) {
                server.execute(() -> {
                    ServerPlayer p = server.getPlayerList().getPlayerByName(frozenPlayerName);
                    if (p != null) {
                        p.sendSystemMessage(Component.literal("[NM] 我现在有点卡，稍后再试~"));
                    }
                });
                return;
            }

            LOG.info("LLM plan for '{}': {} → {} actions", frozenPlayerName, plan.reasoning(), plan.actions().size());

            // Check whether LLM returned only noop(s) — that means it has reasoning but no actions
            boolean onlyNoop = plan.actions().stream()
                    .allMatch(a -> "noop".equals(cc.neonisch.neomind.llm.ActionPlan.getString(a, "type")));

            if (onlyNoop) {
                server.execute(() -> {
                    ServerPlayer p = server.getPlayerList().getPlayerByName(frozenPlayerName);
                    if (p == null) return;
                    if (!plan.reasoning().isEmpty()) {
                        p.sendSystemMessage(Component.literal("[NM] " + clipReasoning(plan.reasoning())));
                    } else {
                        p.sendSystemMessage(Component.literal("[NM] 抱歉，我不太确定该怎么回答这个，换个问法试试？"));
                    }
                });
            } else {
                server.execute(() -> {
                    int total = plan.actions().size();
                    int done = executor.executeAll(plan.actions(), frozenPlayerName);
                    LOG.info("Executed {}/{} actions for {}", done, total, frozenPlayerName);
                    ServerPlayer p = server.getPlayerList().getPlayerByName(frozenPlayerName);
                    if (p == null) return;
                    if (done == 0) {
                        p.sendSystemMessage(Component.literal("[NM] 抱歉，我不太确定该怎么回答这个，换个问法试试？"));
                    } else if (done < total) {
                        p.sendSystemMessage(Component.literal("[NM] 部分操作未能执行（" + done + "/" + total + "），可能是 LLM 返回的 JSON 不完整。"));
                    }
                });
            }
        });
    }

    /** 动作字段参考，每次请求注入用户上下文。跟 ActionExecutor 的 dispatch() 保持同步。 */
    private static final String ACTIONS_REFERENCE = """
        
        可用动作参考（严格遵守字段名，缺失字段导致动作失败）:
        - say:             {"type":"say", "message":"广播内容"}
        - whisper:         {"type":"whisper", "player":"玩家名", "message":"私聊内容"}
        - title:           {"type":"title", "player":"玩家名", "title":"大标题", "subtitle":"副标题"}
        - give_item:       {"type":"give_item", "player":"玩家名", "item":"物品ID", "count":数量}
        - teleport:        {"type":"teleport", "player":"玩家名", "x":0.0, "y":64.0, "z":0.0, "dimension":"minecraft:overworld"}
        - set_time:        {"type":"set_time", "time":"day/noon/night/midnight"}
        - set_weather:     {"type":"set_weather", "weather":"clear/rain/thunder"}
        - run_command:     {"type":"run_command", "command":"控制台指令"}
        - scan_entities:   {"type":"scan_entities", "entity":"minecraft:pig"}  (扫描3x3区块内实体，返回数量和方位)
        - scan_blocks:     {"type":"scan_blocks", "block":"minecraft:sand"}  (扫描3x3区块地表方块，返回数量和最近方位)
        - detect_structure:{"type":"detect_structure"}  (检测附近非玩家结构如村庄/地狱门遗迹/神殿等)
        - look_at:         {"type":"look_at"}  (看你面前是什么方块)
        - noop:            {"type":"noop"}  (不知道怎么做时用这个)
        命令白名单: weather, time, say, give, tp, spawn
        """;

    private String buildContext(ServerPlayer player, String prompt) {
        StringBuilder sb = new StringBuilder();

        // Current request
        sb.append("玩家 ").append(player.getGameProfile().getName()).append(" 说：").append(prompt);

        sb.append("\n\n最近聊天:\n");
        synchronized (recentChat) {
            for (String msg : recentChat) {
                sb.append("  - ").append(msg).append("\n");
            }
        }

        sb.append("\n当前玩家状态:\n");
        sb.append("  坐标: ").append(String.format("%.1f, %.1f, %.1f", player.getX(), player.getY(), player.getZ()));
        sb.append("\n  维度: ").append(player.level().dimension().location());
        sb.append("\n  生命: ").append(String.format("%.0f/%.0f", player.getHealth(), player.getMaxHealth()));
        sb.append("\n  食物: ").append(player.getFoodData().getFoodLevel());

        sb.append("\n服务器: ").append(server.getPlayerCount()).append("/").append(server.getMaxPlayers()).append(" 人在线");

        // ── Region context (if NeoSightAPI available) ──
        if (sightApiAvailable) {
            try {
                var regions = SightApiClient.findRegionsAt(
                        (int) player.getX(), (int) player.getZ(),
                        player.level().dimension().location().toString()
                );
                if (!regions.isEmpty()) {
                    sb.append("\n当前位置位于以下区域:\n");
                    for (JsonObject r : regions) {
                        sb.append("  - ").append(r.get("name").getAsString());
                        String label = r.has("label") ? r.get("label").getAsString() : "";
                        if (!label.isEmpty()) sb.append(": ").append(label);
                        sb.append(" (属于 ").append(r.get("owner").getAsString()).append(")");
                        sb.append("\n");
                    }
                }
            } catch (Exception e) {
                LOG.debug("SightAPI unreachable for region query", e);
            }
        }

        sb.append(ACTIONS_REFERENCE);

        return sb.toString();
    }

    private void recordChat(String msg) {
        synchronized (recentChat) {
            recentChat.addLast(msg);
            while (recentChat.size() > NeoMindConfig.CHAT_MAX_CONTEXT.get()) {
                recentChat.removeFirst();
            }
        }
    }

    /** Minecraft chat limit is ~256 chars; clip at 220 to leave room for prefix. */
    private static String clipReasoning(String msg) {
        if (msg.length() <= 220) return msg;
        return msg.substring(0, 217) + "...";
    }
}