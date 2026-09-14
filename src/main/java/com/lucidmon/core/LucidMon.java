package com.lucidmon.core;

import net.fabricmc.api.ModInitializer;

public final class LucidMon implements ModInitializer {
    public static final String VERSION = "0.1.1-unstable.2";
    public static final String DISPLAY_VERSION = "v0.1.1 [UNSTABLE]";

    @Override
    public void onInitialize() {
        log("Starting LucidMon " + DISPLAY_VERSION + " for Minecraft 1.21.1 / Fabric.");
        ConfigManager.load();
        MapGenConfigManager.load();
        CommandBridge.register();
        log("Modules bundled in this test build: Core config, MapGen pre-flight/profile config, League gym placement scaffold, Map Reveal v3, Soulpack v9, Virtual Pasture Cobblemon 1.8 compatibility build.");
        log("MapGen note: STANDARD is safe/active. KANTO_ARCHIPELAGO configuration is bundled and validated, but the absolute-coordinate Kanto terrain generator is NOT active in unstable.2; do not create the final Kanto world with this build.");
        log("Champion note: Player Champion matchmaking/battle UI is still not enabled in unstable.2. AI/PLAYER config remains parsed and validated for forward compatibility.");
    }

    public static void log(String s) { System.out.println("[LucidMon] " + s); }
    public static void warn(String s) { System.out.println("[LucidMon/WARN] " + s); }
    public static void error(String s, Throwable t) {
        System.err.println("[LucidMon/ERROR] " + s);
        if (t != null) t.printStackTrace(System.err);
    }
}
