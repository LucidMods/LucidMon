package com.lucidmon.mapgen;

import com.lucidmon.core.MapGenConfigManager;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;

import java.util.Locale;

/**
 * Macro terrain shaper layered on top of vanilla Overworld noise.
 *
 * Layout v3 keeps vanilla caves/ores/local feature generation, but replaces the
 * previous uniform ramps with multi-scale terrain undulation, irregular mountain
 * shoulders, authored rivers/lakes, organic coasts and gently meandering routes.
 */
public final class KantoTerrainShaper {
    public static final int SEA_LEVEL = 63;
    private static final ResourceLocation COAST_NOISE = ResourceLocation.fromNamespaceAndPath("lucidmon", "kanto_coast");
    private static final ResourceLocation MACRO_HEIGHT_NOISE = ResourceLocation.fromNamespaceAndPath("lucidmon", "kanto_height_macro");
    private static final ResourceLocation REGIONAL_HEIGHT_NOISE = ResourceLocation.fromNamespaceAndPath("lucidmon", "kanto_height_regional");
    private static final ResourceLocation LOCAL_HEIGHT_NOISE = ResourceLocation.fromNamespaceAndPath("lucidmon", "kanto_height_local");

    private KantoTerrainShaper() {}

    public static boolean enabled() {
        return MapGenConfigManager.current.mapType() == MapGenConfigManager.MapType.KANTO_ARCHIPELAGO;
    }

    public static boolean insidePlayable(int x, int z, MapGenConfigManager.KantoConfig cfg) {
        int half = cfg.playableHalfExtent();
        return Math.abs((long)x - cfg.centerX()) <= half && Math.abs((long)z - cfg.centerZ()) <= half;
    }

    public static boolean insideSafeLand(int x, int z, MapGenConfigManager.KantoConfig cfg) {
        int half = cfg.safeLandHalfExtent();
        return Math.abs((long)x - cfg.centerX()) <= half && Math.abs((long)z - cfg.centerZ()) <= half;
    }

    public static boolean isMacroLand(int x, int z, MapGenConfigManager.KantoConfig cfg, KantoLayout layout) {
        return insideSafeLand(x, z, cfg) && macroLandScore(x, z, cfg, layout) > 0.0;
    }

    public static double macroLandScore(int x, int z, MapGenConfigManager.KantoConfig cfg, KantoLayout layout) {
        return KantoShape.macroLandScore(x, z, cfg, layout);
    }

    public static int targetSurfaceY(int x, int z, RandomState random) {
        MapGenConfigManager.KantoConfig cfg = MapGenConfigManager.current.kanto();
        KantoLayout layout = KantoLayout.current();
        if (!insidePlayable(x, z, cfg) || !insideSafeLand(x, z, cfg)) {
            return oceanFloorY(x, z, random, -4.0);
        }

        double macro = macroLandScore(x, z, cfg, layout);
        double effective = macro + smoothNoise(random, COAST_NOISE, x, z, 190) * 0.12;
        if (effective <= 0.0) return oceanFloorY(x, z, random, effective);

        double inland = smoothStep(clamp01(effective / 1.24));
        int lowland = naturalLowlandY(x, z, random, inland);

        int mountainous = applyMountains(lowland, x, z, cfg, layout);
        double mountainBlend = smoothStep(clamp01(effective / 0.76));
        int y = (int)Math.round(lowland + (mountainous - lowland) * mountainBlend);

        y = applySettlementFlattening(y, x, z, layout);
        y = applyRouteGrading(y, x, z, cfg, layout, random);
        y = applyHydrology(y, x, z, cfg);
        return Math.max(42, Math.min(250, y));
    }

