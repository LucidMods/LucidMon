package com.lucidmon.mapgen;

import com.lucidmon.core.MapGenConfigManager;

/**
 * Stable authored macro geography for KANTO_ARCHIPELAGO layout v3.
 *
 * The Kanto outline remains hand-authored, but geography is never evaluated as
 * a raw polygon/ellipse. Deterministic domain warping bends coasts, ecosystem
 * boundaries and offshore islands so the large-scale map stays recognizable
 * while local shapes read as natural geography rather than geometry.
 */
public final class KantoShape {
    public static final double COAST_SCALE = 220.0;

    public enum SurfaceFamily { TEMPERATE, JUNGLE, WETLAND, DRYLAND, HIGHLAND, SNOW }

    private static final long MAIN_WARP_X = 0x4B414E544F31L;
    private static final long MAIN_WARP_Z = 0x4B414E544F32L;

    // Coordinates are relative to the configured center. North is -Z.
    private static final int[][] MAINLAND = {
        {-2380, -1420}, {-2050, -2020}, {-1450, -2420}, {-650, -2580},
        {  250, -2560}, { 1050, -2370}, { 1740, -2010}, { 2240, -1460},
        { 2520,  -760}, { 2510,   120}, { 2290,   840}, { 1880,  1390},
        { 1280,  1610}, {  820,  1510}, {  640,  1120}, {  290,   850},
        { -180,   900}, { -500,  1260}, { -920,  1530}, {-1420,  1660},
        {-1900,  1540}, {-2260,  1260}, {-2470,   760}, {-2410,   180},
        {-2540,  -430}
    };

    /**
     * The chain starts well offshore from the southeast/southern mainland and
     * advances toward the volcano in 300-500 block steps. Sizes deliberately
     * vary so the chain does not read as a row of circles.
     */
    private static final IslandSpec[] VOLCANO_CHAIN = {
        new IslandSpec("chain_east",   980, 2020, 155, 105, 0xC1101L),
        new IslandSpec("chain_2",      565, 2190, 125,  92, 0xC1102L),
        new IslandSpec("chain_3",      120, 2320, 105,  78, 0xC1103L),
        new IslandSpec("chain_4",     -335, 2390, 138,  88, 0xC1104L),
        new IslandSpec("chain_west",  -805, 2360, 112,  76, 0xC1105L)
    };

    /** Additional offshore land so a 6500x6500 reveal is not mostly empty sea. */
    private static final IslandSpec[] SATELLITE_ISLANDS = {
        new IslandSpec("southwest_marsh", -2240, 2050, 215, 150, 0x151A01L),
        new IslandSpec("southeast_jungle", 1740, 2190, 235, 155, 0x151A02L),
        new IslandSpec("east_rock",         2320, 1490, 165, 118, 0x151A03L),
        new IslandSpec("far_south",          420, 2600, 170,  86, 0x151A04L)
    };

    private record IslandSpec(String id, int x, int z, int radiusX, int radiusZ, long salt) {}

    private KantoShape() {}

    public static double mainlandScore(int x, int z, MapGenConfigManager.KantoConfig cfg) {
        double rx = x - cfg.centerX();
        double rz = z - cfg.centerZ();

        // Two warp frequencies: continental-scale bays/headlands plus smaller
        // coastline bends. Both are fixed across world seeds by design.
        double wx = rx
                + fixedNoise((int)rx, (int)rz, MAIN_WARP_X, 760.0) * 105.0
                + fixedNoise((int)rx, (int)rz, MAIN_WARP_X ^ 0x91L, 245.0) * 38.0;
        double wz = rz
                + fixedNoise((int)rx, (int)rz, MAIN_WARP_Z, 720.0) * 105.0
                + fixedNoise((int)rx, (int)rz, MAIN_WARP_Z ^ 0xA7L, 225.0) * 38.0;

        return signedPolygonDistance(wx, wz, MAINLAND) / COAST_SCALE;
    }

    /** Full dry-land mask: mainland, volcano, long stepping chain and satellites. */
    public static double macroLandScore(int x, int z, MapGenConfigManager.KantoConfig cfg, KantoLayout layout) {
        if (!KantoTerrainShaper.insideSafeLand(x, z, cfg)) return -12.0;

        double score = mainlandScore(x, z, cfg);
        KantoLayout.Volcano v = layout.volcano();
        score = Math.max(score, irregularIslandScore(x, z, v.x(), v.z(), v.radiusX(), v.radiusZ(), 0x701CA90L) * 2.0);

        for (IslandSpec island : VOLCANO_CHAIN) {
            score = Math.max(score, irregularIslandScore(
                    x, z, cfg.centerX() + island.x(), cfg.centerZ() + island.z(),
                    island.radiusX(), island.radiusZ(), island.salt()) * 1.8);
        }
        for (IslandSpec island : SATELLITE_ISLANDS) {
            score = Math.max(score, irregularIslandScore(
                    x, z, cfg.centerX() + island.x(), cfg.centerZ() + island.z(),
                    island.radiusX(), island.radiusZ(), island.salt()) * 1.75);
        }

        KantoLayout.Region mushroom = layout.region("mushroomIsland");
        if (mushroom != null && mainlandScore(mushroom.centerX(), mushroom.centerZ(), cfg) <= 0.0) {
            score = Math.max(score, irregularIslandScore(x, z, mushroom.centerX(), mushroom.centerZ(),
                    mushroom.radius(), mushroom.radius(), 0x4D555348L) * 1.6);
        }
        return score;
    }

