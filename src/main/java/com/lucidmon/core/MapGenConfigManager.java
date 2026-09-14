package com.lucidmon.core;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Configuration and validation layer for LucidMon MapGen.
 *
 * KANTO_ARCHIPELAGO is an active runtime profile. This class validates the
 * profile before world activation; KantoMapGen separately verifies that the
 * matching world preset is actually active before generation proceeds.
 */
public final class MapGenConfigManager {
    public static final Path CONFIG_PATH = Path.of("config", "LucidMon", "MapGen", "LucidMon_mapgen.JSON5");
    private static final String DEFAULT_RESOURCE = "/assets/lucidmon/defaults/LucidMon_mapgen.JSON5";
    private static final Pattern RESOURCE_ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
    private static final Set<String> OCEAN_BIOMES = Set.of(
        "minecraft:ocean", "minecraft:deep_ocean",
        "minecraft:cold_ocean", "minecraft:deep_cold_ocean",
        "minecraft:lukewarm_ocean", "minecraft:deep_lukewarm_ocean",
        "minecraft:warm_ocean", "minecraft:frozen_ocean", "minecraft:deep_frozen_ocean"
    );

    public static volatile RuntimeConfig current = RuntimeConfig.defaults();
    public static volatile ValidationReport lastReport = new ValidationReport();

    private MapGenConfigManager() {}

    public static synchronized RuntimeConfig load() {
        ValidationReport report = new ValidationReport();
        try {
            ensureExists();
            String text = Files.readString(CONFIG_PATH, StandardCharsets.UTF_8);
            Object parsed = Json5.parse(text);
            if (!(parsed instanceof Map<?, ?> raw)) throw new IllegalArgumentException("Root must be an object");
            @SuppressWarnings("unchecked") Map<String,Object> root = (Map<String,Object>) raw;
            RuntimeConfig cfg = validate(root, report);
            current = cfg;
            lastReport = report;
            LucidMon.log("Loaded " + CONFIG_PATH + " with " + report.fallbackCount + " fallback(s), " + report.warnings.size() + " warning(s).");
            for (String w : report.warnings) LucidMon.warn("MapGen: " + w);
            return cfg;
        } catch (Exception e) {
            report.fallbackCount++;
            report.warnings.add("Could not parse/load MapGen config; using immutable STANDARD defaults for this session: " + e.getMessage());
            current = RuntimeConfig.defaults();
            lastReport = report;
            LucidMon.error("MapGen config load failed; using STANDARD safe defaults", e);
            return current;
        }
    }

    public static void ensureExists() throws IOException {
        Files.createDirectories(CONFIG_PATH.getParent());
        if (Files.exists(CONFIG_PATH)) return;
        try (InputStream in = MapGenConfigManager.class.getResourceAsStream(DEFAULT_RESOURCE)) {
            if (in == null) throw new FileNotFoundException("Bundled default MapGen config missing: " + DEFAULT_RESOURCE);
            Files.copy(in, CONFIG_PATH);
        }
        LucidMon.log("Created default MapGen config: " + CONFIG_PATH);
    }

