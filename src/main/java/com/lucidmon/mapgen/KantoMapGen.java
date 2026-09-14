package com.lucidmon.mapgen;

import com.lucidmon.core.LucidMon;
import com.lucidmon.core.MapGenConfigManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Registers the Kanto biome source and verifies/locks generator activation. */
public final class KantoMapGen {
    public static final ResourceLocation BIOME_SOURCE_ID = ResourceLocation.fromNamespaceAndPath("lucidmon", "kanto");
    public static final String WORLD_PRESET_ID = "lucidmon:kanto_archipelago";
    public static final int PROFILE_LAYOUT_VERSION = 2;
    private static volatile boolean active;
    private static volatile String activationMessage = "Kanto world has not been loaded yet.";

    private KantoMapGen() {}

    public static void register() {
        Registry.register(BuiltInRegistries.BIOME_SOURCE, BIOME_SOURCE_ID, KantoBiomeSource.CODEC);
        ServerWorldEvents.LOAD.register(KantoMapGen::onWorldLoad);
        LucidMon.log("Registered KANTO_ARCHIPELAGO biome source/worldgen hooks (layout v" + PROFILE_LAYOUT_VERSION + ").");
    }

    private static void onWorldLoad(MinecraftServer server, ServerLevel world) {
        if (!world.dimension().equals(Level.OVERWORLD)) return;

        ChunkGenerator generator = world.getChunkSource().getGenerator();
        boolean presetActive = generator.getBiomeSource() instanceof KantoBiomeSource;
        boolean requested = MapGenConfigManager.current.mapType() == MapGenConfigManager.MapType.KANTO_ARCHIPELAGO;

        if (requested) {
            if (!MapGenConfigManager.lastReport.errors.isEmpty()) {
                throw new IllegalStateException("LucidMon KANTO_ARCHIPELAGO config has validation errors: " + MapGenConfigManager.lastReport.errors);
            }
            if (!presetActive) {
                activationMessage = "KANTO_ARCHIPELAGO requested, but the active Overworld is not using " + WORLD_PRESET_ID
                        + ". Create a new singleplayer world with the LucidMon preset or set level-type=" + WORLD_PRESET_ID + " on a dedicated server before world creation.";
                throw new IllegalStateException("LucidMon MapGen safety stop: " + activationMessage);
            }

            verifyWorldLock(server);
            if (MapGenConfigManager.current.kanto().setWorldBorder()) {
                var cfg = MapGenConfigManager.current.kanto();
                world.getWorldBorder().setCenter(cfg.centerX(), cfg.centerZ());
                world.getWorldBorder().setSize(cfg.playableDiameter());
            }
            active = true;
            activationMessage = "KANTO_ARCHIPELAGO layout v" + PROFILE_LAYOUT_VERSION + " active and profile lock verified.";
            LucidMon.log(activationMessage + " Outside playable area is "
                    + MapGenConfigManager.current.kanto().outsidePlayableAreaMode() + " / "
                    + MapGenConfigManager.current.kanto().outsidePlayableAreaBiome() + ".");
        } else if (presetActive) {
            activationMessage = "The Kanto world preset is active while mapType=STANDARD.";
            throw new IllegalStateException("LucidMon MapGen safety stop: " + activationMessage
                    + " Either set mapType=KANTO_ARCHIPELAGO or restore a normal world preset before creating/loading this world.");
        } else {
            active = false;
            activationMessage = "STANDARD active; LucidMon terrain shaping is disabled.";
        }
    }

    private static void verifyWorldLock(MinecraftServer server) {
        try {
            Path root = server.getWorldPath(LevelResource.ROOT);
            Path lock = root.resolve("lucidmon-mapgen.lock");
            String fingerprint = sha256(KantoLayout.current().fingerprintText(MapGenConfigManager.current.kanto()));
            String expected = "profile=KANTO_ARCHIPELAGO\nversion=" + PROFILE_LAYOUT_VERSION + "\nfingerprint=" + fingerprint + "\n";
            if (Files.exists(lock)) {
                String existing = Files.readString(lock, StandardCharsets.UTF_8);
                if (!existing.equals(expected)) {
                    throw new IllegalStateException("LucidMon MapGen profile lock differs from the current config/layout version. Refusing to mix generators/layouts in an existing world. "
                            + "Restore the matching build/config or create a new world. Lock: " + lock);
                }
            } else {
                Files.writeString(lock, expected, StandardCharsets.UTF_8);
                LucidMon.log("Created MapGen world profile lock: " + lock);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not read/write LucidMon MapGen world profile lock", e);
        }
    }

    private static String sha256(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public static boolean isActive() { return active; }
    public static String activationMessage() { return activationMessage; }
}