    /** Deterministic approximation used by tunnel entrances and diagnostics. */
    public static int targetSurfaceYNoSeed(int x, int z) {
        MapGenConfigManager.KantoConfig cfg = MapGenConfigManager.current.kanto();
        KantoLayout layout = KantoLayout.current();
        double macro = macroLandScore(x, z, cfg, layout);
        if (!insidePlayable(x, z, cfg) || !insideSafeLand(x, z, cfg) || macro <= 0.0) return 52;

        double inland = smoothStep(clamp01(macro / 1.24));
        double macroN = KantoShape.fixedNoise(x, z, 0x731001L, 920.0) * 9.0;
        double regionalN = KantoShape.fixedNoise(x, z, 0x731002L, 340.0) * 6.0;
        double localN = KantoShape.fixedNoise(x, z, 0x731003L, 110.0) * 2.5;
        int lowland = SEA_LEVEL + 1 + (int)Math.round(inland * 10.0 + (macroN + regionalN + localN) * inland);
        lowland = Math.max(SEA_LEVEL + 1, lowland);

        int mountainous = applyMountains(lowland, x, z, cfg, layout);
        double mountainBlend = smoothStep(clamp01(macro / 0.76));
        int y = (int)Math.round(lowland + (mountainous - lowland) * mountainBlend);
        y = applySettlementFlattening(y, x, z, layout);
        y = applyHydrology(y, x, z, cfg);
        return Math.max(42, Math.min(250, y));
    }

    /** Local water surface, including oceans, rivers and lakes. */
    public static int waterSurfaceY(int x, int z) {
        MapGenConfigManager.KantoConfig cfg = MapGenConfigManager.current.kanto();
        KantoLayout layout = KantoLayout.current();
        if (!insidePlayable(x, z, cfg) || !insideSafeLand(x, z, cfg)) return SEA_LEVEL;
        if (macroLandScore(x, z, cfg, layout) <= 0.0) return SEA_LEVEL;
        KantoHydrology.Sample hydro = KantoHydrology.sample(x, z, cfg);
        return hydro.hasWater() ? hydro.waterY() : Integer.MIN_VALUE;
    }

    public static int baseHeight(int x, int z, Heightmap.Types type, RandomState random) {
        int surface = targetSurfaceY(x, z, random);
        int water = waterSurfaceY(x, z);
        if (water > surface) return isOceanFloor(type) ? surface + 1 : water + 1;
        return surface + 1;
    }

    public static NoiseColumn baseColumn(int x, int z, int minY, int maxY, RandomState random) {
        int surface = targetSurfaceY(x, z, random);
        int water = waterSurfaceY(x, z);
        BlockState[] states = new BlockState[Math.max(0, maxY - minY)];
        for (int y = minY; y < maxY; y++) {
            BlockState state;
            if (y <= surface) state = Blocks.STONE.defaultBlockState();
            else if (water > surface && y <= water) state = Blocks.WATER.defaultBlockState();
            else state = Blocks.AIR.defaultBlockState();
            states[y - minY] = state;
        }
        return new NoiseColumn(minY, states);
    }

    public static void shapeChunk(ChunkAccess chunk, RandomState random) {
        if (!enabled()) return;
        int minY = chunk.getMinBuildHeight();
        int maxY = chunk.getMaxBuildHeight() - 1;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int baseX = chunk.getPos().getMinBlockX();
        int baseZ = chunk.getPos().getMinBlockZ();

        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int x = baseX + lx, z = baseZ + lz;
                int target = Math.max(minY + 5, Math.min(maxY - 2, targetSurfaceY(x, z, random)));
                int water = Math.min(maxY - 1, waterSurfaceY(x, z));
                int current = chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, lx, lz);

                // Remove everything above the desired land/bed surface. Water is
                // filled back afterward, allowing rivers/lakes above sea level.
                if (current > target) {
                    for (int y = current; y > target; y--) chunk.setBlockState(pos.set(x, y, z), Blocks.AIR.defaultBlockState(), false);
                } else if (current < target) {
                    for (int y = Math.max(minY, current); y <= target; y++) chunk.setBlockState(pos.set(x, y, z), Blocks.STONE.defaultBlockState(), false);
                }
                chunk.setBlockState(pos.set(x, target, z), Blocks.STONE.defaultBlockState(), false);

                if (water > target) {
                    for (int y = target + 1; y <= water; y++) chunk.setBlockState(pos.set(x, y, z), Blocks.WATER.defaultBlockState(), false);
                }