    private static RuntimeConfig validate(Map<String,Object> root, ValidationReport r) {
        RuntimeConfig d = RuntimeConfig.defaults();
        MapType mapType = enumValue(root.get("mapType"), MapType.class, d.mapType, "mapType", r);
        boolean lock = bool(root.get("lockProfileOnWorldCreation"), d.lockProfileOnWorldCreation, "lockProfileOnWorldCreation", r);

        Map<String,Object> k = map(root.get("kantoArchipelago"));
        int centerX = integer(k.get("centerX"), d.kanto.centerX, "kantoArchipelago.centerX", r);
        int centerZ = integer(k.get("centerZ"), d.kanto.centerZ, "kantoArchipelago.centerZ", r);
        int diameter = intRange(k.get("playableDiameter"), d.kanto.playableDiameter, 1024, 30000, "kantoArchipelago.playableDiameter", r);
        boolean border = bool(k.get("setWorldBorder"), d.kanto.setWorldBorder, "kantoArchipelago.setWorldBorder", r);
        int oceanBuffer = intRange(k.get("oceanBufferBlocks"), d.kanto.oceanBufferBlocks, 0, Math.max(0, diameter/3), "kantoArchipelago.oceanBufferBlocks", r);

        Map<String,Object> outside = map(k.get("outsidePlayableArea"));
        OutsidePlayableAreaMode outsideMode = enumValue(outside.get("mode"), OutsidePlayableAreaMode.class, d.kanto.outsidePlayableAreaMode, "kantoArchipelago.outsidePlayableArea.mode", r);
        String outsideBiome = oceanBiome(outside.get("biome"), d.kanto.outsidePlayableAreaBiome, "kantoArchipelago.outsidePlayableArea.biome", r);

        Map<String,Object> coverage = map(k.get("biomeCoverage"));
        BiomePolicy biomePolicy = enumValue(coverage.get("policy"), BiomePolicy.class, d.kanto.biomePolicy, "kantoArchipelago.biomeCoverage.policy", r);
        int minSurface = intRange(coverage.get("minimumSurfaceBiomeDiameter"), d.kanto.minimumSurfaceBiomeDiameter, 32, 2048, "kantoArchipelago.biomeCoverage.minimumSurfaceBiomeDiameter", r);
        int minOcean = intRange(coverage.get("minimumOceanBiomeDiameter"), d.kanto.minimumOceanBiomeDiameter, 32, 2048, "kantoArchipelago.biomeCoverage.minimumOceanBiomeDiameter", r);
        int minCave = intRange(coverage.get("minimumCaveBiomeDiameter"), d.kanto.minimumCaveBiomeDiameter, 32, 2048, "kantoArchipelago.biomeCoverage.minimumCaveBiomeDiameter", r);
        int requiredBiomes = listSize(coverage.get("requiredBiomes"));
        if (requiredBiomes == 0) {
            fallback(r, "kantoArchipelago.biomeCoverage.requiredBiomes", "empty/missing", d.kanto.requiredBiomeCount, "Expected non-empty biome ID list");
            requiredBiomes = d.kanto.requiredBiomeCount;
        }

        Map<String,Object> city = map(k.get("startingCity"));
        boolean cityEnabled = bool(city.get("enabled"), d.kanto.startingCityEnabled, "kantoArchipelago.startingCity.enabled", r);
        String cityStructure = resource(city.get("structureId"), d.kanto.startingCityStructure, "kantoArchipelago.startingCity.structureId", r);
        int cityX = integer(city.get("x"), d.kanto.startingCityX, "kantoArchipelago.startingCity.x", r);
        int cityZ = integer(city.get("z"), d.kanto.startingCityZ, "kantoArchipelago.startingCity.z", r);
        Rotation cityRotation = enumValue(city.get("rotation"), Rotation.class, d.kanto.startingCityRotation, "kantoArchipelago.startingCity.rotation", r);
        int foundation = intRange(city.get("foundationRadius"), d.kanto.startingCityFoundationRadius, 32, 1024, "kantoArchipelago.startingCity.foundationRadius", r);
        boolean suppressCopies = bool(city.get("suppressNaturalCopies"), d.kanto.suppressNaturalCityCopies, "kantoArchipelago.startingCity.suppressNaturalCopies", r);

        Map<String,Object> volcano = map(k.get("volcanoIsland"));
        int volcanoX = integer(volcano.get("centerX"), d.kanto.volcanoX, "kantoArchipelago.volcanoIsland.centerX", r);
        int volcanoZ = integer(volcano.get("centerZ"), d.kanto.volcanoZ, "kantoArchipelago.volcanoIsland.centerZ", r);
        int volcanoRadiusX = intRange(volcano.get("radiusX"), d.kanto.volcanoRadiusX, 64, 2048, "kantoArchipelago.volcanoIsland.radiusX", r);
        int volcanoRadiusZ = intRange(volcano.get("radiusZ"), d.kanto.volcanoRadiusZ, 64, 2048, "kantoArchipelago.volcanoIsland.radiusZ", r);
        int peakY = intRange(volcano.get("peakY"), d.kanto.volcanoPeakY, 96, 319, "kantoArchipelago.volcanoIsland.peakY", r);

        Map<String,Object> routes = map(k.get("routes"));
        boolean routesEnabled = bool(routes.get("enabled"), d.kanto.routesEnabled, "kantoArchipelago.routes.enabled", r);
        RouteStyle routeStyle = enumValue(routes.get("style"), RouteStyle.class, d.kanto.routeStyle, "kantoArchipelago.routes.style", r);
        int routeWidth = intRange(routes.get("defaultWidth"), d.kanto.defaultRouteWidth, 1, 31, "kantoArchipelago.routes.defaultWidth", r);
        int cityRoadWidth = intRange(routes.get("cityApproachWidth"), d.kanto.cityApproachWidth, routeWidth, 63, "kantoArchipelago.routes.cityApproachWidth", r);

        Map<String,Object> important = map(k.get("importantLocations"));
        Map<String,Object> moon = map(important.get("mtMoon"));
        boolean mtMoonEnabled = bool(moon.get("enabled"), d.kanto.mtMoonEnabled, "kantoArchipelago.importantLocations.mtMoon.enabled", r);
        int mtMoonX = integer(moon.get("centerX"), d.kanto.mtMoonX, "kantoArchipelago.importantLocations.mtMoon.centerX", r);
        int mtMoonZ = integer(moon.get("centerZ"), d.kanto.mtMoonZ, "kantoArchipelago.importantLocations.mtMoon.centerZ", r);
        boolean mtMoonConnected = bool(map(moon.get("cave")).get("guaranteeEntranceToExitPath"), true, "kantoArchipelago.importantLocations.mtMoon.cave.guaranteeEntranceToExitPath", r);
        if (!mtMoonConnected) r.warnings.add("Mt. Moon entrance-to-exit guarantee is disabled; this can break canonical route progression.");

        Map<String,Object> verification = map(k.get("verification"));
        int declaredRequiredBiomeCount = intRange(verification.get("requiredBiomeCount"), d.kanto.requiredBiomeCount, 1, 256, "kantoArchipelago.verification.requiredBiomeCount", r);
        if (declaredRequiredBiomeCount != requiredBiomes) {
            r.warnings.add("verification.requiredBiomeCount=" + declaredRequiredBiomeCount + " but requiredBiomes currently contains " + requiredBiomes + " entries.");
        }

        validateSafeLandEnvelope(k, centerX, centerZ, diameter, oceanBuffer, r);

        KantoConfig kc = new KantoConfig(
            centerX, centerZ, diameter, border, oceanBuffer, outsideMode, outsideBiome, biomePolicy,
            minSurface, minOcean, minCave, requiredBiomes,
            cityEnabled, cityStructure, cityX, cityZ, cityRotation, foundation, suppressCopies,
            volcanoX, volcanoZ, volcanoRadiusX, volcanoRadiusZ, peakY,
            routesEnabled, routeStyle, routeWidth, cityRoadWidth,
            mtMoonEnabled, mtMoonX, mtMoonZ, mtMoonConnected
        );

        return new RuntimeConfig(mapType, lock, kc);
    }

