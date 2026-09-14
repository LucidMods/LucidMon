package com.lucidmon.mapgen;

import com.lucidmon.core.Json5;
import com.lucidmon.core.LucidMon;
import com.lucidmon.core.MapGenConfigManager;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/**
 * Immutable, cached view of the detailed Kanto profile sections that are used in
 * hot world-generation paths. The cache is automatically refreshed whenever
 * MapGenConfigManager installs a new RuntimeConfig (for example after
 * /lucidmon config reload).
 */
public final class KantoLayout {
    private static volatile MapGenConfigManager.RuntimeConfig cachedFor;
    private static volatile KantoLayout cached = empty();

    public record Region(String id, int centerX, int centerZ, int radius, List<String> biomes) {}
    public record Node(String id, int x, int z) {}
    public record Route(String id, String from, String to, String travelType) {}
    public record Zone(String id, int x, int z, int radius) {}
    public record Area(String id, int x, int z, int radiusX, int radiusZ, boolean enabled) {}
    public record Volcano(int x, int z, int radiusX, int radiusZ, int peakY,
                          int craterRadius, int craterDepth, boolean lavaInCrater,
                          int townX, int townZ, int townRadius) {}

    private final Map<String, Region> regions;
    private final Map<String, Node> nodes;
    private final List<Route> routes;
    private final Map<String, Zone> zones;
    private final Map<String, Area> areas;
    private final Zone startingCity;
    private final Zone centralCity;
    private final Volcano volcano;

    private KantoLayout(Map<String, Region> regions, Map<String, Node> nodes, List<Route> routes,
                        Map<String, Zone> zones, Map<String, Area> areas,
                        Zone startingCity, Zone centralCity, Volcano volcano) {
        this.regions = Collections.unmodifiableMap(new LinkedHashMap<>(regions));
        this.nodes = Collections.unmodifiableMap(new LinkedHashMap<>(nodes));
        this.routes = List.copyOf(routes);
        this.zones = Collections.unmodifiableMap(new LinkedHashMap<>(zones));
        this.areas = Collections.unmodifiableMap(new LinkedHashMap<>(areas));
        this.startingCity = startingCity;
        this.centralCity = centralCity;
        this.volcano = volcano;
    }

    public static KantoLayout current() {
        MapGenConfigManager.RuntimeConfig runtime = MapGenConfigManager.current;
        if (cachedFor != runtime) {
            synchronized (KantoLayout.class) {
                if (cachedFor != runtime) {
                    cached = load(runtime);
                    cachedFor = runtime;
                }
            }
        }
        return cached;
    }

    @SuppressWarnings("unchecked")
    private static KantoLayout load(MapGenConfigManager.RuntimeConfig runtime) {
        try {
            String text = Files.readString(MapGenConfigManager.CONFIG_PATH, StandardCharsets.UTF_8);
            Object parsed = Json5.parse(text);
            if (!(parsed instanceof Map<?, ?> rootRaw)) throw new IllegalArgumentException("MapGen root is not an object");
            Map<String, Object> root = (Map<String, Object>) rootRaw;
            return from(map(root.get("kantoArchipelago")), runtime.kanto());
        } catch (Exception e) {
            LucidMon.warn("MapGen layout cache could not read detailed profile; using minimal runtime layout: " + e.getMessage());
            return minimal(runtime.kanto());
        }
    }

