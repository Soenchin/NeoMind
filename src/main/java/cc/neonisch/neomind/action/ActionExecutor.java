package cc.neonisch.neomind.action;

import com.google.gson.JsonObject;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static cc.neonisch.neomind.llm.ActionPlan.*;

/**
 * Executes actions from a parsed ActionPlan.
 * All methods assume they are called on the main server thread.
 */
public final class ActionExecutor {

    private static final Logger LOG = LoggerFactory.getLogger("neomind");

    private final MinecraftServer server;
    private final List<String> allowedActions;
    private final List<String> commandAllowlist;

    public ActionExecutor(MinecraftServer server, List<? extends String> allowed, List<? extends String> commands) {
        this.server = server;
        this.allowedActions = List.copyOf(allowed);
        this.commandAllowlist = List.copyOf(commands);
    }

    /**
     * Execute every action in the plan. Returns the number successfully executed.
     */
    public int executeAll(List<JsonObject> actions, String playerName) {
        int count = 0;
        for (JsonObject action : actions) {
            String type = getString(action, "type");
            if (type == null || !allowedActions.contains(type)) {
                LOG.warn("Skipping unapproved action: {}", type);
                continue;
            }
            if (dispatch(type, action, playerName)) count++;
        }
        return count;
    }

    private boolean dispatch(String type, JsonObject a, String player) {
        try {
            return switch (type) {
                case "noop"          -> true;
                case "say"           -> say(readMessage(a));
                case "whisper"       -> whisper(player, readMessage(a));
                case "title"         -> title(getString(a, "player"), getString(a, "title"),
                                              getString(a, "subtitle"), getBool(a, "action_bar", false));
                case "run_command"   -> runCommand(getString(a, "command"));
                case "set_time"      -> setTime(getString(a, "dimension"), getString(a, "time"));
                case "give_item"     -> giveItem(getString(a, "player"), getString(a, "item"), getInt(a, "count", 1));
                case "teleport"      -> teleport(getString(a, "player"), getString(a, "dimension"),
                                                  getDouble(a, "x", 0), getDouble(a, "y", 64), getDouble(a, "z", 0));
                // MVP skips these — noop with log
                case "mark_waypoint" -> { LOG.info("waypoint {} for {} skipped (not in MVP)", getString(a, "label"), getString(a, "player")); yield true; }
                case "chunk_ticket"  -> { LOG.info("chunk_ticket skipped (not in MVP)"); yield true; }
                case "set_weather"   -> { LOG.info("set_weather skipped (not in MVP)"); yield true; }
                case "spawn_entity"  -> { LOG.info("spawn_entity skipped (not in MVP)"); yield true; }
                case "schedule"      -> { LOG.info("schedule skipped (not in MVP)"); yield true; }
                default              -> { LOG.warn("Unknown action type: {}", type); yield false; }
            };
        } catch (Exception e) {
            LOG.error("Action {} failed: {}", type, e.getMessage());
            return false;
        }
    }

    // ─── action implementations ──────────────────────────────

    private boolean say(String message) {
        if (message == null || message.isEmpty()) return false;
        server.getPlayerList().broadcastSystemMessage(
                Component.literal("[Neo] " + clip(message)), false);
        return true;
    }

    private boolean whisper(String playerName, String message) {
        if (playerName == null || message == null) return false;
        ServerPlayer target = server.getPlayerList().getPlayerByName(playerName);
        if (target == null) return false;
        target.sendSystemMessage(Component.literal("[Neo -> 你] " + clip(message)));
        return true;
    }

    /** 消息字段兜底：优先读 "message"，为空则读 "content"（DeepSeek 有时乱猜字段名） */
    private static String readMessage(JsonObject a) {
        String msg = getString(a, "message");
        if (msg == null || msg.isEmpty()) {
            msg = getString(a, "content");
        }
        return msg;
    }

    /** Minecraft chat cap is ~256; clip at 220 chars to leave room for prefix. */
    private static String clip(String msg) {
        if (msg.length() <= 220) return msg;
        return msg.substring(0, 217) + "...";
    }

    private boolean title(String playerName, String title, String subtitle, boolean actionBar) {
        ServerPlayer target = server.getPlayerList().getPlayerByName(playerName);
        if (target == null) return false;
        Component t = title  != null ? Component.literal(title)  : Component.empty();
        Component s = subtitle != null ? Component.literal(subtitle) : Component.empty();
        if (actionBar) {
            target.displayClientMessage(t, true);
        } else {
            target.sendSystemMessage(t);
            if (!s.getString().isEmpty()) target.sendSystemMessage(s);
        }
        return true;
    }

    private boolean runCommand(String command) {
        if (command == null || command.isEmpty()) return false;
        for (String prefix : commandAllowlist) {
            if (command.startsWith(prefix + " ") || command.equals(prefix)) {
                CommandSourceStack stack = server.createCommandSourceStack();
                server.getCommands().performPrefixedCommand(stack, command);
                LOG.info("Command executed: /{}", command);
                return true;
            }
        }
        LOG.warn("Command blocked (not in allowlist): /{}", command);
        return false;
    }

    private boolean setTime(String dimKey, String time) {
        String cmd = switch (time) {
            case "day"    -> "time set 1000";
            case "noon"   -> "time set 6000";
            case "night"  -> "time set 13000";
            case "midnight" -> "time set 18000";
            default -> null;
        };
        if (cmd == null) return false;
        return runCommand(cmd);
    }

    private boolean giveItem(String playerName, String itemId, int count) {
        if (playerName == null || itemId == null || count <= 0) return false;
        ServerPlayer target = server.getPlayerList().getPlayerByName(playerName);
        if (target == null) return false;

        ResourceLocation rl = ResourceLocation.tryParse(itemId);
        if (rl == null) return false;

        String cmd = "give " + playerName + " " + itemId + " " + count;
        return runCommand(cmd);
    }

    private boolean teleport(String playerName, String dimKey, double x, double y, double z) {
        if (playerName == null || dimKey == null) return false;
        ServerPlayer target = server.getPlayerList().getPlayerByName(playerName);
        if (target == null) return false;

        ServerLevel dest = resolveDimension(dimKey);
        if (dest == null) {
            LOG.warn("Unknown dimension: {}", dimKey);
            return false;
        }
        target.teleportTo(dest, x, y, z, target.getYRot(), target.getXRot());
        LOG.info("Teleported {} to {}@{},{},{}", playerName, dimKey, x, y, z);
        return true;
    }

    private ServerLevel resolveDimension(String key) {
        ResourceLocation rl = ResourceLocation.tryParse(key);
        if (rl == null) return null;
        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimension().location().equals(rl)) {
                return level;
            }
        }
        return null;
    }
}