    private static void validateSafeLandEnvelope(Map<String,Object> k,int centerX,int centerZ,int diameter,int oceanBuffer,ValidationReport r) {
        int half = diameter / 2;
        int safeHalf = Math.max(0, half - oceanBuffer);
        long minX = (long)centerX - safeHalf, maxX = (long)centerX + safeHalf;
        long minZ = (long)centerZ - safeHalf, maxZ = (long)centerZ + safeHalf;

        Map<String,Object> coverage = map(k.get("biomeCoverage"));
        Map<String,Object> regions = map(coverage.get("regions"));
        for (Map.Entry<String,Object> e : regions.entrySet()) {
            Map<String,Object> region = map(e.getValue());
            if (region.containsKey("centerX") && region.containsKey("centerZ") && region.containsKey("radius")) {
                int x = rawInt(region.get("centerX"), centerX);
                int z = rawInt(region.get("centerZ"), centerZ);
                int radius = Math.max(0, rawInt(region.get("radius"), 0));
                checkExtent("kantoArchipelago.biomeCoverage.regions."+e.getKey(), x, z, radius, radius, minX, maxX, minZ, maxZ, r);
            }
        }

        Map<String,Object> city = map(k.get("startingCity"));
        if (rawBool(city.get("enabled"), true)) {
            int radius = Math.max(0, rawInt(city.get("foundationRadius"), 0));
            checkExtent("kantoArchipelago.startingCity", rawInt(city.get("x"), centerX), rawInt(city.get("z"), centerZ), radius, radius, minX, maxX, minZ, maxZ, r);
        }

        Map<String,Object> volcano = map(k.get("volcanoIsland"));
        if (rawBool(volcano.get("enabled"), true)) {
            checkExtent("kantoArchipelago.volcanoIsland", rawInt(volcano.get("centerX"), centerX), rawInt(volcano.get("centerZ"), centerZ), Math.max(0, rawInt(volcano.get("radiusX"), 0)), Math.max(0, rawInt(volcano.get("radiusZ"), 0)), minX, maxX, minZ, maxZ, r);
        }

        Map<String,Object> important = map(k.get("importantLocations"));
        for (Map.Entry<String,Object> e : important.entrySet()) {
            Map<String,Object> loc = map(e.getValue());
            if (!rawBool(loc.get("enabled"), true)) continue;
            if (loc.containsKey("centerX") && loc.containsKey("centerZ")) {
                int rx = Math.max(0, rawInt(loc.get("radiusX"), rawInt(loc.get("radius"), 0)));
                int rz = Math.max(0, rawInt(loc.get("radiusZ"), rawInt(loc.get("radius"), 0)));
                checkExtent("kantoArchipelago.importantLocations."+e.getKey(), rawInt(loc.get("centerX"), centerX), rawInt(loc.get("centerZ"), centerZ), rx, rz, minX, maxX, minZ, maxZ, r);
            } else if (loc.containsKey("x") && loc.containsKey("z")) {
                int radius = Math.max(0, rawInt(loc.get("flatRadius"), 0));
                checkExtent("kantoArchipelago.importantLocations."+e.getKey(), rawInt(loc.get("x"), centerX), rawInt(loc.get("z"), centerZ), radius, radius, minX, maxX, minZ, maxZ, r);
            }
        }

        Map<String,Object> zones = map(k.get("landmarkZones"));
        for (Map.Entry<String,Object> e : zones.entrySet()) {
            Map<String,Object> zone = map(e.getValue());
            int radius = Math.max(0, rawInt(zone.get("flatRadius"), 0));
            checkExtent("kantoArchipelago.landmarkZones."+e.getKey(), rawInt(zone.get("x"), centerX), rawInt(zone.get("z"), centerZ), radius, radius, minX, maxX, minZ, maxZ, r);
        }

        Map<String,Object> nodes = map(k.get("routeNodes"));
        for (Map.Entry<String,Object> e : nodes.entrySet()) {
            Map<String,Object> node = map(e.getValue());
            checkExtent("kantoArchipelago.routeNodes."+e.getKey(), rawInt(node.get("x"), centerX), rawInt(node.get("z"), centerZ), 0, 0, minX, maxX, minZ, maxZ, r);
        }
    }