    private static KantoLayout from(Map<String, Object> k, MapGenConfigManager.KantoConfig cfg) {
        Map<String, Region> regions = new LinkedHashMap<>();
        Map<String, Object> regionRoot = map(map(k.get("biomeCoverage")).get("regions"));
        for (var entry : regionRoot.entrySet()) {
            Map<String, Object> r = map(entry.getValue());
            if (!r.containsKey("centerX") || !r.containsKey("centerZ") || !r.containsKey("radius")) continue;
            List<String> biomes = strings(r.get("biomes"));
            regions.put(entry.getKey(), new Region(entry.getKey(),
                    integer(r.get("centerX"), cfg.centerX()), integer(r.get("centerZ"), cfg.centerZ()),
                    Math.max(1, integer(r.get("radius"), 256)), biomes));
        }

        Map<String, Node> nodes = new LinkedHashMap<>();
        for (var entry : map(k.get("routeNodes")).entrySet()) {
            Map<String, Object> n = map(entry.getValue());
            nodes.put(entry.getKey(), new Node(entry.getKey(),
                    integer(n.get("x"), cfg.centerX()), integer(n.get("z"), cfg.centerZ())));
        }

        List<Route> routes = new ArrayList<>();
        for (var entry : map(map(k.get("routes")).get("network")).entrySet()) {
            Map<String, Object> route = map(entry.getValue());
            String from = string(route.get("from"), "");
            String to = string(route.get("to"), "");
            if (from.isBlank() || to.isBlank()) continue;
            routes.add(new Route(entry.getKey(), from, to, string(route.get("travelType"), "LAND").toUpperCase(Locale.ROOT)));
        }

        Map<String, Zone> zones = new LinkedHashMap<>();
        for (var entry : map(k.get("landmarkZones")).entrySet()) {
            Map<String, Object> z = map(entry.getValue());
            zones.put(entry.getKey(), new Zone(entry.getKey(),
                    integer(z.get("x"), cfg.centerX()), integer(z.get("z"), cfg.centerZ()),
                    Math.max(0, integer(z.get("flatRadius"), 0))));
        }

        Map<String, Area> areas = new LinkedHashMap<>();
        for (var entry : map(k.get("importantLocations")).entrySet()) {
            Map<String, Object> a = map(entry.getValue());
            boolean enabled = bool(a.get("enabled"), true);
            if (a.containsKey("centerX") && a.containsKey("centerZ")) {
                int common = integer(a.get("radius"), 0);
                int rx = Math.max(0, integer(a.get("radiusX"), common));
                int rz = Math.max(0, integer(a.get("radiusZ"), common));
                if (entry.getKey().equals("seafoamIslands") && rx == 0 && rz == 0) rx = rz = 360;
                areas.put(entry.getKey(), new Area(entry.getKey(),
                        integer(a.get("centerX"), cfg.centerX()), integer(a.get("centerZ"), cfg.centerZ()), rx, rz, enabled));
            } else if (a.containsKey("x") && a.containsKey("z")) {
                int radius = Math.max(0, integer(a.get("flatRadius"), 0));
                areas.put(entry.getKey(), new Area(entry.getKey(),
                        integer(a.get("x"), cfg.centerX()), integer(a.get("z"), cfg.centerZ()), radius, radius, enabled));
            }
        }

        Map<String, Object> city = map(k.get("startingCity"));
        Zone startingCity = new Zone("startingCity",
                integer(city.get("x"), cfg.startingCityX()), integer(city.get("z"), cfg.startingCityZ()),
                Math.max(0, integer(city.get("foundationRadius"), cfg.startingCityFoundationRadius())));

        // centralCity is new in layout v2. Older configs fall back to the existing
        // centralMetro landmark so the code remains safe until the config is replaced.
        Map<String, Object> central = map(k.get("centralCity"));
        Zone oldCentral = zones.get("centralMetro");
        int fallbackCentralX = oldCentral == null ? cfg.centerX() : oldCentral.x();
        int fallbackCentralZ = oldCentral == null ? cfg.centerZ() : oldCentral.z();
        int fallbackCentralRadius = oldCentral == null ? 340 : Math.max(300, oldCentral.radius());
        Zone centralCity = new Zone("centralCity",
                integer(central.get("x"), fallbackCentralX), integer(central.get("z"), fallbackCentralZ),
                Math.max(0, integer(central.get("foundationRadius"), fallbackCentralRadius)));

        Map<String, Object> v = map(k.get("volcanoIsland"));
        Map<String, Object> town = map(v.get("townSite"));
        Volcano volcano = new Volcano(
                integer(v.get("centerX"), cfg.volcanoX()), integer(v.get("centerZ"), cfg.volcanoZ()),
                Math.max(64, integer(v.get("radiusX"), cfg.volcanoRadiusX())),
                Math.max(64, integer(v.get("radiusZ"), cfg.volcanoRadiusZ())),
                integer(v.get("peakY"), cfg.volcanoPeakY()),
                Math.max(16, integer(v.get("craterRadius"), 95)),
                Math.max(8, integer(v.get("craterDepth"), 42)),
                bool(v.get("lavaInCrater"), true),
                integer(town.get("x"), cfg.volcanoX() + cfg.volcanoRadiusX() / 2),
                integer(town.get("z"), cfg.volcanoZ() + cfg.volcanoRadiusZ() / 2),
                Math.max(0, integer(town.get("radius"), 170))
        );

        return new KantoLayout(regions, nodes, routes, zones, areas, startingCity, centralCity, volcano);
    }

