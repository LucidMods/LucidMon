package com.lucidmon.mapgen;

import com.lucidmon.core.MapGenConfigManager;

import java.util.List;

/**
 * Deterministic authored drainage network for KANTO_ARCHIPELAGO layout v4.
 *
 * Rivers are modeled as connected drainage systems rather than independent
 * north/south polylines. Major rivers begin in high terrain or at elevated
 * lakes, tributaries feed the major channels, and most systems terminate at
 * the ocean. Low-frequency coordinate warping supplies natural meanders while
 * preserving the authored source-to-mouth topology.
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
            boolean tributary,
            int minX,
            int maxX,
            int minZ,
            int maxZ
    ) {}

    private record LakeSpec(String id, int x, int z, int radiusX, int radiusZ,
                            int waterY, int depth, long salt) {}

    /**
     * channel is 0 outside open water and rises toward 1 at the channel center.
     * bank extends farther than channel and is used to lower terrain gradually.
     */
    public record Sample(String id, boolean lake, double channel, double bank,
                         int waterY, int bedY, boolean frozen) {
        public boolean hasWater() { return channel > 0.0; }
        public boolean affectsTerrain() { return bank > 0.0; }
    }

    private static final Sample NONE =
            new Sample("", false, 0.0, 0.0, Integer.MIN_VALUE, Integer.MIN_VALUE, false);

    /*
     * Lakes are placed in elevated terrain and are referenced by several river
     * sources/outlets. This gives the drainage network visible hydrologic logic:
     * mountain -> lake -> river -> ocean rather than isolated blue lines.
     */
    private static final List<LakeSpec> LAKES = List.of(
        new LakeSpec("north_lake",        250, -1660, 180, 125, 71, 5, 0x720001L),
        new LakeSpec("moon_foothill",    -520,  -430, 105,  76, 69, 4, 0x720002L),
        new LakeSpec("southwest_marsh", -1670,   900, 145, 100, 65, 3, 0x720003L),
        new LakeSpec("east_upland",      1280,  -520, 115,  82, 69, 4, 0x720004L),
        new LakeSpec("south_lowland",     840,  1060, 128,  92, 65, 3, 0x720005L)
    );

    /*
     * Major drainage systems deliberately use different bearings. They are
     * not all north/south and they do not share the same visual cadence.
     *
     * Water levels descend monotonically from source to mouth. A source may
     * be an elevated mountain spring or an authored lake outlet.
     */
    private static final List<RiverSpec> RIVERS = List.of(
        river("snowmelt_west",
                List.of(new Point(40,-2180), new Point(-180,-1880), new Point(-430,-1590),
                        new Point(-760,-1430), new Point(-980,-1160), new Point(-820,-850),
                        new Point(-1110,-520), new Point(-1430,-260), new Point(-1710,120),
                        new Point(-1980,470), new Point(-2260,720)),
                6, 24, 124, 63, 5, 0x710101L, false),

        river("moon_southwest",
                List.of(new Point(-520,-430), new Point(-700,-190), new Point(-860,80),
                        new Point(-1120,250), new Point(-1320,520), new Point(-1510,780),
                        new Point(-1740,930), new Point(-2010,1110), new Point(-2280,1280)),
                5, 22, 69, 63, 4, 0x710102L, false),

        river("northlake_southeast",
                List.of(new Point(250,-1660), new Point(470,-1390), new Point(690,-1110),
                        new Point(610,-820), new Point(820,-570), new Point(1080,-350),
                        new Point(1240,-60), new Point(1510,220), new Point(1700,520),
                        new Point(1950,790), new Point(2180,1120)),
                6, 25, 71, 63, 5, 0x710103L, false),

        river("east_upland_south",
                List.of(new Point(1280,-520), new Point(1530,-410), new Point(1690,-180),
                        new Point(1600,100), new Point(1770,330), new Point(1640,610),
                        new Point(1820,870), new Point(2050,1120), new Point(2200,1460),
                        new Point(2320,1810)),
                5, 21, 69, 63, 4, 0x710104L, false),

        river("central_highlands_south",
                List.of(new Point(700,-1120), new Point(520,-870), new Point(330,-650),
                        new Point(80,-470), new Point(-20,-180), new Point(180,80),
                        new Point(420,330), new Point(610,650), new Point(520,940),
                        new Point(700,1240), new Point(820,1570), new Point(690,1910),
                        new Point(820,2260)),
                5, 22, 116, 63, 4, 0x710105L, false),

        river("western_crossflow",
                List.of(new Point(-1840,-900), new Point(-1530,-760), new Point(-1260,-610),
                        new Point(-1010,-470), new Point(-730,-500), new Point(-420,-650),
                        new Point(-120,-790), new Point(220,-720), new Point(540,-540),
                        new Point(860,-420), new Point(1160,-300), new Point(1480,-120),
                        new Point(1800,90)),
                5, 21, 112, 63, 4, 0x710106L, false),

        // Tributaries: short, higher-elevation feeders that merge into the
        // major systems instead of terminating in open plains.
        river("nw_feeder",
                List.of(new Point(-1780,-1480), new Point(-1600,-1260), new Point(-1420,-1080),
                        new Point(-1260,-920), new Point(-1110,-780)),
                3, 9, 116, 84, 3, 0x710201L, true),

        river("moon_feeder",
                List.of(new Point(-1020,-260), new Point(-850,-300), new Point(-700,-360),
                        new Point(-540,-430)),
                3, 10, 92, 69, 3, 0x710202L, true),

        river("northlake_feeder",
                List.of(new Point(70,-1390), new Point(100,-1250), new Point(180,-1110),
                        new Point(270,-980), new Point(360,-860)),
                3, 10, 108, 78, 3, 0x710203L, true),

        river("east_feeder",
                List.of(new Point(1660,-980), new Point(1570,-820), new Point(1460,-690),
                        new Point(1360,-580), new Point(1280,-520)),
                3, 10, 104, 69, 3, 0x710204L, true),

        river("central_north_feeder",
                List.of(new Point(1100,-1320), new Point(970,-1130), new Point(850,-980),
                        new Point(730,-900), new Point(610,-820)),
                3, 10, 120, 84, 3, 0x710205L, true),

        river("central_west_feeder",
                List.of(new Point(-60,-1080), new Point(40,-940), new Point(180,-830),
                        new Point(300,-720), new Point(520,-620)),
                3, 9, 108, 82, 3, 0x710206L, true),

        river("southwest_feeder",
                List.of(new Point(-1390,520), new Point(-1510,650), new Point(-1590,760),
                        new Point(-1670,900)),
                3, 9, 86, 65, 3, 0x710207L, true),

        river("southland_feeder",
                List.of(new Point(420,920), new Point(560,980), new Point(700,1030),
                        new Point(840,1060)),
                3, 10, 88, 65, 3, 0x710208L, true),

        river("eastern_feeder",
                List.of(new Point(1900,420), new Point(1810,520), new Point(1740,650),
                        new Point(1700,790)),
                3, 9, 94, 78, 3, 0x710209L, true)
    );

    private KantoHydrology() {}

    public static int riverCount() { return RIVERS.size(); }
    public static int majorRiverCount() {
        int count = 0;
        for (RiverSpec river : RIVERS) if (!river.tributary()) count++;
        return count;
    }
    public static int tributaryCount() {
        int count = 0;
        for (RiverSpec river : RIVERS) if (river.tributary()) count++;
        return count;
    }
    public static int lakeCount() { return LAKES.size(); }

    public static Sample sample(int x, int z, MapGenConfigManager.KantoConfig cfg) {
        int rx = x - cfg.centerX();
        int rz = z - cfg.centerZ();
        Sample best = NONE;

        for (RiverSpec river : RIVERS) {
            if (rx < river.minX() - 100 || rx > river.maxX() + 100
                    || rz < river.minZ() - 100 || rz > river.maxZ() + 100) continue;
            Sample s = sampleRiver(rx, rz, river);
            if (s.bank() > best.bank()) best = s;
        }

        for (LakeSpec lake : LAKES) {
            if (Math.abs(rx - lake.x()) > lake.radiusX() + 100
                    || Math.abs(rz - lake.z()) > lake.radiusZ() + 100) continue;
            Sample s = sampleLake(rx, rz, lake);
            if (s.bank() > best.bank()) best = s;
        }
        return best;
    }

    private static Sample sampleRiver(int x, int z, RiverSpec river) {
        /*
         * Multi-scale domain warping bends the channel continuously without
         * moving its authored source/mouth topology. A smaller high-frequency
         * term prevents every bend from having the same radius.
         */
        double wx = x
                + KantoShape.fixedNoise(x, z, river.salt() ^ 0x91L, 520.0) * 82.0
                + KantoShape.fixedNoise(x, z, river.salt() ^ 0xA3L, 180.0) * 24.0;
        double wz = z
                + KantoShape.fixedNoise(x, z, river.salt() ^ 0xB5L, 470.0) * 82.0
                + KantoShape.fixedNoise(x, z, river.salt() ^ 0xC7L, 165.0) * 24.0;

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

        double width = lerp(river.startWidth(), river.endWidth(), smoothStep(bestProgress));
        double bankWidth = width + 26.0 + width * 0.85;
        double channel = clamp01(1.0 - bestDistance / Math.max(1.0, width));
        double bank = clamp01(1.0 - bestDistance / Math.max(1.0, bankWidth));
        if (bank <= 0.0) return NONE;

        /*
         * Water always falls downstream. Major rivers may start in mountains
         * or elevated lakes; tributaries finish above sea level where they
         * merge into a larger drainage system.
         */
        double progress = smoothStep(bestProgress);
        int waterY = (int)Math.round(lerp(river.startWaterY(), river.endWaterY(), progress));
        int depth = Math.max(2, river.depth() + (int)Math.round(progress * 2.0));
        int bedY = waterY - depth;

        boolean frozen = river.startWaterY() >= 90
                && (z < -1180 || (bestProgress < 0.18 && waterY > 76));
        return new Sample(river.id(), false, channel, bank, waterY, bedY, frozen);
    }

    private static Sample sampleLake(int x, int z, LakeSpec lake) {
        double dx = x - lake.x(), dz = z - lake.z();
        double wx = dx
                + KantoShape.fixedNoise(x, z, lake.salt() ^ 0x31L, 235.0) * lake.radiusX() * 0.20
                + KantoShape.fixedNoise(x, z, lake.salt() ^ 0x37L, 88.0) * lake.radiusX() * 0.06;
        double wz = dz
                + KantoShape.fixedNoise(x, z, lake.salt() ^ 0x41L, 215.0) * lake.radiusZ() * 0.20
                + KantoShape.fixedNoise(x, z, lake.salt() ^ 0x43L, 82.0) * lake.radiusZ() * 0.06;
        double nx = wx / Math.max(1.0, lake.radiusX());
        double nz = wz / Math.max(1.0, lake.radiusZ());
        double d = Math.sqrt(nx * nx + nz * nz);
        double score = 1.0 - d + KantoShape.fixedNoise(x, z, lake.salt() ^ 0x55L, 72.0) * 0.06;
        double channel = clamp01(score / 0.18);
        double bank = clamp01((score + 0.28) / 0.28);
        if (bank <= 0.0) return NONE;
        boolean frozen = lake.waterY() >= 70 && lake.z() < -1100;
        return new Sample(lake.id(), true, channel, bank, lake.waterY(),
                lake.waterY() - lake.depth(), frozen);
    }

    private static RiverSpec river(String id, List<Point> points, int startWidth, int endWidth,
                                   int startWaterY, int endWaterY, int depth, long salt,
                                   boolean tributary) {
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (Point p : points) {
            minX = Math.min(minX, p.x());
            maxX = Math.max(maxX, p.x());
            minZ = Math.min(minZ, p.z());
            maxZ = Math.max(maxZ, p.z());
        }
        return new RiverSpec(id, List.copyOf(points), startWidth, endWidth, startWaterY, endWaterY,
                depth, salt, tributary, minX, maxX, minZ, maxZ);
    }

    private static double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static double smoothStep(double t) {
        t = clamp01(t);
        return t * t * (3.0 - 2.0 * t);
    }
}