    private static void checkExtent(String path,int x,int z,int radiusX,int radiusZ,long minX,long maxX,long minZ,long maxZ,ValidationReport r) {
        long featureMinX=(long)x-radiusX, featureMaxX=(long)x+radiusX;
        long featureMinZ=(long)z-radiusZ, featureMaxZ=(long)z+radiusZ;
        if (featureMinX < minX || featureMaxX > maxX || featureMinZ < minZ || featureMaxZ > maxZ) {
            r.errors.add(path+" exceeds the safe land envelope. Feature extent X="+featureMinX+".."+featureMaxX+", Z="+featureMinZ+".."+featureMaxZ+"; allowed land extent X="+minX+".."+maxX+", Z="+minZ+".."+maxZ+". Move/resize the feature or adjust playableDiameter/oceanBufferBlocks.");
        }
    }

    private static int rawInt(Object o,int d){return o instanceof Number n ? n.intValue() : d;}
    private static boolean rawBool(Object o,boolean d){return o instanceof Boolean b ? b : d;}

    @SuppressWarnings("unchecked") private static Map<String,Object> map(Object o){return o instanceof Map<?,?> ? (Map<String,Object>)o : Collections.emptyMap();}
    private static int listSize(Object o){return o instanceof List<?> l ? l.size() : 0;}
    private static boolean bool(Object o,boolean d,String p,ValidationReport r){if(o==null)return d;if(o instanceof Boolean b)return b;fallback(r,p,o,d,"Expected true or false");return d;}
    private static int integer(Object o,int d,String p,ValidationReport r){if(o==null)return d;if(o instanceof Number n){long v=n.longValue();if(v>=Integer.MIN_VALUE&&v<=Integer.MAX_VALUE)return(int)v;}fallback(r,p,o,d,"Expected 32-bit integer");return d;}
    private static int intRange(Object o,int d,int min,int max,String p,ValidationReport r){int v=integer(o,d,p,r);if(v<min||v>max){fallback(r,p,v,d,"Expected value in range "+min+".."+max);return d;}return v;}
    private static String string(Object o,String d,String p,ValidationReport r){if(o==null)return d;if(o instanceof String s)return s;fallback(r,p,o,d,"Expected string");return d;}
    private static String resource(Object o,String d,String p,ValidationReport r){String s=string(o,d,p,r).toLowerCase(Locale.ROOT);if(RESOURCE_ID.matcher(s).matches())return s;fallback(r,p,o,d,"Expected namespace:path resource ID");return d;}
    private static String oceanBiome(Object o,String d,String p,ValidationReport r){String s=resource(o,d,p,r);if(OCEAN_BIOMES.contains(s))return s;fallback(r,p,o,d,"Expected a vanilla Overworld ocean biome: "+OCEAN_BIOMES);return d;}
    private static <E extends Enum<E>> E enumValue(Object o,Class<E> type,E d,String path,ValidationReport r){if(o==null)return d;if(o instanceof String s){try{return Enum.valueOf(type,s.trim().toUpperCase(Locale.ROOT));}catch(Exception ignored){}}fallback(r,path,o,d.name(),"Valid values: "+Arrays.toString(type.getEnumConstants()));return d;}
    private static void fallback(ValidationReport r,String path,Object bad,Object d,String why){r.fallbackCount++;r.warnings.add("Invalid "+path+"='"+String.valueOf(bad)+"'. "+why+". Using default: "+d+".");}