    private static KantoLayout minimal(MapGenConfigManager.KantoConfig cfg) {
        Map<String, Node> nodes = new LinkedHashMap<>();
        nodes.put("starting_city", new Node("starting_city", cfg.startingCityX(), cfg.startingCityZ()));
        nodes.put("mt_moon_east_entrance", new Node("mt_moon_east_entrance", cfg.mtMoonX() + 450, cfg.mtMoonZ() + 50));
        nodes.put("mt_moon_west_exit", new Node("mt_moon_west_exit", cfg.mtMoonX() - 450, cfg.mtMoonZ() - 50));
        Zone city = new Zone("startingCity", cfg.startingCityX(), cfg.startingCityZ(), cfg.startingCityFoundationRadius());
        Zone central = new Zone("centralCity", cfg.centerX(), cfg.centerZ(), 340);
        Volcano v = new Volcano(cfg.volcanoX(), cfg.volcanoZ(), cfg.volcanoRadiusX(), cfg.volcanoRadiusZ(), cfg.volcanoPeakY(), 95, 42, true,
                cfg.volcanoX() + 400, cfg.volcanoZ() + 325, 170);
        return new KantoLayout(Map.of(), nodes, List.of(), Map.of(), Map.of(), city, central, v);
    }

    private static KantoLayout empty() {
        return new KantoLayout(Map.of(), Map.of(), List.of(), Map.of(), Map.of(),
                new Zone("startingCity", -1900, 1250, 135),
                new Zone("centralCity", 0, 0, 340),
                new Volcano(-1450, 2250, 430, 380, 205, 95, 42, true, -1300, 2460, 120));
    }

    public Collection<Region> regions() { return regions.values(); }
    public Region region(String id) { return regions.get(id); }
    public Map<String, Node> nodes() { return nodes; }
    public Node node(String id) { return nodes.get(id); }
    public List<Route> routes() { return routes; }
    public Collection<Zone> zones() { return zones.values(); }
    public Zone zone(String id) { return zones.get(id); }
    public Area area(String id) { return areas.get(id); }
    public Collection<Area> areas() { return areas.values(); }
    public Zone startingCity() { return startingCity; }
    public Zone centralCity() { return centralCity; }
    public Volcano volcano() { return volcano; }

    /** Returns the strongest matching legacy configured region, if any. */
    public Region nearestRegion(int x, int z) {
        Region best = null;
        double bestD = Double.POSITIVE_INFINITY;
        for (Region r : regions.values()) {
            double dx = (x - r.centerX()) / (double) r.radius();
            double dz = (z - r.centerZ()) / (double) r.radius();
            double d = dx * dx + dz * dz;
            if (d <= 1.0 && d < bestD) { best = r; bestD = d; }
        }
        return best;
    }

    /** Stable semantic text used by the per-world profile lock. */
    public String fingerprintText(MapGenConfigManager.KantoConfig cfg) {
        StringBuilder b = new StringBuilder();
        b.append(cfg.centerX()).append(',').append(cfg.centerZ()).append(',').append(cfg.playableDiameter()).append(',')
                .append(cfg.oceanBufferBlocks()).append(',').append(cfg.outsidePlayableAreaMode()).append(',').append(cfg.outsidePlayableAreaBiome()).append('\n');
        regions.values().stream().sorted(Comparator.comparing(Region::id)).forEach(r -> b.append("R:").append(r).append('\n'));
        nodes.values().stream().sorted(Comparator.comparing(Node::id)).forEach(n -> b.append("N:").append(n).append('\n'));
        routes.stream().sorted(Comparator.comparing(Route::id)).forEach(r -> b.append("T:").append(r).append('\n'));
        zones.values().stream().sorted(Comparator.comparing(Zone::id)).forEach(z -> b.append("Z:").append(z).append('\n'));
        areas.values().stream().sorted(Comparator.comparing(Area::id)).forEach(a -> b.append("A:").append(a).append('\n'));
        b.append("C:").append(startingCity).append('\n');
        b.append("CC:").append(centralCity).append('\n');
        b.append("V:").append(volcano).append('\n');
        b.append("SHAPE:KANTO_LAYOUT_V2_POLYGON\n");
        return b.toString();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object o) { return o instanceof Map<?, ?> m ? (Map<String, Object>) m : Collections.emptyMap(); }
    private static int integer(Object o, int d) { return o instanceof Number n ? n.intValue() : d; }
    private static boolean bool(Object o, boolean d) { return o instanceof Boolean b ? b : d; }
    private static String string(Object o, String d) { return o instanceof String s ? s : d; }
    private static List<String> strings(Object o) {
        if (!(o instanceof List<?> l)) return List.of();
        ArrayList<String> out = new ArrayList<>();
        for (Object v : l) if (v instanceof String s) out.add(s.toLowerCase(Locale.ROOT));
        return List.copyOf(out);
    }
}
