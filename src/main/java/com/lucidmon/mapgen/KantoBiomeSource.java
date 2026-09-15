package com.lucidmon.mapgen;

import com.lucidmon.core.MapGenConfigManager;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

import java.util.*;
import java.util.stream.Stream;

/**
 * Absolute-coordinate biome source for the Kanto Archipelago world preset.
 *
 * Layout v3 deliberately avoids nearest-cell/Voronoi selection. Broad authored
 * ecological families are warped continuously, then low-frequency continuous
 * noise chooses related variants inside each family. The result is fewer,
 * larger biome provinces with curved boundaries instead of a patchwork grid.
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

        if (!KantoTerrainShaper.insidePlayable(x, z, cfg)) return biome(cfg.outsidePlayableAreaBiome());

        boolean macroLand = KantoTerrainShaper.isMacroLand(x, z, cfg, layout);
        KantoShape.SurfaceFamily family = KantoShape.surfaceFamily(x, z, cfg);

        // Broad underground habitat volumes remain landmark/ecology-driven.
        if (macroLand && y < 48) {
            if (y < 4 && (family == KantoShape.SurfaceFamily.SNOW || family == KantoShape.SurfaceFamily.HIGHLAND)) {
                return biome("minecraft:deep_dark");
            }
            if (y < 28 && (family == KantoShape.SurfaceFamily.DRYLAND
                    || insideArea(layout.area("rockTunnel"), x, z)
                    || KantoShape.isVolcanoIsland(x, z, layout))) {
                return biome("minecraft:dripstone_caves");
            }
            if (y < 42 && (family == KantoShape.SurfaceFamily.JUNGLE
                    || family == KantoShape.SurfaceFamily.WETLAND
                    || family == KantoShape.SurfaceFamily.TEMPERATE)) {
                return biome("minecraft:lush_caves");
            }
        }

        if (macroLand) return surfaceBiome(x, y, z, cfg, layout, family);
        return biome(selectOceanBiome(x, z, cfg));
    }

    private Holder<Biome> surfaceBiome(int x, int y, int z, MapGenConfigManager.KantoConfig cfg,
                                       KantoLayout layout, KantoShape.SurfaceFamily family) {
        double coast = KantoTerrainShaper.macroLandScore(x, z, cfg, layout);
        KantoHydrology.Sample hydro = KantoHydrology.sample(x, z, cfg);

        // Rivers and lakes use actual river biomes; northern water freezes.
        if (hydro.hasWater() && hydro.channel() > 0.08) {
            return biome(hydro.frozen() ? "minecraft:frozen_river" : "minecraft:river");
        }

        // Mt. Moon is explicitly snow-capped even though its foothills can lie
        // inside another broad ecological province.
        KantoLayout.Area moon = layout.area("mtMoon");
        if (moon != null && moon.enabled() && insideArea(moon, x, z) && y >= 106) {
            if (y >= 156) return biome("minecraft:frozen_peaks");
            if (y >= 134) return biome("minecraft:jagged_peaks");
            return biome("minecraft:snowy_slopes");
        }

        // Preserve a recognizable volcanic ecology without forcing a perfect
        // circular biome footprint; the island mask itself is warped in KantoShape.
        if (KantoShape.isVolcanoIsland(x, z, layout)) {
            if (y >= 128) return biome("minecraft:stony_peaks");
            if (y >= 88) return biome(variant(x, z, 0x701CA91L, 520.0) > 0.15
                    ? "minecraft:wooded_badlands" : "minecraft:badlands");
            if (coast < 0.42) return biome("minecraft:stony_shore");
            return biome("minecraft:plains");
        }

        // Coast is a geographic band rather than an ecosystem-cell boundary.
        if (coast < 0.40) {
            if (family == KantoShape.SurfaceFamily.SNOW) return biome("minecraft:snowy_beach");
            if (family == KantoShape.SurfaceFamily.WETLAND && KantoShape.wetlandWeight(x, z, cfg) > 0.48) {
                return biome("minecraft:mangrove_swamp");
            }
            double rocky = variant(x, z, 0xC0457L, 520.0);
            if (family == KantoShape.SurfaceFamily.HIGHLAND || rocky > 0.58) return biome("minecraft:stony_shore");
            return biome("minecraft:beach");
        }

        return switch (family) {
            case SNOW -> biome(selectSnow(x, y, z));
            case HIGHLAND -> biome(selectHighland(x, y, z));
            case DRYLAND -> biome(selectDryland(x, z));
            case JUNGLE -> biome(selectJungle(x, z, cfg));
            case WETLAND -> biome(selectWetland(x, z, cfg, coast));
            case TEMPERATE -> biome(selectTemperate(x, z));
        };
    }

    private static String selectTemperate(int x, int z) {
        double n = variant(x, z, 0x100101L, 760.0);
        double rare = variant(x, z, 0x100102L, 1280.0);
        if (rare > 0.70 && n > 0.05) return "minecraft:cherry_grove";
        if (n > 0.66) return "minecraft:dark_forest";
        if (n > 0.34) return "minecraft:forest";
        if (n > 0.12) return "minecraft:birch_forest";
        if (n > -0.18) return "minecraft:plains";
        if (n > -0.40) return "minecraft:meadow";
        if (n > -0.62) return "minecraft:flower_forest";
        return rare < -0.35 ? "minecraft:sunflower_plains" : "minecraft:old_growth_birch_forest";
    }

    private static String selectJungle(int x, int z, MapGenConfigManager.KantoConfig cfg) {
        double weight = KantoShape.jungleWeight(x, z, cfg);
        double n = variant(x, z, 0x200201L, 610.0);
        if (weight < 0.50) return "minecraft:sparse_jungle";
        if (n > 0.58) return "minecraft:bamboo_jungle";
        return "minecraft:jungle";
    }

    private static String selectWetland(int x, int z, MapGenConfigManager.KantoConfig cfg, double coast) {
        double n = variant(x, z, 0x300301L, 720.0);
        double weight = KantoShape.wetlandWeight(x, z, cfg);
        if (coast < 0.78 || weight > 0.70 || n > 0.25) return "minecraft:mangrove_swamp";
        return "minecraft:swamp";
    }

    private static String selectDryland(int x, int z) {
        double n = variant(x, z, 0x400401L, 820.0);
        double rare = variant(x, z, 0x400402L, 1180.0);
        if (n > 0.60) return rare > 0.15 ? "minecraft:eroded_badlands" : "minecraft:badlands";
        if (n > 0.28) return rare > 0.45 ? "minecraft:wooded_badlands" : "minecraft:savanna_plateau";
        if (n > -0.18) return "minecraft:savanna";
        if (n > -0.48) return "minecraft:desert";
        return rare > 0.15 ? "minecraft:windswept_savanna" : "minecraft:desert";
    }

    private static String selectHighland(int x, int y, int z) {
        double n = variant(x, z, 0x500501L, 700.0);
        if (y >= 148) return "minecraft:stony_peaks";
        if (y >= 124) return n > 0.10 ? "minecraft:windswept_hills" : "minecraft:windswept_gravelly_hills";
        if (n > 0.55) return "minecraft:old_growth_spruce_taiga";
        if (n > 0.18) return "minecraft:old_growth_pine_taiga";
        if (n > -0.18) return "minecraft:taiga";
        return "minecraft:windswept_forest";
    }

    private static String selectSnow(int x, int y, int z) {
        double n = variant(x, z, 0x600601L, 760.0);
        double rare = variant(x, z, 0x600602L, 1250.0);
        if (y >= 160) return "minecraft:frozen_peaks";
        if (y >= 140) return "minecraft:jagged_peaks";
        if (y >= 116) return "minecraft:snowy_slopes";
        if (rare > 0.70 && n > 0.12) return "minecraft:ice_spikes";
        if (n > 0.36) return "minecraft:snowy_taiga";
        if (n > -0.12) return "minecraft:grove";
        return "minecraft:snowy_plains";
    }

    private String selectOceanBiome(int x, int z, MapGenConfigManager.KantoConfig cfg) {
        int rx = x - cfg.centerX();
        int rz = z - cfg.centerZ();
        double score = KantoTerrainShaper.macroLandScore(x, z, cfg, KantoLayout.current());
        boolean deep = score < -1.15;

        // Climate boundaries are warped so ocean colors do not introduce new
        // ruler-straight lines around an otherwise organic archipelago.
        double climateZ = rz + KantoShape.fixedNoise(rx, rz, 0x0CEA01L, 760.0) * 430.0;
        double climateX = rx + KantoShape.fixedNoise(rx, rz, 0x0CEA02L, 820.0) * 380.0;
        if (climateZ < -1650) return deep ? "minecraft:deep_frozen_ocean" : "minecraft:frozen_ocean";
        if (climateX < -900 && climateZ < -650) return deep ? "minecraft:deep_cold_ocean" : "minecraft:cold_ocean";
        if (climateX > 850 && climateZ > 720) return deep ? "minecraft:deep_lukewarm_ocean" : "minecraft:warm_ocean";
        if (climateZ > 760) return deep ? "minecraft:deep_lukewarm_ocean" : "minecraft:lukewarm_ocean";
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

    private static double variant(int x, int z, long salt, double scale) {
        double broad = KantoShape.fixedNoise(x, z, salt, scale);
        double detail = KantoShape.fixedNoise(x, z, salt ^ 0x5F5F5FL, scale * 0.42) * 0.28;
        return Math.max(-1.0, Math.min(1.0, broad + detail));
    }

    private static boolean insideArea(KantoLayout.Area a, int x, int z) {
        if (a == null || !a.enabled() || a.radiusX() <= 0 || a.radiusZ() <= 0) return false;
        double dx = (x - a.x()) / (double)a.radiusX();
        double dz = (z - a.z()) / (double)a.radiusZ();
        return dx * dx + dz * dz <= 1.0;
    }
}
