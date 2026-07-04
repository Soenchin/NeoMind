package cc.neonisch.neomind.command;

import com.google.gson.JsonObject;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * /mind claim|unclaim|list — region management commands.
 * Only registered when NeoSightAPI is available.
 */
public final class RegionCommand {

    private RegionCommand() {}

    /**
     * Build the /mind command tree for region subcommands.
     * Caller should attach this to the root "mind" literal.
     */
    public static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> build() {
        return literal("nmland")
                .then(literal("claim")
                        .then(argument("name", StringArgumentType.greedyString())
                                .executes(RegionCommand::claim)))
                .then(literal("unclaim")
                        .then(argument("name", StringArgumentType.greedyString())
                                .executes(RegionCommand::unclaim)))
                .then(literal("list")
                        .executes(RegionCommand::listAll)
                        .then(literal("near")
                                .executes(RegionCommand::listNear)));
    }

    // ─── /mind claim <name> [radius] [description] ─────────

    private static int claim(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer player = src.getPlayer();
        if (player == null) {
            src.sendFailure(Component.literal("只有玩家才能使用此命令"));
            return 0;
        }

        String raw = StringArgumentType.getString(ctx, "name");
        // Parse: name [radius] [description]
        String[] parts = raw.split("\\s+", 3);
        String name = parts[0];
        int radius = 15;
        String label = null;
        if (parts.length >= 2) {
            try { radius = Integer.parseInt(parts[1]); } catch (NumberFormatException e) { /* keep 15 */ }
        }
        if (parts.length >= 3) {
            label = parts[2];
        }

        int cx = (int) player.getX();
        int cz = (int) player.getZ();
        String dim = player.level().dimension().location().toString();
        String owner = player.getGameProfile().getName();

        int x1 = cx - radius;
        int z1 = cz - radius;
        int x2 = cx + radius;
        int z2 = cz + radius;

        JsonObject result = SightApiClient.createRegion(name, owner, dim, x1, z1, x2, z2, label);
        if (result == null) {
            src.sendFailure(Component.literal("[NM] 注册失败，NeoSightAPI 可能未运行。"));
            return 0;
        }

        int size = (radius * 2 + 1);
        src.sendSuccess(() -> Component.literal(
                "[NM] 已注册区域 \"" + name + "\" (" + size + "x" + size + ", 以你为中心)")
                .withStyle(ChatFormatting.GREEN), false);
        return Command.SINGLE_SUCCESS;
    }

    // ─── /mind unclaim <name> ──────────────────────────────

    private static int unclaim(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer player = src.getPlayer();
        if (player == null) {
            src.sendFailure(Component.literal("只有玩家才能使用此命令"));
            return 0;
        }

        String name = StringArgumentType.getString(ctx, "name");
        String owner = player.getGameProfile().getName();

        // Find region by name + owner
        List<JsonObject> all = SightApiClient.listRegions();
        for (JsonObject r : all) {
            if (name.equals(r.get("name").getAsString())
                    && owner.equals(r.get("owner").getAsString())) {
                String id = r.get("id").getAsString();
                if (SightApiClient.deleteRegion(id, owner)) {
                    src.sendSuccess(() -> Component.literal(
                            "[NM] 已删除区域 \"" + name + "\"").withStyle(ChatFormatting.YELLOW), false);
                    return Command.SINGLE_SUCCESS;
                }
            }
        }

        src.sendFailure(Component.literal("[NM] 未找到属于你的区域 \"" + name + "\""));
        return 0;
    }

    // ─── /mind list ────────────────────────────────────────

    private static int listAll(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        List<JsonObject> regions = SightApiClient.listRegions();
        if (regions.isEmpty()) {
            src.sendSuccess(() -> Component.literal("[NM] 暂无已注册区域"), false);
            return Command.SINGLE_SUCCESS;
        }

        src.sendSuccess(() -> Component.literal("[NM] 已注册区域 (" + regions.size() + "):"), false);
        for (JsonObject r : regions) {
            String n = r.get("name").getAsString();
            String o = r.get("owner").getAsString();
            String d = r.get("dimension").getAsString();
            src.sendSuccess(() -> Component.literal("  - " + n + " (" + o + ", " + d + ")"), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    // ─── /mind list near ───────────────────────────────────

    private static int listNear(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer player = src.getPlayer();
        if (player == null) {
            src.sendFailure(Component.literal("只有玩家才能使用此命令"));
            return 0;
        }

        int x = (int) player.getX();
        int z = (int) player.getZ();
        String dim = player.level().dimension().location().toString();

        List<JsonObject> regions = SightApiClient.findRegionsAt(x, z, dim);
        if (regions.isEmpty()) {
            src.sendSuccess(() -> Component.literal("[NM] 附近没有已注册区域"), false);
            return Command.SINGLE_SUCCESS;
        }

        src.sendSuccess(() -> Component.literal("[NM] 附近区域:"), false);
        for (JsonObject r : regions) {
            String n = r.get("name").getAsString();
            String o = r.get("owner").getAsString();
            String lbl = r.has("label") && !r.get("label").getAsString().isEmpty()
                    ? " — " + r.get("label").getAsString() : "";
            src.sendSuccess(() -> Component.literal("  - " + n + " (属于 " + o + ")" + lbl), false);
        }
        return Command.SINGLE_SUCCESS;
    }
}
