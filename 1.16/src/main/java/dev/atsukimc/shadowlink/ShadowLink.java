package dev.atsukimc.shadowlink;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Common entrypoint. All real work happens on the logical server through mixins
 * (see {@link dev.atsukimc.shadowlink.core.ShadowManager}), so the same JAR works on a
 * dedicated server and inside a singleplayer integrated server.
 */
public final class ShadowLink implements ModInitializer {
    public static final String MOD_ID = "shadowlink";
    public static final Logger LOGGER = LogManager.getLogger("ShadowLink");

    @Override
    public void onInitialize() {
        String version = FabricLoader.getInstance().getModContainer(MOD_ID)
                .map(mod -> mod.getMetadata().getVersion().getFriendlyString())
                .orElse("?");
        LOGGER.info("ShadowLink {} loaded: item shadow links are tracked and restored automatically.", version);
    }
}
