package com.lucidmon.core;

import com.lucidmon.mapgen.KantoMapGen;
import net.fabricmc.api.ModInitializer;

public final class LucidMon implements ModInitializer {
    public static final String VERSION = "0.1.2-unstable.3";
    public static final String DISPLAY_VERSION = "v0.1.2 [UNSTABLE]";

    @Override
    public void onInitialize() {
        log("Starting LucidMon " + DISPLAY_VERSION + " for Minecraft 1.21.1 / Fabric.");
        ConfigManager.load();
        MapGenConfigManager.load();
        KantoMapGen.register();
        CommandBridge.register();
        log("Modules bundled in this test build: Core config, KANTO_ARCHIPELAGO terrain/biome generator, League gym placement scaffold, Map Reveal v3, Soulpack v9, Virtual Pasture Cobblemon 1.8 compatibility build.");
        log("MapGen note: STANDARD leaves terrain generation unchanged. KANTO_ARCHIPELAGO requires both mapType=KANTO_ARCHIPELAGO and level-type=lucidmon:kanto_archipelago before a new world is created.");
        log("Champion note: Player Champion matchmaking/battle UI is still not enabled in unstable.3. AI/PLAYER config remains parsed and validated for forward compatibility.");
    }

    public static void log(String s) { System.out.println("[LucidMon] " + s); }
    public static void warn(String s) { System.out.println("[LucidMon/WARN] " + s); }
    public static void error(String s, Throwable t) {
        System.err.println("[LucidMon/ERROR] " + s);
        if (t != null) t.printStackTrace(System.err);
    }
}
