package com.lucidmon.mapgen;

import com.lucidmon.core.MapGenConfigManager;

/**
 * Hand-authored macro geography for the Kanto Archipelago profile.
 *
 * The first MapGen prototype built the mainland by taking the maximum of several
 * large ellipses. That was useful for proving that absolute-coordinate shaping
 * worked, but it also produced the obvious circular lobes visible on a world map.
 * Layout v2 instead uses an intentionally asymmetric polygon for the mainland and
 * signed edge distance for coast blending. Local seeded noise is still applied by
 * {@link KantoTerrainShaper}; this class only defines the stable macro silhouette.
 */
public final class KantoShape {
    /** Signed-distance score is measured in roughly this many blocks per unit. */
    public static final double COAST_SCALE = 220.0;

    // Coordinates are relative to the configured Kanto center. North is -Z.
    // The outline intentionally contains the broad northern body, western and
    // eastern peninsulas, a southern bay and the southwest starter-town spur.
    private static final int[][] MAINLAND = {
        {-2380, -1420}, {-2050, -2020}, {-1450, -2420}, {-650, -2580},
        {  250, -2560}, { 1050, -2370}, { 1740, -2010}, { 2240, -1460},
        { 2520,  -760}, { 2510,   120}, { 2290,   840}, { 1880,  1390},
        { 1280,  1610}, {  820,  1510}, {  640,  1120}, {  290,   850},
        { -180,   900}, { -500,  1260}, { -920,  1530}, {-1420,  1660},
        {-1900,  1540}, {-2260,  1260}, {-2470,   760}, {-2410,   180},
        {-2540,  -430}
    };

    // Dense tree masses inspired by the illustrated Kanto map. These become
    // jungle-family biomes rather than radial biome circles.
    private static final int[][] WEST_FOREST = {
        {-2260, -420}, {-1650, -720}, {-980, -560}, {-560, -170},
        {-760,  390}, {-1320,  760}, {-2030,  600}, {-2360,  180}
    };
    private static final int[][] NORTH_FOREST = {
        {-920, -2160}, {-120, -2350}, {760, -2180}, {1260, -1730},
        {920, -1290}, {250, -1190}, {-520, -1390}
    };
    private static final int[][] SOUTH_FOREST = {
        {420, 760}, {1120, 720}, {1750, 980}, {1590, 1390},
        {900, 1510}, {420, 1260}
    };

    private KantoShape() {}

    public static double mainlandScore(int x, int z, MapGenConfigManager.KantoConfig cfg) {
        return signedPolygonDistance(x - cfg.centerX(), z - cfg.centerZ(), MAINLAND) / COAST_SCALE;
    }

    /**
     * Full dry-land mask: mainland, volcano, the small eastern stepping islets,
     * and optional profile islands such as Seafoam/mushroom fields.
     */
    public static double macroLandScore(int x, int z, MapGenConfigManager.KantoConfig cfg, KantoLayout layout) {
        if (!KantoTerrainShaper.insideSafeLand(x, z, cfg)) return -12.0;

        double score = mainlandScore(x, z, cfg);
        KantoLayout.Volcano v = layout.volcano();
        score = Math.max(score, ellipseScore(x, z, v.x(), v.z(), v.radiusX(), v.radiusZ()) * 2.0);

        // Three small islands to the right/east of the volcano, matching the
        // visual progression in the Kanto reference rather than a giant chain.
        score = Math.max(score, ellipseScore(x, z, v.x() + 610, v.z() + 20, 135, 105) * 1.7);
        score = Math.max(score, ellipseScore(x, z, v.x() + 875, v.z() - 45, 105, 82) * 1.7);
        score = Math.max(score, ellipseScore(x, z, v.x() + 1080, v.z() - 105, 78, 62) * 1.7);

        KantoLayout.Region mushroom = layout.region("mushroomIsland");
        if (mushroom != null) {
            score = Math.max(score, ellipseScore(x, z, mushroom.centerX(), mushroom.centerZ(),
                    mushroom.radius(), mushroom.radius()) * 1.6);
        }

        KantoLayout.Area seafoam = layout.area("seafoamIslands");
        if (seafoam != null && seafoam.enabled()) {
            int rx = Math.max(150, seafoam.radiusX());
            int rz = Math.max(130, seafoam.radiusZ());
            score = Math.max(score, ellipseScore(x, z, seafoam.x() - rx / 3, seafoam.z() - 35,
                    Math.max(90, rx * 2 / 3), Math.max(80, rz * 3 / 4)) * 1.7);
            score = Math.max(score, ellipseScore(x, z, seafoam.x() + rx / 3, seafoam.z() + 65,
                    Math.max(90, rx * 2 / 3), Math.max(80, rz * 3 / 4)) * 1.7);
        }
        return score;
    }

    public static boolean isDenseForest(int x, int z, MapGenConfigManager.KantoConfig cfg) {
        int rx = x - cfg.centerX(), rz = z - cfg.centerZ();
        return pointInPolygon(rx, rz, WEST_FOREST)
                || pointInPolygon(rx, rz, NORTH_FOREST)
                || pointInPolygon(rx, rz, SOUTH_FOREST);
    }

    /** Wooded coastal bands become swamp/mangrove rather than vertical forest cliffs. */
    public static boolean isMarshCoast(int x, int z, MapGenConfigManager.KantoConfig cfg) {
        double coast = mainlandScore(x, z, cfg);
        if (coast <= 0.0 || coast > 0.80) return false;
        int rx = x - cfg.centerX(), rz = z - cfg.centerZ();
        return (rx < -900 && rz > 250) || (rz > 900 && rx > -350) || (rx > 1550 && rz > 150);
    }

    public static boolean isFarNorth(int x, int z, MapGenConfigManager.KantoConfig cfg) {
        return z - cfg.centerZ() <= -1500;
    }

    public static boolean isMainland(int x, int z, MapGenConfigManager.KantoConfig cfg) {
        return mainlandScore(x, z, cfg) > 0.0;
    }

    private static double ellipseScore(int x, int z, int cx, int cz, int rx, int rz) {
        double dx = (x - cx) / (double)Math.max(1, rx);
        double dz = (z - cz) / (double)Math.max(1, rz);
        return 1.0 - Math.sqrt(dx * dx + dz * dz);
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
}