    public enum MapType { STANDARD, KANTO_ARCHIPELAGO }
    public enum OutsidePlayableAreaMode { OCEAN_ONLY }
    public enum BiomePolicy { ALL_VANILLA_OVERWORLD, REGIONAL }
    public enum RouteStyle { NATURAL, BUILT, MIXED }
    public enum Rotation { NONE, CLOCKWISE_90, CLOCKWISE_180, COUNTERCLOCKWISE_90 }

    public record KantoConfig(
        int centerX,int centerZ,int playableDiameter,boolean setWorldBorder,int oceanBufferBlocks,
        OutsidePlayableAreaMode outsidePlayableAreaMode,String outsidePlayableAreaBiome,
        BiomePolicy biomePolicy,int minimumSurfaceBiomeDiameter,int minimumOceanBiomeDiameter,int minimumCaveBiomeDiameter,int requiredBiomeCount,
        boolean startingCityEnabled,String startingCityStructure,int startingCityX,int startingCityZ,Rotation startingCityRotation,int startingCityFoundationRadius,boolean suppressNaturalCityCopies,
        int volcanoX,int volcanoZ,int volcanoRadiusX,int volcanoRadiusZ,int volcanoPeakY,
        boolean routesEnabled,RouteStyle routeStyle,int defaultRouteWidth,int cityApproachWidth,
        boolean mtMoonEnabled,int mtMoonX,int mtMoonZ,boolean mtMoonGuaranteedPath
    ) {
        public int playableHalfExtent(){ return playableDiameter/2; }
        public int safeLandHalfExtent(){ return Math.max(0, playableHalfExtent()-oceanBufferBlocks); }
    }

    public record RuntimeConfig(MapType mapType,boolean lockProfileOnWorldCreation,KantoConfig kanto) {
        public static RuntimeConfig defaults(){
            return new RuntimeConfig(MapType.STANDARD,true,new KantoConfig(
                0,0,6000,true,256,OutsidePlayableAreaMode.OCEAN_ONLY,"minecraft:deep_ocean",BiomePolicy.ALL_VANILLA_OVERWORLD,192,256,192,53,
                true,"bca:default_city_large",250,1050,Rotation.NONE,240,true,
                -1050,2200,620,500,205,
                true,RouteStyle.MIXED,5,9,
                true,-950,-950,true
            ));
        }
    }

    public static final class ValidationReport {
        public int fallbackCount=0;
        public final List<String>errors=new ArrayList<>();
        public final List<String>warnings=new ArrayList<>();
        public boolean clean(){return fallbackCount==0&&errors.isEmpty();}
    }
}
