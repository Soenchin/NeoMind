package cc.neonisch.neomind.action;

import com.google.gson.JsonObject;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

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
                case "noop"            -> true;
                case "say"             -> say(readMessage(a));
                case "whisper"         -> whisper(player, readMessage(a));
                case "title"           -> title(getString(a, "player"), getString(a, "title"),
                                                getString(a, "subtitle"), getBool(a, "action_bar", false));
                case "run_command"     -> runCommand(getString(a, "command"));
                case "set_time"        -> setTime(getString(a, "dimension"), getString(a, "time"));
                case "give_item"       -> giveItem(getString(a, "player"), getString(a, "item"), getInt(a, "count", 1));
                case "teleport"        -> teleport(getString(a, "player"), getString(a, "dimension"),
                                                  getDouble(a, "x", 0), getDouble(a, "y", 64), getDouble(a, "z", 0));
                case "scan_entities"   -> scanEntities(player, a);
                case "scan_blocks"     -> scanBlocks(player, a);
                case "detect_structure" -> detectStructure(player, a);
                case "look_at"         -> lookAt(player);
                // MVP skips these — noop with log
                case "mark_waypoint"   -> { LOG.info("waypoint {} for {} skipped (not in MVP)", getString(a, "label"), getString(a, "player")); yield true; }
                case "chunk_ticket"    -> { LOG.info("chunk_ticket skipped (not in MVP)"); yield true; }
                case "set_weather"     -> { LOG.info("set_weather skipped (not in MVP)"); yield true; }
                case "spawn_entity"    -> { LOG.info("spawn_entity skipped (not in MVP)"); yield true; }
                case "schedule"        -> { LOG.info("schedule skipped (not in MVP)"); yield true; }
                default                -> { LOG.warn("Unknown action type: {}", type); yield false; }
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
        if (msg == null) return "";
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

    // ─── scan implementations ─────────────────────────────────

    /**
     * 方位字符串映射，用于 8 方向方位描述。
     * 基于实体相对于玩家的位置，算出大致方位。
     */
    private static String directionOf(ServerPlayer player, double targetX, double targetZ) {
        double dx = targetX - player.getX();
        double dz = targetZ - player.getZ();
        if (Math.abs(dx) < 8 && Math.abs(dz) < 8) return "附近";
        double angle = Math.toDegrees(Math.atan2(-dz, dx)); // MC: -Z = north
        if (angle < 0) angle += 360;
        if (angle < 22.5 || angle >= 337.5) return "东边";
        if (angle < 67.5)  return "东南";
        if (angle < 112.5) return "南边";
        if (angle < 157.5) return "西南";
        if (angle < 202.5) return "西边";
        if (angle < 247.5) return "西北";
        if (angle < 292.5) return "北边";
        return "东北";
    }

    /**
     * scan_entities — 扫描玩家周围 3x3 区块内的实体。
     * LLM 字段: entity (实体ID如 minecraft:pig), direction (可选: front/back/left/right/nearby)
     */
    private boolean scanEntities(String playerName, JsonObject a) {
        ServerPlayer player = server.getPlayerList().getPlayerByName(playerName);
        if (player == null) return false;

        String entityTypeId = getString(a, "entity");
        if (entityTypeId == null || entityTypeId.isEmpty()) {
            whisper(playerName, "没有指定要扫描的实体类型。");
            return false;
        }

        ResourceLocation rl = ResourceLocation.tryParse(entityTypeId);
        if (rl == null) {
            whisper(playerName, "实体ID格式不对：" + entityTypeId);
            return false;
        }

        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(rl);
        if (type == null) {
            whisper(playerName, "找不到实体类型：" + entityTypeId);
            return false;
        }

        ServerLevel level = player.serverLevel();

        // 3x3 chunks AABB: player chunk ± 1 chunk = 48 blocks each direction
        double cx = player.getX();
        double cz = player.getZ();
        AABB box = new AABB(cx - 24, player.getY() - 32, cz - 24,
                            cx + 24, player.getY() + 32, cz + 24);

        List<? extends Entity> entities = level.getEntitiesOfClass(Entity.class, box).stream()
                .filter(e -> e.getType() == type)
                .toList();

        if (entities.isEmpty()) {
            whisper(playerName, "附近没有发现 " + entityTypeId.replace("minecraft:", "") + "。");
        } else {
            // Group by direction
            Map<String, Integer> dirCount = new LinkedHashMap<>();
            for (Entity e : entities) {
                String dir = directionOf(player, e.getX(), e.getZ());
                dirCount.merge(dir, 1, Integer::sum);
            }
            StringBuilder sb = new StringBuilder();
            sb.append("发现 ").append(entities.size()).append(" 个 ")
              .append(entityTypeId.replace("minecraft:", "")).append("：");
            for (Map.Entry<String, Integer> entry : dirCount.entrySet()) {
                sb.append(entry.getValue()).append("只").append(entry.getKey()).append("，");
            }
            // Remove trailing comma
            String result = sb.toString();
            if (result.endsWith("，")) result = result.substring(0, result.length() - 1) + "。";
            whisper(playerName, result);
        }

        LOG.info("scan_entities: {} → {} found", entityTypeId, entities.size());
        return true;
    }

    /**
     * scan_blocks — 扫描玩家周围 3x3 区块内的地表方块。
     * LLM 字段: block (方块ID如 minecraft:sand), direction (可选)
     * 只扫玩家 Y±16 范围，避免遍历整个 384 格高度。
     */
    private boolean scanBlocks(String playerName, JsonObject a) {
        ServerPlayer player = server.getPlayerList().getPlayerByName(playerName);
        if (player == null) return false;

        String blockId = getString(a, "block");
        if (blockId == null || blockId.isEmpty()) {
            whisper(playerName, "没有指定要扫描的方块类型。");
            return false;
        }

        ResourceLocation rl = ResourceLocation.tryParse(blockId);
        if (rl == null) {
            whisper(playerName, "方块ID格式不对：" + blockId);
            return false;
        }

        var block = BuiltInRegistries.BLOCK.get(rl);
        if (block == null) {
            whisper(playerName, "找不到方块类型：" + blockId);
            return false;
        }

        ServerLevel level = player.serverLevel();
        int playerChunkX = player.chunkPosition().x;
        int playerChunkZ = player.chunkPosition().z;

        // Scan 3x3 chunks, Y±16 from player
        int minY = Math.max(level.getMinBuildHeight(), player.blockPosition().getY() - 16);
        int maxY = Math.min(level.getMaxBuildHeight(), player.blockPosition().getY() + 16);
        int count = 0;
        String nearestDir = "附近";
        double nearestDist = Double.MAX_VALUE;

        for (int cx = playerChunkX - 1; cx <= playerChunkX + 1; cx++) {
            for (int cz = playerChunkZ - 1; cz <= playerChunkZ + 1; cz++) {
                if (!level.hasChunk(cx, cz)) continue;
                LevelChunk chunk = level.getChunk(cx, cz);
                int startX = cx << 4;
                int startZ = cz << 4;
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        for (int y = minY; y <= maxY; y++) {
                            BlockState state = chunk.getBlockState(new BlockPos(startX + x, y, startZ + z));
                            if (state.is(block)) {
                                count++;
                                double dist = Math.sqrt(
                                    Math.pow(startX + x - player.getX(), 2) +
                                    Math.pow(y - player.getY(), 2) +
                                    Math.pow(startZ + z - player.getZ(), 2));
                                if (dist < nearestDist) {
                                    nearestDist = dist;
                                    nearestDir = directionOf(player, startX + x, startZ + z);
                                }
                            }
                        }
                    }
                }
            }
        }

        String displayName = blockId.replace("minecraft:", "");
        if (count == 0) {
            whisper(playerName, "附近没有发现 " + displayName + "。");
        } else {
            whisper(playerName, "发现 " + count + " 个 " + displayName + "，最近的在" + nearestDir + "约" + (int) nearestDist + "格处。");
        }

        LOG.info("scan_blocks: {} → {} found", blockId, count);
        return true;
    }

    /**
     * detect_structure — 检测玩家周围 3x3 区块内的非玩家结构。
     * 通过遍历已加载区块的 getAllStarts() 查找结构标记点，覆盖原版 + Mod 注册的结构。
     * LLM 字段: direction (可选，3x3 模式)
     */
    private boolean detectStructure(String playerName, JsonObject a) {
        ServerPlayer player = server.getPlayerList().getPlayerByName(playerName);
        if (player == null) return false;

        ServerLevel level = player.serverLevel();
        int playerChunkX = player.chunkPosition().x;
        int playerChunkZ = player.chunkPosition().z;

        // Track structures found: name → {x, z, distance}
        Map<String, double[]> found = new LinkedHashMap<>();

        for (int cx = playerChunkX - 1; cx <= playerChunkX + 1; cx++) {
            for (int cz = playerChunkZ - 1; cz <= playerChunkZ + 1; cz++) {
                if (!level.hasChunk(cx, cz)) continue;
                LevelChunk chunk = level.getChunk(cx, cz);
                Map<Structure, StructureStart> starts;
                try {
                    starts = chunk.getAllStarts();
                } catch (Exception e) {
                    LOG.debug("getAllStarts failed for chunk ({}, {}): {}", cx, cz, e.getMessage());
                    continue;
                }
                if (starts == null || starts.isEmpty()) continue;

                for (Map.Entry<Structure, StructureStart> entry : starts.entrySet()) {
                    Structure struct = entry.getKey();
                    var key = level.registryAccess()
                            .registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE)
                            .getKey(struct);
                    if (key == null || found.containsKey(key.toString())) continue;

                    var bbox = entry.getValue().getBoundingBox();
                    double sx = (bbox.minX() + bbox.maxX()) / 2.0;
                    double sz = (bbox.minZ() + bbox.maxZ()) / 2.0;
                    double dist = Math.sqrt(
                            Math.pow(sx - player.getX(), 2) +
                            Math.pow(sz - player.getZ(), 2));
                    found.put(key.toString(), new double[]{sx, sz, dist});
                }
            }
        }

        if (found.isEmpty()) {
            whisper(playerName, "附近没有发现任何结构。");
        } else {
            // Sort by distance, report up to 3 closest
            List<Map.Entry<String, double[]>> sorted = new ArrayList<>(found.entrySet());
            sorted.sort(Comparator.comparingDouble(e -> e.getValue()[2]));

            StringBuilder sb = new StringBuilder("发现以下结构：");
            int count = 0;
            for (var entry : sorted) {
                if (count >= 3) break;
                String name = entry.getKey().replace("minecraft:", "");
                String dir = directionOf(player, entry.getValue()[0], entry.getValue()[1]);
                sb.append(name).append("(").append(dir).append("约").append((int) entry.getValue()[2]).append("格)，");
                count++;
            }
            if (sorted.size() > 3) sb.append("等共").append(sorted.size()).append("处，");
            String result = sb.toString();
            if (result.endsWith("，")) result = result.substring(0, result.length() - 1) + "。";
            whisper(playerName, result);
        }

        LOG.info("detect_structure: {} → {} found", playerName, found.size());
        return true;
    }

    /**
     * look_at — 3D 射线检测面前 20 格。三层返回：
     *   1. 命中实体（猪、村民等）→ 报告实体名
     *   2. 命中结构方块（神殿/村庄墙壁等）→ 报告结构名 + 方块
     *   3. 普通方块 → 报告方块名
     */
    private boolean lookAt(String playerName) {
        ServerPlayer player = server.getPlayerList().getPlayerByName(playerName);
        if (player == null) return false;

        ServerLevel level = player.serverLevel();
        Vec3 eye = player.getEyePosition();
        Vec3 dir = player.getViewVector(1.0F);
        Vec3 target = eye.add(dir.scale(20));

        ClipContext ctx = new ClipContext(eye, target,
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player);
        BlockHitResult hit = level.clip(ctx);

        if (hit.getType() == BlockHitResult.Type.MISS) {
            whisper(playerName, "面前 20 格内没有东西。");
            return true;
        }

        BlockPos pos = hit.getBlockPos();
        double dist = Math.sqrt(pos.distToLowCornerSqr(eye.x, eye.y, eye.z));

        // Layer 1: check if there's a non-player entity near the hit point
        var entities = level.getEntitiesOfClass(Entity.class,
                new AABB(pos).inflate(1.5));
        for (Entity e : entities) {
            if (e instanceof ServerPlayer) continue;
            String entityName = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath();
            whisper(playerName, "你面前 " + (int) dist + " 格处有一只 " + entityName + "。");
            return true;
        }

        // Layer 2: check if the hit block belongs to a structure
        if (level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) {
            Map<Structure, StructureStart> starts = level.getChunk(pos).getAllStarts();
            for (var entry : starts.entrySet()) {
                if (entry.getValue().getBoundingBox().isInside(pos)) {
                    String blockName = BuiltInRegistries.BLOCK.getKey(
                            level.getBlockState(pos).getBlock()).getPath();
                    String structName = level.registryAccess()
                            .registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE)
                            .getKey(entry.getKey()).getPath();
                    whisper(playerName, "你面前是 " + structName + " 的 " + blockName + "（约" + (int) dist + "格）。");
                    return true;
                }
            }
        }

        // Layer 3: just report the block
        String name = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).getPath();
        whisper(playerName, "你面前 " + (int) dist + " 格处是 " + name + "。");
        return true;
    }
}
