package com.lucidmon.mapgen;

import com.lucidmon.core.MapGenConfigManager;

import java.util.List;

/**
 * Deterministic authored hydrology for KANTO_ARCHIPELAGO layout v3.
 *
 * Rivers are broad spline-like polylines evaluated through a warped coordinate
 * field so channels meander instead of forming ruler-straight trenches. Lakes
 * use the same irregular masking approach as islands. Everything is relative
 * to the configured Kanto center and therefore remains stable across seeds.
 */
public final class KantoHydrology {
    public record Point(int x, int z) {}

    private record RiverSpec(
            String id,
            List<Point> points,
            int startWidth,
            int endWidth,
            int startWaterY,
            int endWaterY,
            int depth,
            long salt,
            int minX,
            int maxX,
            int minZ,
            int maxZ
    ) {}

    private record LakeSpec(String id, int x, int z, int radiusX, int radiusZ, int waterY, int depth, long salt) {}

    /**
     * channel is 0 outside open water and rises toward 1 at the channel center.
     * bank extends farther than channel and is used to lower terrain gradually.
     */
    public record Sample(String id, boolean lake, double channel, double bank, int waterY, int bedY, boolean frozen) {
        public boolean hasWater() { return channel > 0.0; }
        public boolean affectsTerrain() { return bank > 0.0; }
    }

    private static final Sample NONE = new Sample("", false, 0.0, 0.0, Integer.MIN_VALUE, Integer.MIN_VALUE, false);

    private static final List<RiverSpec> RIVERS = List.of(
        river("north_melt",
                List.of(new Point(20,-2210), new Point(-170,-1810), new Point(-70,-1420), new Point(210,-1010),
                        new Point(120,-620), new Point(360,-230), new Point(520,260), new Point(690,760), new Point(860,1350)),
                7, 24, 80, 63, 4, 0x710001L),
        river("northwest_run",
                List.of(new Point(-1770,-1600), new Point(-1590,-1260), new Point(-1420,-900), new Point(-1190,-560),
                        new Point(-1050,-160), new Point(-1220,230), new Point(-1450,650), new Point(-1760,1260)),
                7, 22, 78, 63, 4, 0x710002L),
        river("moon_run",
                List.of(new Point(-930,-1080), new Point(-710,-810), new Point(-510,-520), new Point(-340,-160),
                        new Point(-430,240), new Point(-300,620), new Point(-170,1050)),
                6, 18, 77, 64, 4, 0x710003L),
        river("east_run",
                List.of(new Point(1510,-1060), new Point(1440,-720), new Point(1580,-390), new Point(1420,-20),
                        new Point(1580,340), new Point(1810,720), new Point(1770,1110), new Point(1600,1460)),
                6, 20, 76, 63, 4, 0x710004L),
        river("central_run",
                List.of(new Point(610,-790), new Point(760,-470), new Point(700,-120), new Point(540,260),
                        new Point(390,620), new Point(360,970), new Point(430,1360)),
                5, 17, 73, 63, 3, 0x710005L)
    );

    private static final List<LakeSpec> LAKES = List.of(
        new LakeSpec("north_lake",       250, -1660, 180, 125, 71, 5, 0x720001L),
        new LakeSpec("moon_foothill",   -520,  -430, 105,  76, 69, 4, 0x720002L),
        new LakeSpec("southwest_marsh",-1670,   900, 145, 100, 65, 3, 0x720003L),
        new LakeSpec("east_upland",     1280,  -520, 115,  82, 69, 4, 0x720004L),
        new LakeSpec("south_lowland",    840,  1060, 128,  92, 65, 3, 0x720005L)
    );

    private KantoHydrology() {}

    public static int riverCount() { return RIVERS.size(); }
    public static int lakeCount() { return LAKES.size(); }

    public static Sample sample(int x, int z, MapGenConfigManager.KantoConfig cfg) {
        int rx = x - cfg.centerX();
        int rz = z - cfg.centerZ();
        Sample best = NONE;

        for (RiverSpec river : RIVERS) {
            if (rx < river.minX() - 80 || rx > river.maxX() + 80 || rz < river.minZ() - 80 || rz > river.maxZ() + 80) continue;
            Sample s = sampleRiver(rx, rz, river);
            if (s.bank() > best.bank()) best = s;
        }

        for (LakeSpec lake : LAKES) {
            if (Math.abs(rx - lake.x()) > lake.radiusX() + 90 || Math.abs(rz - lake.z()) > lake.radiusZ() + 90) continue;
            Sample s = sampleLake(rx, rz, lake);
            if (s.bank() > best.bank()) best = s;
        }
        return best;
    }