                applyVolcanoLava(chunk, pos, x, z, target);
            }
        }
    }

    public static void paintRoutes(ChunkAccess chunk) {
        if (!enabled() || !MapGenConfigManager.current.kanto().routesEnabled()) return;
        KantoLayout layout = KantoLayout.current();
        MapGenConfigManager.KantoConfig cfg = MapGenConfigManager.current.kanto();
        int baseX = chunk.getPos().getMinBlockX();
        int baseZ = chunk.getPos().getMinBlockZ();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        double halfWidth = Math.max(1.0, cfg.defaultRouteWidth() / 2.0 + 1.0);

        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int x = baseX + lx, z = baseZ + lz;
                if (distanceToNearestLandRoute(x, z, layout) > halfWidth) continue;
                if (KantoHydrology.sample(x, z, cfg).hasWater()) continue;
                int top = chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, lx, lz);
                if (top <= SEA_LEVEL) continue;
                BlockState surface = cfg.routeStyle() == MapGenConfigManager.RouteStyle.BUILT
                        ? Blocks.STONE_BRICKS.defaultBlockState() : Blocks.GRAVEL.defaultBlockState();
                chunk.setBlockState(pos.set(x, top, z), surface, false);
            }
        }
    }

    public static void carveCanonicalTunnels(ChunkAccess chunk) {
        if (!enabled()) return;
        KantoLayout layout = KantoLayout.current();
        carve(layout, chunk, "mt_moon_east_entrance", "mt_moon_west_exit", 84);
        carve(layout, chunk, "rock_tunnel_west", "rock_tunnel_east", 80);
        carve(layout, chunk, "victory_road_south_entrance", "victory_road_north_exit", 90);
    }

    private static void carve(KantoLayout layout, ChunkAccess chunk, String aId, String bId, int preferredY) {
        KantoLayout.Node a = layout.node(aId), b = layout.node(bId);
        if (a == null || b == null) return;
        int baseX = chunk.getPos().getMinBlockX(), baseZ = chunk.getPos().getMinBlockZ();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        double vx = b.x() - a.x(), vz = b.z() - a.z();
        double len2 = vx * vx + vz * vz;
        if (len2 < 1.0) return;

        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int x = baseX + lx, z = baseZ + lz;
                double t = ((x - a.x()) * vx + (z - a.z()) * vz) / len2;
                if (t < -0.04 || t > 1.04) continue;
                double tc = clamp01(t);
                double px = a.x() + tc * vx, pz = a.z() + tc * vz;
                if (Math.hypot(x - px, z - pz) > 3.25) continue;

                int entranceA = Math.max(preferredY, targetSurfaceYNoSeed(a.x(), a.z()) - 3);
                int entranceB = Math.max(preferredY, targetSurfaceYNoSeed(b.x(), b.z()) - 3);
                double edgeBlend = Math.min(1.0, Math.min(tc, 1.0 - tc) / 0.12);
                int interpolatedEntrance = (int)Math.round(entranceA + (entranceB - entranceA) * tc);
                int y = (int)Math.round(interpolatedEntrance * (1.0 - edgeBlend) + preferredY * edgeBlend);
                for (int yy = y - 2; yy <= y + 3; yy++) chunk.setBlockState(pos.set(x, yy, z), Blocks.AIR.defaultBlockState(), false);
            }
        }
    }

    private static int naturalLowlandY(int x, int z, RandomState random, double inland) {
        double macro = smoothNoise(random, MACRO_HEIGHT_NOISE, x, z, 920) * 9.0;
        double regional = smoothNoise(random, REGIONAL_HEIGHT_NOISE, x, z, 335) * 6.0;
        double local = smoothNoise(random, LOCAL_HEIGHT_NOISE, x, z, 105) * 2.8;
        int y = SEA_LEVEL + 1 + (int)Math.round(inland * 10.0 + (macro + regional + local) * inland);
        return Math.max(SEA_LEVEL + 1, y);
    }

    private static int applyHydrology(int y, int x, int z, MapGenConfigManager.KantoConfig cfg) {
        KantoHydrology.Sample hydro = KantoHydrology.sample(x, z, cfg);
        if (!hydro.affectsTerrain()) return y;

        if (hydro.hasWater()) {
            // Keep at least the authored depth while allowing naturally lower
            // ground to remain lower instead of building artificial dams.
            return Math.min(y, hydro.bedY());
        }

        // Outside open water, ease banks toward the waterline rather than
        // slicing vertical trenches into otherwise smooth terrain.
        double blend = smoothStep(clamp01(hydro.bank())) * 0.70;
        int bankTarget = hydro.waterY() + 2;
        return Math.min(y, (int)Math.round(y * (1.0 - blend) + bankTarget * blend));
    }

    private static int applyMountains(int base, int x, int z, MapGenConfigManager.KantoConfig cfg, KantoLayout layout) {
        int y = base;
        KantoLayout.Region snow = layout.region("northSnowCrown");
        if (snow != null) y = Math.max(y, mountainHeight(x, z, snow.centerX(), snow.centerZ(), snow.radius(), snow.radius(), 176, base));
        KantoLayout.Region nw = layout.region("northwestHighlands");
        if (nw != null) y = Math.max(y, mountainHeight(x, z, nw.centerX(), nw.centerZ(), nw.radius(), nw.radius(), 148, base));
        KantoLayout.Zone ne = layout.zone("northeastHighlands");
        if (ne != null) y = Math.max(y, mountainHeight(x, z, ne.x(), ne.z(), 520, 480, 150, base));

        KantoLayout.Area moon = layout.area("mtMoon");
        if (moon != null && moon.enabled()) {
            y = Math.max(y, mountainHeight(x, z, moon.x(), moon.z(), Math.max(1, moon.radiusX()), Math.max(1, moon.radiusZ()), 190, base));
        } else {
            y = Math.max(y, mountainHeight(x, z, cfg.mtMoonX(), cfg.mtMoonZ(), 520, 460, 190, base));
        }

        KantoLayout.Area rock = layout.area("rockTunnel");
        if (rock != null && rock.enabled()) y = Math.max(y, mountainHeight(x, z, rock.x(), rock.z(), Math.max(1, rock.radiusX()), Math.max(1, rock.radiusZ()), 152, base));
        KantoLayout.Area victory = layout.area("victoryRoad");
        if (victory != null && victory.enabled()) y = Math.max(y, mountainHeight(x, z, victory.x(), victory.z(), Math.max(1, victory.radiusX()), Math.max(1, victory.radiusZ()), 195, base));

        KantoLayout.Volcano v = layout.volcano();
        double nd = ellipseDistance(x, z, v.x(), v.z(), v.radiusX(), v.radiusZ());
        if (nd < 1.0) {
            int cone = 68 + (int)Math.round((v.peakY() - 68) * Math.pow(1.0 - nd, 1.48));
            double craterDist = Math.hypot(x - v.x(), z - v.z());
            if (craterDist < v.craterRadius()) {
                double rim = craterDist / Math.max(1.0, v.craterRadius());
                int floor = v.peakY() - v.craterDepth();
                cone = Math.min(cone, floor + (int)Math.round(rim * Math.min(18, v.craterDepth() / 2.0)));
            }
            y = Math.max(y, cone);
        }
        return y;
    }

    private static int applySettlementFlattening(int y, int x, int z, KantoLayout layout) {
        y = flatten(y, x, z, layout.startingCity(), 72);
        y = flatten(y, x, z, layout.centralCity(), 74);

        for (KantoLayout.Zone zone : layout.zones()) {
            if (zone.id().equalsIgnoreCase("centralMetro")) continue;
            int target = switch (zone.id().toLowerCase(Locale.ROOT)) {
                case "leagueplateau" -> 128;
                case "northwesthighlands", "northeasthighlands" -> 96;
                default -> 74;
            };
            y = flatten(y, x, z, zone, target);
        }
        KantoLayout.Volcano v = layout.volcano();
        y = flatten(y, x, z, new KantoLayout.Zone("volcanoTown", v.townX(), v.townZ(), v.townRadius()), 72);
        return y;
    }

    private static int flatten(int y, int x, int z, KantoLayout.Zone zone, int target) {
        if (zone == null || zone.radius() <= 0) return y;
        double d = Math.hypot(x - zone.x(), z - zone.z()) / zone.radius();
        if (d >= 1.0) return y;
        // Fully flat only near the build center; outer 45% eases smoothly back
        // to native terrain so settlement pads do not read as circular plates.
        double blend = d <= 0.55 ? 1.0 : smoothStep(clamp01((1.0 - d) / 0.45));
        return (int)Math.round(y * (1.0 - blend) + target * blend);
    }

    private static int applyRouteGrading(int y, int x, int z, MapGenConfigManager.KantoConfig cfg, KantoLayout layout, RandomState random) {
        if (!cfg.routesEnabled()) return y;
        double best = Double.POSITIVE_INFINITY;
        KantoLayout.Route bestRoute = null;
        RouteProjection bestProjection = null;
        for (KantoLayout.Route route : layout.routes()) {
            if (route.travelType().equals("SEA")) continue;
            KantoLayout.Node a = layout.node(route.from()), b = layout.node(route.to());
            if (a == null || b == null) continue;
            RouteProjection p = projectRoute(x, z, a, b, route.id());
            if (p.distance() < best) { best = p.distance(); bestRoute = route; bestProjection = p; }
        }
        if (bestRoute == null || bestProjection == null || best > Math.max(14.0, cfg.defaultRouteWidth() * 2.5)) return y;
        KantoLayout.Node a = layout.node(bestRoute.from()), b = layout.node(bestRoute.to());
        double t = bestProjection.t();
        int ya = targetSurfaceWithoutRoutes(a.x(), a.z(), cfg, layout, random);
        int yb = targetSurfaceWithoutRoutes(b.x(), b.z(), cfg, layout, random);
        int grade = (int)Math.round(ya + (yb - ya) * t);
        double blend = Math.max(0.0, 1.0 - best / Math.max(14.0, cfg.defaultRouteWidth() * 2.5));
        return (int)Math.round(y * (1.0 - blend * 0.60) + grade * (blend * 0.60));
    }

    private static int targetSurfaceWithoutRoutes(int x, int z, MapGenConfigManager.KantoConfig cfg, KantoLayout layout, RandomState random) {
        double macro = macroLandScore(x, z, cfg, layout);
        if (!insideSafeLand(x, z, cfg) || macro <= 0.0) return SEA_LEVEL;
        double inland = smoothStep(clamp01(macro / 1.24));
        int lowland = naturalLowlandY(x, z, random, inland);
        int mountain = applyMountains(lowland, x, z, cfg, layout);
        double mountainBlend = smoothStep(clamp01(macro / 0.76));
        return applySettlementFlattening((int)Math.round(lowland + (mountain - lowland) * mountainBlend), x, z, layout);
    }

    private static void applyVolcanoLava(ChunkAccess chunk, BlockPos.MutableBlockPos pos, int x, int z, int surface) {
        KantoLayout.Volcano v = KantoLayout.current().volcano();
        if (!v.lavaInCrater()) return;
        double d = Math.hypot(x - v.x(), z - v.z());
        if (d > v.craterRadius() * 0.42) return;
        int lavaY = Math.min(v.peakY() - v.craterDepth() + 3, surface + 1);
        if (lavaY > surface && lavaY < chunk.getMaxBuildHeight()) chunk.setBlockState(pos.set(x, lavaY, z), Blocks.LAVA.defaultBlockState(), false);
    }

    private static int oceanFloorY(int x, int z, RandomState random, double signedLandScore) {
        double deep = smoothStep(clamp01((-signedLandScore) / 2.1));
        int base = 59 - (int)Math.round(deep * 14.0);
        int detail = (int)Math.round(smoothNoise(random, REGIONAL_HEIGHT_NOISE, x, z, 150) * (1.5 + deep * 2.0));
        return Math.max(42, Math.min(60, base + detail));
    }

    private static double smoothNoise(RandomState random, ResourceLocation salt, int x, int z, int scale) {
        if (random == null) return 0.0;
        int gx = Math.floorDiv(x, scale), gz = Math.floorDiv(z, scale);
        double fx = Math.floorMod(x, scale) / (double)scale, fz = Math.floorMod(z, scale) / (double)scale;
        fx = fx * fx * (3.0 - 2.0 * fx);
        fz = fz * fz * (3.0 - 2.0 * fz);
        double n00 = cellNoise(random, salt, gx, gz), n10 = cellNoise(random, salt, gx + 1, gz);
        double n01 = cellNoise(random, salt, gx, gz + 1), n11 = cellNoise(random, salt, gx + 1, gz + 1);
        double nx0 = n00 + (n10 - n00) * fx, nx1 = n01 + (n11 - n01) * fx;
        return nx0 + (nx1 - nx0) * fz;
    }

    private static double cellNoise(RandomState random, ResourceLocation salt, int gx, int gz) {
        return random.getOrCreateRandomFactory(salt).at(gx, 0, gz).nextDouble() * 2.0 - 1.0;
    }

    private static int mountainHeight(int x, int z, int cx, int cz, int rx, int rz, int peak, int base) {
        long salt = 0x4D4F554EL ^ (((long)cx) << 32) ^ (cz & 0xffffffffL) ^ ((long)rx << 12) ^ rz;
        double wx = x + KantoShape.fixedNoise(x, z, salt ^ 0x11L, 430.0) * rx * 0.13;
        double wz = z + KantoShape.fixedNoise(x, z, salt ^ 0x22L, 410.0) * rz * 0.13;
        double d = ellipseDistance(wx, wz, cx, cz, rx, rz);
        if (d >= 1.0) return base;
        double strength = Math.pow(1.0 - d, 1.55);
        double ridge = 0.82 + (KantoShape.fixedNoise(x, z, salt ^ 0x33L, 180.0) + 1.0) * 0.09;
        return base + (int)Math.round((peak - base) * strength * ridge);
    }

    private static double ellipseDistance(double x, double z, int cx, int cz, int rx, int rz) {
        double dx = (x - cx) / (double)Math.max(1, rx), dz = (z - cz) / (double)Math.max(1, rz);
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static double distanceToNearestLandRoute(int x, int z, KantoLayout layout) {
        double best = Double.POSITIVE_INFINITY;
        for (KantoLayout.Route route : layout.routes()) {
            if (route.travelType().equals("SEA")) continue;
            KantoLayout.Node a = layout.node(route.from()), b = layout.node(route.to());
            if (a == null || b == null) continue;
            best = Math.min(best, projectRoute(x, z, a, b, route.id()).distance());
        }
        return best;
    }

    private record RouteProjection(double distance, double t) {}

    private static RouteProjection projectRoute(int x, int z, KantoLayout.Node a, KantoLayout.Node b, String id) {
        long salt = stableSalt(id);
        double wx = x + KantoShape.fixedNoise(x, z, salt ^ 0x71L, 360.0) * 52.0
                + KantoShape.fixedNoise(x, z, salt ^ 0x73L, 135.0) * 11.0;
        double wz = z + KantoShape.fixedNoise(x, z, salt ^ 0x79L, 340.0) * 52.0
                + KantoShape.fixedNoise(x, z, salt ^ 0x7BL, 128.0) * 11.0;
        double vx = b.x() - a.x(), vz = b.z() - a.z();
        double len2 = Math.max(1.0, vx * vx + vz * vz);
        double t = clamp01(((wx - a.x()) * vx + (wz - a.z()) * vz) / len2);
        double px = a.x() + t * vx, pz = a.z() + t * vz;
        return new RouteProjection(Math.hypot(wx - px, wz - pz), t);
    }

    private static long stableSalt(String id) {
        long h = 0xcbf29ce484222325L;
        for (int i = 0; i < id.length(); i++) {
            h ^= id.charAt(i);
            h *= 0x100000001b3L;
        }
        return h;
    }

    private static double clamp01(double v) { return Math.max(0.0, Math.min(1.0, v)); }
    private static double smoothStep(double v) { return v * v * (3.0 - 2.0 * v); }

    private static boolean isOceanFloor(Heightmap.Types type) {
        return type == Heightmap.Types.OCEAN_FLOOR || type == Heightmap.Types.OCEAN_FLOOR_WG;
    }
}
