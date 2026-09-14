package com.lucidmon.mapgen;

import com.lucidmon.core.LucidMon;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Places the two city anchors that must be deterministic for Kanto layout v2.
 *
 * BCA's small default village is the starter settlement on the southwest tip;
 * BCA's large default village (the variant with the department-store content)
 * is the central Celadon-sized city. Placement is done once per world after the
 * Kanto profile activates. A world marker prevents duplication on later loads.
 */
public final class KantoCityPlacer {
    public static final ResourceLocation STARTER_CITY = ResourceLocation.fromNamespaceAndPath("bca", "village/default_small");
    public static final ResourceLocation CENTRAL_CITY = ResourceLocation.fromNamespaceAndPath("bca", "village/default_large");
    private static final String MARKER_NAME = "lucidmon-kanto-cities-v2.lock";

    private KantoCityPlacer() {}

    public static void schedule(MinecraftServer server, ServerLevel world) {
        // ServerWorldEvents.LOAD fires while the integrated/dedicated server is
        // still assembling its worlds. Queue the actual placement so vanilla's
        // command/registry state is fully available and chunk generation may run.
        server.execute(() -> placeOnce(server, world));
    }

    private static void placeOnce(MinecraftServer server, ServerLevel world) {
        try {
            Path marker = server.getWorldPath(LevelResource.ROOT).resolve(MARKER_NAME);
            if (Files.exists(marker)) return;

            Registry<Structure> structures = world.registryAccess().registryOrThrow(Registries.STRUCTURE);
            if (!structures.containsKey(STARTER_CITY) || !structures.containsKey(CENTRAL_CITY)) {
                LucidMon.warn("Kanto city placement deferred: required BCA structures are unavailable (expected "
                        + STARTER_CITY + " and " + CENTRAL_CITY + "). No city marker was written, so a later load can retry.");
                return;
            }

            KantoLayout layout = KantoLayout.current();
            KantoLayout.Zone starter = layout.startingCity();
            KantoLayout.Zone central = layout.centralCity();

            int starterY = world.getHeight(Heightmap.Types.WORLD_SURFACE, starter.x(), starter.z());
            int centralY = world.getHeight(Heightmap.Types.WORLD_SURFACE, central.x(), central.z());

            var source = server.createCommandSourceStack()
                    .withPermission(4)
                    .withLevel(world)
                    .withSuppressedOutput();

            server.getCommands().performPrefixedCommand(source,
                    "place structure " + STARTER_CITY + " " + starter.x() + " " + starterY + " " + starter.z());
            server.getCommands().performPrefixedCommand(source,
                    "place structure " + CENTRAL_CITY + " " + central.x() + " " + centralY + " " + central.z());

            String text = "layout=2\n"
                    + "starter=" + STARTER_CITY + "@" + starter.x() + "," + starterY + "," + starter.z() + "\n"
                    + "central=" + CENTRAL_CITY + "@" + central.x() + "," + centralY + "," + central.z() + "\n";
            Files.writeString(marker, text, StandardCharsets.UTF_8);
            LucidMon.log("Placed Kanto layout-v2 city anchors: starter " + STARTER_CITY + " at "
                    + starter.x() + "," + starterY + "," + starter.z() + "; central/Celadon " + CENTRAL_CITY + " at "
                    + central.x() + "," + centralY + "," + central.z() + ".");
        } catch (Throwable t) {
            // City placement must never make an otherwise valid world unloadable.
            // Without the marker the next load can retry after the underlying issue is fixed.
            LucidMon.error("Could not place Kanto layout-v2 city anchors", t);
        }
    }
}
