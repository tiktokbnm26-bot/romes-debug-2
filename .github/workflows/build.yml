package net.romes.romesdebug;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.romes.romesdebug.gui.ToastManager;
import net.romes.romesdebug.modules.SeedSusChunks;

import java.lang.invoke.MethodHandles;

public class RomesAddon extends MeteorAddon {

    public static final Category CATEGORY = new Category("Rome's Debug");

    @Override
    public void onInitialize() {
        // Required for @EventHandler to work in an addon package
        MeteorClient.EVENT_BUS.registerLambdaFactory("net.romes.romesdebug",
            (lookupInMethod, klass) -> (MethodHandles.Lookup) lookupInMethod.invoke(null, klass, MethodHandles.lookup()));

        ToastManager.init();
        Modules.get().add(new SeedSusChunks());
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
    }

    @Override
    public String getPackage() {
        return "net.romes.romesdebug";
    }
}