    public static int satelliteIslandCount() {
        return VOLCANO_CHAIN.length + SATELLITE_ISLANDS.length;
    }

    public static SurfaceFamily surfaceFamily(int x, int z, MapGenConfigManager.KantoConfig cfg) {
        double snow = snowWeight(x, z, cfg);
        double dry = drylandWeight(x, z, cfg);
        double jungle = jungleWeight(x, z, cfg);
        double wet = wetlandWeight(x, z, cfg);
        double high = highlandWeight(x, z, cfg);

        double best = 0.20;
        SurfaceFamily family = SurfaceFamily.TEMPERATE;
        if (jungle > best) { best = jungle; family = SurfaceFamily.JUNGLE; }
        if (wet > best) { best = wet; family = SurfaceFamily.WETLAND; }
        if (dry > best) { best = dry; family = SurfaceFamily.DRYLAND; }
        if (high > best) { best = high; family = SurfaceFamily.HIGHLAND; }
        if (snow > best) family = SurfaceFamily.SNOW;
        return family;
    }

    /** One dominant western jungle and one distant southeastern jungle province. */
    public static double jungleWeight(int x, int z, MapGenConfigManager.KantoConfig cfg) {
        int rx = x - cfg.centerX(), rz = z - cfg.centerZ();
        double west = blobWeight(rx, rz, -1500, 260, 1030, 760, 0xA11001L);
        double southeast = blobWeight(rx, rz, 1250, 1170, 720, 540, 0xA11002L);
        double island = blobWeight(rx, rz, 1740, 2190, 330, 240, 0xA11003L);
        return Math.max(west, Math.max(southeast, island));
    }

    /** Wetlands stay grouped around low southwestern and southern coastal drainage. */
    public static double wetlandWeight(int x, int z, MapGenConfigManager.KantoConfig cfg) {
        int rx = x - cfg.centerX(), rz = z - cfg.centerZ();
        double southwest = blobWeight(rx, rz, -1770, 1120, 720, 470, 0xB22001L);
        double south = blobWeight(rx, rz, 930, 1370, 560, 340, 0xB22002L);
        double island = blobWeight(rx, rz, -2240, 2050, 300, 220, 0xB22003L);
        return Math.max(southwest, Math.max(south, island));
    }

    /** The east remains one coherent dry province instead of scattered orange cells. */
    public static double drylandWeight(int x, int z, MapGenConfigManager.KantoConfig cfg) {
        int rx = x - cfg.centerX(), rz = z - cfg.centerZ();
        double east = blobWeight(rx, rz, 1750, 250, 980, 1180, 0xD33001L);
        double eastIsland = blobWeight(rx, rz, 2320, 1490, 260, 210, 0xD33002L);
        return Math.max(east, eastIsland);
    }

    public static double highlandWeight(int x, int z, MapGenConfigManager.KantoConfig cfg) {
        int rx = x - cfg.centerX(), rz = z - cfg.centerZ();
        return blobWeight(rx, rz, -1500, -1150, 980, 900, 0xE44001L);
    }

    /** Broad connected northern climate mass; Mt. Moon elevation adds local snow separately. */
    public static double snowWeight(int x, int z, MapGenConfigManager.KantoConfig cfg) {
        int rx = x - cfg.centerX(), rz = z - cfg.centerZ();
        return blobWeight(rx, rz, 0, -1980, 2250, 980, 0xF55001L);
    }

    public static boolean isDenseForest(int x, int z, MapGenConfigManager.KantoConfig cfg) {
        return jungleWeight(x, z, cfg) > 0.32;
    }

    public static boolean isMarshCoast(int x, int z, MapGenConfigManager.KantoConfig cfg) {
        double wet = wetlandWeight(x, z, cfg);
        double coast = mainlandScore(x, z, cfg);
        return wet > 0.42 || (wet > 0.18 && coast > 0.0 && coast < 0.90);
    }

    public static boolean isFarNorth(int x, int z, MapGenConfigManager.KantoConfig cfg) {
        return snowWeight(x, z, cfg) > 0.42;
    }