    private static Sample sampleRiver(int x, int z, RiverSpec river) {
        // Warping the query against an authored polyline bends the apparent
        // channel continuously without moving its guaranteed endpoints.
        double wx = x + KantoShape.fixedNoise(x, z, river.salt() ^ 0x91L, 360.0) * 58.0
                + KantoShape.fixedNoise(x, z, river.salt() ^ 0xA3L, 145.0) * 16.0;
        double wz = z + KantoShape.fixedNoise(x, z, river.salt() ^ 0xB5L, 330.0) * 58.0
                + KantoShape.fixedNoise(x, z, river.salt() ^ 0xC7L, 135.0) * 16.0;

        double bestDistance = Double.POSITIVE_INFINITY;
        double bestProgress = 0.0;
        int segmentCount = river.points().size() - 1;
        for (int i = 0; i < segmentCount; i++) {
            Point a = river.points().get(i), b = river.points().get(i + 1);
            double vx = b.x() - a.x(), vz = b.z() - a.z();
            double len2 = Math.max(1.0, vx * vx + vz * vz);
            double t = clamp01(((wx - a.x()) * vx + (wz - a.z()) * vz) / len2);
            double px = a.x() + vx * t, pz = a.z() + vz * t;
            double distance = Math.hypot(wx - px, wz - pz);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestProgress = (i + t) / segmentCount;
            }
        }

        double width = lerp(river.startWidth(), river.endWidth(), bestProgress);
        double bankWidth = width + 22.0 + width * 0.75;
        double channel = clamp01(1.0 - bestDistance / Math.max(1.0, width));
        double bank = clamp01(1.0 - bestDistance / Math.max(1.0, bankWidth));
        if (bank <= 0.0) return NONE;

        int waterY = (int)Math.round(lerp(river.startWaterY(), river.endWaterY(), bestProgress));
        int depth = Math.max(2, river.depth() + (int)Math.round(bestProgress * 1.5));
        int bedY = waterY - depth;
        boolean frozen = z <= -1240;
        return new Sample(river.id(), false, channel, bank, waterY, bedY, frozen);
    }

    private static Sample sampleLake(int x, int z, LakeSpec lake) {
        double dx = x - lake.x(), dz = z - lake.z();
        double wx = dx + KantoShape.fixedNoise(x, z, lake.salt() ^ 0x31L, 235.0) * lake.radiusX() * 0.20
                + KantoShape.fixedNoise(x, z, lake.salt() ^ 0x37L, 88.0) * lake.radiusX() * 0.06;
        double wz = dz + KantoShape.fixedNoise(x, z, lake.salt() ^ 0x41L, 215.0) * lake.radiusZ() * 0.20
                + KantoShape.fixedNoise(x, z, lake.salt() ^ 0x43L, 82.0) * lake.radiusZ() * 0.06;
        double nx = wx / Math.max(1.0, lake.radiusX());
        double nz = wz / Math.max(1.0, lake.radiusZ());
        double d = Math.sqrt(nx * nx + nz * nz);
        double score = 1.0 - d + KantoShape.fixedNoise(x, z, lake.salt() ^ 0x55L, 72.0) * 0.06;
        double channel = clamp01(score / 0.18);
        double bank = clamp01((score + 0.28) / 0.28);
        if (bank <= 0.0) return NONE;
        boolean frozen = z <= -1240;
        return new Sample(lake.id(), true, channel, bank, lake.waterY(), lake.waterY() - lake.depth(), frozen);
    }

    private static RiverSpec river(String id, List<Point> points, int startWidth, int endWidth,
                                   int startWaterY, int endWaterY, int depth, long salt) {
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (Point p : points) {
            minX = Math.min(minX, p.x()); maxX = Math.max(maxX, p.x());
            minZ = Math.min(minZ, p.z()); maxZ = Math.max(maxZ, p.z());
        }
        return new RiverSpec(id, List.copyOf(points), startWidth, endWidth, startWaterY, endWaterY,
                depth, salt, minX, maxX, minZ, maxZ);
    }

    private static double clamp01(double v) { return Math.max(0.0, Math.min(1.0, v)); }
    private static double lerp(double a, double b, double t) { return a + (b - a) * t; }
}
