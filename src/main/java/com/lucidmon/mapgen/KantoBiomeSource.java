package com.lucidmon.mapgen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.lucidmon.core.MapGenConfigManager;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

import java.util.*;
import java.util.stream.Stream;

/**
 * Absolute-coordinate biome source for the Kanto Archipelago world preset.
 * The serialized biome list makes the preset datapack-friendly while the
 * region assignment itself is driven by LucidMon's JSON5 profile.
 */
public final class KantoBiomeSource extends BiomeSource {
    public static final MapCodec<KantoBiomeSource> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Biome.LIST_CODEC.fieldOf("biomes").forGetter(source -> source.allowedBiomes)
    ).apply(instance, KantoBiomeSource::new));

    private final HolderSet<Biome> allowedBiomes;
    private final Map<String, Holder<Biome>> byId;

    public KantoBiomeSource(HolderSet<Biome> allowedBiomes) {
        this.allowedBiomes = allowedBiomes;
        LinkedHashMap<String, Holder<Biome>> ids = new LinkedHashMap<>();
        allowedBiomes.stream().forEach(holder -> holder.unwrapKey().ifPresent(key -> ids.put(key.location().toString(), holder)));
        this.byId = Collections.unmodifiableMap(ids);
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return allowedBiomes.stream();
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        return CODEC;
    }

    @Override
    public Holder<Biome> getNoiseBiome(int quartX, int quartY, int quartZ, Climate.Sampler sampler) {
        int x = quartX * 4;
        int y = quartY * 4;
        int z = quartZ * 4;
        MapGenConfigManager.KantoConfig cfg = MapGenConfigManager.current.kanto();
        KantoLayout layout = KantoLayout.current();

        if (!KantoTerrainShaper.insidePlayable(x, z, cfg)) {
            return biome(cfg.outsidePlayableAreaBiome());
        }

        boolean macroLand = KantoTerrainShaper.isMacroLand(x, z, cfg, layout);

        // Meaningful cave-biome volumes. These are intentionally broad enough
        // to be useful to Cobblemon rather than one-off token cells.
        if (macroLand && y < 48) {
            if (y < 4 && (insideRegion(layout.region("northSnowCrown"), x, z) || insideRegion(layout.region("northwestHighlands"), x, z))) {
                return biome("minecraft:deep_dark");
            }
            if (y < 28 && (insideRegion(layout.region("eastDrylands"), x, z) || insideArea(layout.area("rockTunnel"), x, z)
                    || insideVolcano(layout.volcano(), x, z))) {
                return biome("minecraft:dripstone_caves");
            }
            if (y < 42 && (insideRegion(layout.region("centralTemperate"), x, z)
                    || insideRegion(layout.region("southeastJungleChain"), x, z)
                    || insideRegion(layout.region("southwestWetlands"), x, z))) {
                return biome("minecraft:lush_caves");
            }
        }

        if (macroLand) {
            KantoLayout.Region region = layout.nearestRegion(x, z);
            if (region != null && !region.biomes().isEmpty()) {
                return biome(selectRegionalBiome(region, x, z));
            }
            return biome("minecraft:plains");
        }

        return biome(selectOceanBiome(x, z, cfg));
    }

    private String selectRegionalBiome(KantoLayout.Region region, int x, int z) {
        List<String> candidates = region.biomes().stream()
                .filter(id -> !id.equals("minecraft:deep_dark") && !id.equals("minecraft:lush_caves") && !id.equals("minecraft:dripstone_caves"))
                .toList();
        if (candidates.isEmpty()) return "minecraft:plains";

        double angle = Math.atan2(z - region.centerZ(), x - region.centerX());
        double normalized = (angle + Math.PI) / (Math.PI * 2.0);
        int sector = Math.min(candidates.size() - 1, (int)Math.floor(normalized * candidates.size()));
        int ring = (int)Math.floor(Math.hypot(x - region.centerX(), z - region.centerZ()) / Math.max(64.0, region.radius() / 4.0));
        return candidates.get(Math.floorMod(sector + ring, candidates.size()));
    }

    private String selectOceanBiome(int x, int z, MapGenConfigManager.KantoConfig cfg) {
        int dx = x - cfg.centerX();
        int dz = z - cfg.centerZ();
        double score = KantoTerrainShaper.macroLandScore(x, z, cfg, KantoLayout.current());
        boolean deep = score < -0.28;

        if (dz < -1700) return deep ? "minecraft:deep_frozen_ocean" : "minecraft:frozen_ocean";
        if (dx < -900 && dz < -700) return deep ? "minecraft:deep_cold_ocean" : "minecraft:cold_ocean";
        if (dx > 900 && dz > 800) return deep ? "minecraft:deep_lukewarm_ocean" : "minecraft:warm_ocean";
        if (dz > 800) return deep ? "minecraft:deep_lukewarm_ocean" : "minecraft:lukewarm_ocean";
        return deep ? "minecraft:deep_ocean" : "minecraft:ocean";
    }

    private Holder<Biome> biome(String id) {
        Holder<Biome> exact = byId.get(id);
        if (exact != null) return exact;
        Holder<Biome> plains = byId.get("minecraft:plains");
        if (plains != null) return plains;
        if (allowedBiomes.size() == 0) throw new IllegalStateException("LucidMon Kanto biome source has no allowed biomes");
        return allowedBiomes.get(0);
    }

    private static boolean insideRegion(KantoLayout.Region r, int x, int z) {
        if (r == null) return false;
        double dx = (x - r.centerX()) / (double)r.radius();
        double dz = (z - r.centerZ()) / (double)r.radius();
        return dx * dx + dz * dz <= 1.0;
    }

    private static boolean insideArea(KantoLayout.Area a, int x, int z) {
        if (a == null || !a.enabled() || a.radiusX() <= 0 || a.radiusZ() <= 0) return false;
        double dx = (x - a.x()) / (double)a.radiusX();
        double dz = (z - a.z()) / (double)a.radiusZ();
        return dx * dx + dz * dz <= 1.0;
    }

    private static boolean insideVolcano(KantoLayout.Volcano v, int x, int z) {
        double dx = (x - v.x()) / (double)v.radiusX();
        double dz = (z - v.z()) / (double)v.radiusZ();
        return dx * dx + dz * dz <= 1.0;
    }
}
