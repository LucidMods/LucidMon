package com.lucidmon.core;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Configuration/preflight layer for LucidMon MapGen.
 *
 * unstable.2 deliberately separates profile configuration from terrain generation.
 * The config is created and validated now so server owners can finalize profile values
 * before a later build enables the custom Kanto chunk generator.
 */
public final class MapGenConfigManager {
    public static final Path CONFIG_PATH = Path.of("config", "LucidMon", "MapGen", "LucidMon_mapgen.JSON5");
    private static final String DEFAULT_RESOURCE = "/assets/lucidmon/defaults/LucidMon_mapgen.JSON5";
    private static final Pattern RESOURCE_ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

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

        KantoConfig kc = new KantoConfig(
            centerX, centerZ, diameter, border, oceanBuffer, biomePolicy,
            minSurface, minOcean, minCave, requiredBiomes,
            cityEnabled, cityStructure, cityX, cityZ, cityRotation, foundation, suppressCopies,
            volcanoX, volcanoZ, peakY,
            routesEnabled, routeStyle, routeWidth, cityRoadWidth,
            mtMoonEnabled, mtMoonX, mtMoonZ, mtMoonConnected
        );

        // This is intentionally explicit for unstable.2. The config/profile is now part of
        // LucidMon, but the absolute-coordinate custom chunk generator is not enabled yet.
        if (mapType == MapType.KANTO_ARCHIPELAGO) {
            r.warnings.add("KANTO_ARCHIPELAGO is configured, but unstable.2 is a MapGen PRE-FLIGHT build: do not create the final world yet. The absolute-coordinate terrain generator is not active in this build.");
        }

        return new RuntimeConfig(mapType, lock, kc);
    }

    @SuppressWarnings("unchecked") private static Map<String,Object> map(Object o){return o instanceof Map<?,?> ? (Map<String,Object>)o : Collections.emptyMap();}
    private static int listSize(Object o){return o instanceof List<?> l ? l.size() : 0;}
    private static boolean bool(Object o,boolean d,String p,ValidationReport r){if(o==null)return d;if(o instanceof Boolean b)return b;fallback(r,p,o,d,"Expected true or false");return d;}
    private static int integer(Object o,int d,String p,ValidationReport r){if(o==null)return d;if(o instanceof Number n){long v=n.longValue();if(v>=Integer.MIN_VALUE&&v<=Integer.MAX_VALUE)return(int)v;}fallback(r,p,o,d,"Expected 32-bit integer");return d;}
    private static int intRange(Object o,int d,int min,int max,String p,ValidationReport r){int v=integer(o,d,p,r);if(v<min||v>max){fallback(r,p,v,d,"Expected value in range "+min+".."+max);return d;}return v;}
    private static String string(Object o,String d,String p,ValidationReport r){if(o==null)return d;if(o instanceof String s)return s;fallback(r,p,o,d,"Expected string");return d;}
    private static String resource(Object o,String d,String p,ValidationReport r){String s=string(o,d,p,r).toLowerCase(Locale.ROOT);if(RESOURCE_ID.matcher(s).matches())return s;fallback(r,p,o,d,"Expected namespace:path resource ID");return d;}
    private static <E extends Enum<E>> E enumValue(Object o,Class<E> type,E d,String path,ValidationReport r){if(o==null)return d;if(o instanceof String s){try{return Enum.valueOf(type,s.trim().toUpperCase(Locale.ROOT));}catch(Exception ignored){}}fallback(r,path,o,d.name(),"Valid values: "+Arrays.toString(type.getEnumConstants()));return d;}
    private static void fallback(ValidationReport r,String path,Object bad,Object d,String why){r.fallbackCount++;r.warnings.add("Invalid "+path+"='"+String.valueOf(bad)+"'. "+why+". Using default: "+d+".");}

    public enum MapType { STANDARD, KANTO_ARCHIPELAGO }
    public enum BiomePolicy { ALL_VANILLA_OVERWORLD, REGIONAL }
    public enum RouteStyle { NATURAL, BUILT, MIXED }
    public enum Rotation { NONE, CLOCKWISE_90, CLOCKWISE_180, COUNTERCLOCKWISE_90 }

    public record KantoConfig(
        int centerX,int centerZ,int playableDiameter,boolean setWorldBorder,int oceanBufferBlocks,
        BiomePolicy biomePolicy,int minimumSurfaceBiomeDiameter,int minimumOceanBiomeDiameter,int minimumCaveBiomeDiameter,int requiredBiomeCount,
        boolean startingCityEnabled,String startingCityStructure,int startingCityX,int startingCityZ,Rotation startingCityRotation,int startingCityFoundationRadius,boolean suppressNaturalCityCopies,
        int volcanoX,int volcanoZ,int volcanoPeakY,
        boolean routesEnabled,RouteStyle routeStyle,int defaultRouteWidth,int cityApproachWidth,
        boolean mtMoonEnabled,int mtMoonX,int mtMoonZ,boolean mtMoonGuaranteedPath
    ) {}

    public record RuntimeConfig(MapType mapType,boolean lockProfileOnWorldCreation,KantoConfig kanto) {
        public static RuntimeConfig defaults(){
            return new RuntimeConfig(MapType.STANDARD,true,new KantoConfig(
                0,0,6000,true,256,BiomePolicy.ALL_VANILLA_OVERWORLD,192,256,192,53,
                true,"bca:default_city_large",250,1050,Rotation.NONE,240,true,
                -1050,2350,205,
                true,RouteStyle.MIXED,5,9,
                true,-950,-950,true
            ));
        }
    }

    public static final class ValidationReport {
        public int fallbackCount=0;
        public final List<String>warnings=new ArrayList<>();
        public boolean clean(){return fallbackCount==0;}
    }
}
