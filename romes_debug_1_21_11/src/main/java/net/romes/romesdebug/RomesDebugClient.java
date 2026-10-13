package net.romes.romesdebug;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.romes.romesdebug.command.SeedChunkCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RomesDebugClient implements ClientModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger("romesdebug");

    @Override
    public void onInitializeClient() {
        LOGGER.info("[Rome's Debug] Loaded.");
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            SeedChunkCommand.register(dispatcher);
        });
    }
}
