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
 * The serialized biome list makes the preset datapack-friendly while the
 * ecological assignment follows the hand-authored Kanto macro geography.
 */
public final class KantoBiomeSource extends BiomeSource {
    public static final MapCodec<KantoBiomeSource> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Biome.LIST_CODEC.fieldOf("biomes").forGetter(source -> source.allowedBiomes)
    ).apply(instance, KantoBiomeSource::new));

    private static final int PATCH_SIZE = 224;

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

        // Meaningful cave-biome volumes. Cave placement remains broad and
        // landmark-driven; the radial visual bug was a surface-biome issue.
        if (macroLand && y < 48) {
            if (y < 4 && (insideRegion(layout.region("northSnowCrown"), x, z)
                    || insideRegion(layout.region("northwestHighlands"), x, z))) {
                return biome("minecraft:deep_dark");
            }
            if (y < 28 && (isEastDryland(x, z, cfg) || insideArea(layout.area("rockTunnel"), x, z)
                    || insideVolcano(layout.volcano(), x, z))) {
                return biome("minecraft:dripstone_caves");
            }
            if (y < 42 && (KantoShape.isDenseForest(x, z, cfg)
                    || KantoShape.isMarshCoast(x, z, cfg)
                    || isCentral(x, z, cfg))) {
                return biome("minecraft:lush_caves");
            }
        }

        if (macroLand) return surfaceBiome(x, y, z, cfg, layout);
        return biome(selectOceanBiome(x, z, cfg));
    }

    private Holder<Biome> surfaceBiome(int x, int y, int z, MapGenConfigManager.KantoConfig cfg, KantoLayout layout) {
        double coast = KantoTerrainShaper.macroLandScore(x, z, cfg, layout);

        // Mt. Moon itself is snow-capped, then the country north of it becomes
        // progressively more snow dominated.
        KantoLayout.Area moon = layout.area("mtMoon");
        if (moon != null && moon.enabled() && insideArea(moon, x, z) && y >= 108) {
            if (y >= 154) return biome("minecraft:frozen_peaks");
            if (y >= 132) return biome("minecraft:jagged_peaks");
            return biome("minecraft:snowy_slopes");
        }

        if (KantoShape.isFarNorth(x, z, cfg)) {
            if (coast < 0.42) return biome("minecraft:snowy_beach");
            return biome(selectIrregular(List.of(
                    "minecraft:snowy_plains", "minecraft:snowy_taiga", "minecraft:grove",
                    "minecraft:snowy_slopes", "minecraft:ice_spikes"), x, z, 0x51A9D4L));
        }

        // Wooded coastlines in the reference map are deliberately translated
        // into wetland ecology rather than generic forest touching the sea.
        if (KantoShape.isMarshCoast(x, z, cfg)) {
            return biome(selectIrregular(List.of("minecraft:swamp", "minecraft:mangrove_swamp"), x, z, 0xA77A11L));
        }

        // The first ~70-100 blocks of an ordinary shore use beach/stony shore,
        // matching the new walkable coastal shelf in the terrain shaper.
        if (coast < 0.38) {
            int rx = x - cfg.centerX();
            if (rx > 1500 || rx < -1900) return biome(selectIrregular(List.of("minecraft:beach", "minecraft:stony_shore"), x, z, 0xC0457L));
            return biome("minecraft:beach");
        }

        // Dense tree masses shown on the Kanto reference become jungle-family
        // biomes. This is intentionally a semantic mask, not a circular radius.
        if (KantoShape.isDenseForest(x, z, cfg)) {
            return biome(selectIrregular(List.of(
                    "minecraft:jungle", "minecraft:sparse_jungle", "minecraft:bamboo_jungle"), x, z, 0xD3E5EL));
        }

        KantoLayout.Region region;
        if (isEastDryland(x, z, cfg)) region = layout.region("eastDrylands");
        else if (isNorthwestHighland(x, z, cfg)) region = layout.region("northwestHighlands");
        else if (isSoutheast(x, z, cfg)) region = layout.region("southeastJungleChain");
        else region = layout.region("centralTemperate");

        List<String> candidates = surfaceCandidates(region);
        if (candidates.isEmpty()) candidates = List.of("minecraft:plains", "minecraft:forest", "minecraft:meadow");
        return biome(selectIrregular(candidates, x, z, region == null ? 0xCA470L : stableSalt(region.id())));
    }

    /**
     * Jittered Voronoi patches replace the old angle/radius sector calculation.
     * Boundaries are irregular cellular regions with no shared circular center,
     * eliminating the visible pie slices and concentric rings from prototype 1.
     */
    private static String selectIrregular(List<String> candidates, int x, int z, long salt) {
        if (candidates.size() == 1) return candidates.get(0);
        int gx = Math.floorDiv(x, PATCH_SIZE);
        int gz = Math.floorDiv(z, PATCH_SIZE);
        double best = Double.POSITIVE_INFINITY;
        long bestHash = 0L;

        for (int ox = -1; ox <= 1; ox++) {
            for (int oz = -1; oz <= 1; oz++) {
                int cx = gx + ox, cz = gz + oz;
                long h = hash(cx, cz, salt);
                double jx = (((h >>> 8) & 0xffffL) / 65535.0 - 0.5) * PATCH_SIZE * 0.72;
                double jz = (((h >>> 32) & 0xffffL) / 65535.0 - 0.5) * PATCH_SIZE * 0.72;
                double px = (cx + 0.5) * PATCH_SIZE + jx;
                double pz = (cz + 0.5) * PATCH_SIZE + jz;
                double dx = x - px, dz = z - pz;
                double d = dx * dx + dz * dz;
                if (d < best) { best = d; bestHash = h; }
            }
        }
        return candidates.get(Math.floorMod((int)(bestHash ^ (bestHash >>> 32)), candidates.size()));
    }

    private static List<String> surfaceCandidates(KantoLayout.Region region) {
        if (region == null) return List.of();
        return region.biomes().stream().filter(KantoBiomeSource::isOrdinaryLandBiome).toList();
    }

    private static boolean isOrdinaryLandBiome(String id) {
        return !id.contains("ocean")
                && !id.equals("minecraft:deep_dark")
                && !id.equals("minecraft:lush_caves")
                && !id.equals("minecraft:dripstone_caves")
                && !id.equals("minecraft:river")
                && !id.equals("minecraft:frozen_river")
                && !id.equals("minecraft:beach")
                && !id.equals("minecraft:snowy_beach")
                && !id.equals("minecraft:stony_shore");
    }

    private String selectOceanBiome(int x, int z, MapGenConfigManager.KantoConfig cfg) {
        int dx = x - cfg.centerX();
        int dz = z - cfg.centerZ();
        double score = KantoTerrainShaper.macroLandScore(x, z, cfg, KantoLayout.current());
        boolean deep = score < -1.15;

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

    private static boolean isCentral(int x, int z, MapGenConfigManager.KantoConfig cfg) {
        int rx = x - cfg.centerX(), rz = z - cfg.centerZ();
        return Math.abs(rx) < 1300 && Math.abs(rz) < 1200;
    }

    private static boolean isEastDryland(int x, int z, MapGenConfigManager.KantoConfig cfg) {
        int rx = x - cfg.centerX(), rz = z - cfg.centerZ();
        return rx > 1100 && rz > -1100 && rz < 950;
    }

    private static boolean isNorthwestHighland(int x, int z, MapGenConfigManager.KantoConfig cfg) {
        int rx = x - cfg.centerX(), rz = z - cfg.centerZ();
        return rx < -950 && rz < -500;
    }

    private static boolean isSoutheast(int x, int z, MapGenConfigManager.KantoConfig cfg) {
        int rx = x - cfg.centerX(), rz = z - cfg.centerZ();
        return rx > 700 && rz > 650;
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

    private static long stableSalt(String id) {
        long h = 0xcbf29ce484222325L;
        for (int i = 0; i < id.length(); i++) {
            h ^= id.charAt(i);
            h *= 0x100000001b3L;
        }
        return h;
    }

    private static long hash(int x, int z, long salt) {
        long h = salt ^ (x * 0x9E3779B97F4A7C15L) ^ (z * 0xC2B2AE3D27D4EB4FL);
        h ^= h >>> 30;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 27;
        h *= 0x94D049BB133111EBL;
        return h ^ (h >>> 31);
    }
}
