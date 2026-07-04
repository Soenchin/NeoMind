package cc.neonisch.neomind;

import cc.neonisch.neomind.action.ActionExecutor;
import cc.neonisch.neomind.chat.ChatEventHandler;
import cc.neonisch.neomind.config.NeoMindConfig;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(NeoMind.MOD_ID)
public class NeoMind {

    public static final String  MOD_ID = "neomind";
    public static final Logger  LOGGER = LoggerFactory.getLogger(MOD_ID);

    public NeoMind(IEventBus modEventBus, ModContainer container) {
        LOGGER.info("NeoMind waking up — AI butler online.");

        // Register config via ModContainer
        // SERVER type + explicit filename → config/neomind.toml
        container.registerConfig(ModConfig.Type.SERVER, NeoMindConfig.SPEC, "neomind.toml");

        // ServerStartedEvent is a NeoForge game event, NOT an IModBusEvent.
        // It MUST be registered on NeoForge.EVENT_BUS, not the mod bus.
        NeoForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        var server = event.getServer();

        var executor = new ActionExecutor(server,
                NeoMindConfig.ALLOWED_ACTIONS.get(),
                NeoMindConfig.COMMAND_ALLOWLIST.get());

        var chatHandler = new ChatEventHandler(executor, server);
        NeoForge.EVENT_BUS.register(chatHandler);

        LOGGER.info("NeoMind chat handler ready. Trigger: '{}'", NeoMindConfig.CHAT_TRIGGER_PREFIX.get());
    }
}