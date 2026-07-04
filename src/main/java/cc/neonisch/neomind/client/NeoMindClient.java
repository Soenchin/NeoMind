package cc.neonisch.neomind.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/**
 * Client-side only. Registers the in-game config screen.
 * This class is automatically skipped on dedicated servers (dist = CLIENT).
 */
@Mod(value = "neomind", dist = Dist.CLIENT)
public class NeoMindClient {

    public NeoMindClient(ModContainer container) {
        container.registerExtensionPoint(
            IConfigScreenFactory.class,
            ConfigurationScreen::new
        );
    }
}