    public static boolean isMainland(int x, int z, MapGenConfigManager.KantoConfig cfg) {
        return mainlandScore(x, z, cfg) > 0.0;
    }

    public static boolean isVolcanoIsland(int x, int z, KantoLayout layout) {
        KantoLayout.Volcano v = layout.volcano();
        return irregularIslandScore(x, z, v.x(), v.z(), v.radiusX(), v.radiusZ(), 0x701CA90L) > 0.0;
    }

    /** Fixed continuous noise usable by terrain/biome/hydrology code. */
    public static double fixedNoise(int x, int z, long salt, double scale) {
        double sx = x / Math.max(1.0, scale);
        double sz = z / Math.max(1.0, scale);
        int x0 = (int)Math.floor(sx), z0 = (int)Math.floor(sz);
        double fx = fade(sx - x0), fz = fade(sz - z0);
        double n00 = hashNoise(x0, z0, salt);
        double n10 = hashNoise(x0 + 1, z0, salt);
        double n01 = hashNoise(x0, z0 + 1, salt);
        double n11 = hashNoise(x0 + 1, z0 + 1, salt);
        double nx0 = lerp(n00, n10, fx);
        double nx1 = lerp(n01, n11, fx);
        return lerp(nx0, nx1, fz);
    }

    private static double blobWeight(int x, int z, int cx, int cz, int rx, int rz, long salt) {
        double score = irregularIslandScore(x, z, cx, cz, rx, rz, salt);
        return smoothStep(clamp01(score * 1.28));
    }

    private static double irregularIslandScore(int x, int z, int cx, int cz, int rx, int rz, long salt) {
        double dx = x - cx, dz = z - cz;
        double wx = dx
                + fixedNoise(x, z, salt ^ 0x118L, 410.0) * rx * 0.18
                + fixedNoise(x, z, salt ^ 0x22DL, 145.0) * rx * 0.07;
        double wz = dz
                + fixedNoise(x, z, salt ^ 0x339L, 390.0) * rz * 0.18
                + fixedNoise(x, z, salt ^ 0x44BL, 135.0) * rz * 0.07;
        double nx = Math.abs(wx / Math.max(1.0, rx));
        double nz = Math.abs(wz / Math.max(1.0, rz));
        double p = 2.15;
        double d = Math.pow(Math.pow(nx, p) + Math.pow(nz, p), 1.0 / p);
        double edge = fixedNoise(x, z, salt ^ 0x55DL, 95.0) * 0.075;
        return 1.0 - d + edge;
    }

    private static double signedPolygonDistance(double x, double z, int[][] polygon) {
        boolean inside = pointInPolygon(x, z, polygon);
        double min = Double.POSITIVE_INFINITY;
        for (int i = 0, j = polygon.length - 1; i < polygon.length; j = i++) {
            double d = pointSegmentDistance(x, z,
                    polygon[j][0], polygon[j][1], polygon[i][0], polygon[i][1]);
            min = Math.min(min, d);
        }
        return inside ? min : -min;
    }

    private static boolean pointInPolygon(double x, double z, int[][] polygon) {
        boolean inside = false;
        for (int i = 0, j = polygon.length - 1; i < polygon.length; j = i++) {
            double xi = polygon[i][0], zi = polygon[i][1];
            double xj = polygon[j][0], zj = polygon[j][1];
            boolean crosses = ((zi > z) != (zj > z))
                    && (x < (xj - xi) * (z - zi) / ((zj - zi) == 0.0 ? 1.0e-9 : (zj - zi)) + xi);
            if (crosses) inside = !inside;
        }
        return inside;
    }

    private static double pointSegmentDistance(double px, double pz, double ax, double az, double bx, double bz) {
        double vx = bx - ax, vz = bz - az;
        double len2 = vx * vx + vz * vz;
        if (len2 <= 1.0e-9) return Math.hypot(px - ax, pz - az);
        double t = Math.max(0.0, Math.min(1.0, ((px - ax) * vx + (pz - az) * vz) / len2));
        return Math.hypot(px - (ax + t * vx), pz - (az + t * vz));
    }

    private static double hashNoise(int x, int z, long salt) {
        long h = salt ^ (x * 0x9E3779B97F4A7C15L) ^ (z * 0xC2B2AE3D27D4EB4FL);
        h ^= h >>> 30;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 27;
        h *= 0x94D049BB133111EBL;
        h ^= h >>> 31;
        return ((h >>> 11) * 0x1.0p-53) * 2.0 - 1.0;
    }

    private static double clamp01(double v) { return Math.max(0.0, Math.min(1.0, v)); }
    private static double smoothStep(double v) { return v * v * (3.0 - 2.0 * v); }
    private static double fade(double v) { return v * v * (3.0 - 2.0 * v); }
    private static double lerp(double a, double b, double t) { return a + (b - a) * t; }
}
