package com.lucidmon.core;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;

public final class ConfigManager {
    public static final Path CONFIG_PATH = Path.of("config", "LucidMon", "LucidMon_core.JSON5");
    private static final String DEFAULT_RESOURCE = "/assets/lucidmon/defaults/LucidMon_core.JSON5";
    private static final Pattern RESOURCE_ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
    private static final Pattern PLAYER_NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");

    public static volatile RuntimeConfig current = RuntimeConfig.defaults();
    public static volatile ValidationReport lastReport = new ValidationReport();

    private ConfigManager() {}

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
            for (String w : report.warnings) LucidMon.warn(w);
            return cfg;
        } catch (Exception e) {
            report.fallbackCount++;
            report.warnings.add("Could not parse/load config; using immutable defaults for this session: " + e.getMessage());
            current = RuntimeConfig.defaults();
            lastReport = report;
            LucidMon.error("Config load failed; using safe defaults", e);
            return current;
        }
    }

    public static void ensureExists() throws IOException {
        Files.createDirectories(CONFIG_PATH.getParent());
        if (Files.exists(CONFIG_PATH)) return;
        try (InputStream in = ConfigManager.class.getResourceAsStream(DEFAULT_RESOURCE)) {
            if (in == null) throw new FileNotFoundException("Bundled default config missing: " + DEFAULT_RESOURCE);
            Files.copy(in, CONFIG_PATH);
        }
        LucidMon.log("Created default config: " + CONFIG_PATH);
    }

    private static RuntimeConfig validate(Map<String,Object> root, ValidationReport r) {
        RuntimeConfig def = RuntimeConfig.defaults();

        Map<String,Object> play = map(root.get("playArea"));
        PlayAreaConfig playArea = new PlayAreaConfig(
            resource(play.get("dimension"), def.playArea.dimension, "playArea.dimension", r),
            integer(play.get("centerX"), def.playArea.centerX, "playArea.centerX", r),
            integer(play.get("centerZ"), def.playArea.centerZ, "playArea.centerZ", r),
            intMin(play.get("radius"), def.playArea.radius, 256, "playArea.radius", r)
        );

        Map<String,Object> gymRoot = map(root.get("gyms"));
        boolean suppressNaturalWorldgen = bool(gymRoot.get("suppressNaturalWorldgen"), true, "gyms.suppressNaturalWorldgen", r);
        boolean autoPlace = bool(gymRoot.get("autoPlaceOnServerStart"), false, "gyms.autoPlaceOnServerStart", r);
        LinkedHashMap<String,GymConfig> gyms = new LinkedHashMap<>();
        for (Map.Entry<String,GymConfig> e : def.gyms.entrySet()) {
            String id = e.getKey(); GymConfig gd = e.getValue();
            Map<String,Object> g = map(gymRoot.get(id));
            gyms.put(id, new GymConfig(
                bool(g.get("enabled"), gd.enabled, "gyms."+id+".enabled", r),
                resource(g.get("structure"), gd.structure, "gyms."+id+".structure", r),
                resource(g.get("dimension"), gd.dimension, "gyms."+id+".dimension", r),
                integer(g.get("x"), gd.x, "gyms."+id+".x", r),
                integer(g.get("y"), gd.y, "gyms."+id+".y", r),
                integer(g.get("z"), gd.z, "gyms."+id+".z", r),
                enumValue(g.get("rotation"), Rotation.class, gd.rotation, "gyms."+id+".rotation", r)
            ));
        }

        Map<String,Object> champion = map(root.get("champion"));
        ChampionType type = enumValue(champion.get("type"), ChampionType.class, ChampionType.AI, "champion.type", r);
        String playerName = string(champion.get("playerName"), "", "champion.playerName", r);
        if (!playerName.isBlank() && !PLAYER_NAME.matcher(playerName).matches()) {
            fallback(r, "champion.playerName", playerName, "blank", "Expected a Minecraft username (1-16 letters/numbers/underscore)");
            playerName = "";
        }

        Map<String,Object> arena = map(champion.get("arena"));
        String arenaDim = resource(arena.get("dimension"), "minecraft:overworld", "champion.arena.dimension", r);
        ArenaPoint challenger = arenaPoint(map(arena.get("challenger")), new ArenaPoint(0,80,0,0f,0f), "champion.arena.challenger", r);
        ArenaPoint championPoint = arenaPoint(map(arena.get("champion")), new ArenaPoint(8,80,0,180f,0f), "champion.arena.champion", r);

        ChampionConfig champCfg = new ChampionConfig(
            type,
            playerName,
            new ArenaConfig(arenaDim, challenger, championPoint),
            enumValue(champion.get("battleFormat"), BattleFormat.class, BattleFormat.DOUBLES, "champion.battleFormat", r),
            longMin(champion.get("retryCooldownSeconds"), 3600L, 0L, "champion.retryCooldownSeconds", r),
            bool(champion.get("requireReadyConfirmation"), true, "champion.requireReadyConfirmation", r),
            enumValue(champion.get("challengerDisconnectBehavior"), ChallengerDisconnect.class, ChallengerDisconnect.FORFEIT, "champion.challengerDisconnectBehavior", r),
            enumValue(champion.get("championDisconnectBehavior"), ChampionDisconnect.class, ChampionDisconnect.REQUEUE, "champion.championDisconnectBehavior", r)
        );

        Map<String,Object> qual = map(root.get("qualification"));
        QualificationConfig q = new QualificationConfig(
            intMin(qual.get("tournamentSlots"), 15, 1, "qualification.tournamentSlots", r),
            bool(qual.get("preserveVoucherClaimOrder"), true, "qualification.preserveVoucherClaimOrder", r)
        );

        Map<String,Object> voucher = map(root.get("voucher"));
        VoucherConfig v = new VoucherConfig(
            nonEmptyString(voucher.get("displayName"), "Champion Challenge Voucher", "voucher.displayName", r),
            bool(voucher.get("grantAfterEliteFour"), true, "voucher.grantAfterEliteFour", r),
            bool(voucher.get("oneOpenClaimPerPlayer"), true, "voucher.oneOpenClaimPerPlayer", r)
        );

        if (type == ChampionType.PLAYER && playerName.isBlank()) {
            r.warnings.add("champion.type is PLAYER but champion.playerName is blank. Player Champion matchmaking will remain unavailable until configured.");
        }
        if (allSamePlaceholder(gyms)) {
            r.warnings.add("All enabled gym coordinates are still at the default placeholder (0,80,0). Keep autoPlaceOnServerStart=false until you configure them.");
        }

        return new RuntimeConfig(playArea, suppressNaturalWorldgen, autoPlace, gyms, champCfg, q, v);
    }

    private static boolean allSamePlaceholder(Map<String,GymConfig> gyms) {
        for (GymConfig g : gyms.values()) if (g.enabled && (g.x != 0 || g.y != 80 || g.z != 0)) return false;
        return true;
    }

    private static ArenaPoint arenaPoint(Map<String,Object> m, ArenaPoint d, String path, ValidationReport r) {
        return new ArenaPoint(
            integer(m.get("x"), d.x, path+".x", r), integer(m.get("y"), d.y, path+".y", r), integer(m.get("z"), d.z, path+".z", r),
            floating(m.get("yaw"), d.yaw, path+".yaw", r), floating(m.get("pitch"), d.pitch, path+".pitch", r)
        );
    }

    @SuppressWarnings("unchecked") private static Map<String,Object> map(Object o) { return o instanceof Map<?,?> ? (Map<String,Object>)o : Collections.emptyMap(); }
    private static String string(Object o,String d,String p,ValidationReport r){ if(o==null)return d; if(o instanceof String s)return s; fallback(r,p,o,d,"Expected string"); return d; }
    private static String nonEmptyString(Object o,String d,String p,ValidationReport r){ String s=string(o,d,p,r); if(s.isBlank()){fallback(r,p,s,d,"Must not be blank");return d;}return s; }
    private static String resource(Object o,String d,String p,ValidationReport r){ String s=string(o,d,p,r).toLowerCase(Locale.ROOT); if(RESOURCE_ID.matcher(s).matches())return s; fallback(r,p,o,d,"Expected namespace:path resource ID");return d; }
    private static boolean bool(Object o,boolean d,String p,ValidationReport r){ if(o==null)return d; if(o instanceof Boolean b)return b; fallback(r,p,o,d,"Expected true or false");return d; }
    private static int integer(Object o,int d,String p,ValidationReport r){ if(o==null)return d; if(o instanceof Number n){long v=n.longValue(); if(v>=Integer.MIN_VALUE&&v<=Integer.MAX_VALUE)return(int)v;} fallback(r,p,o,d,"Expected 32-bit integer");return d; }
    private static int intMin(Object o,int d,int min,String p,ValidationReport r){ int v=integer(o,d,p,r); if(v<min){fallback(r,p,v,d,"Must be >= "+min);return d;}return v; }
    private static long longMin(Object o,long d,long min,String p,ValidationReport r){ if(o==null)return d; if(o instanceof Number n){long v=n.longValue(); if(v>=min)return v;} fallback(r,p,o,d,"Expected integer >= "+min);return d; }
    private static float floating(Object o,float d,String p,ValidationReport r){ if(o==null)return d; if(o instanceof Number n){double v=n.doubleValue(); if(Double.isFinite(v)&&v>=-360000&&v<=360000)return(float)v;} fallback(r,p,o,d,"Expected finite number");return d; }

    private static <E extends Enum<E>> E enumValue(Object o,Class<E> type,E d,String path,ValidationReport r){
        if(o==null)return d;
        if(o instanceof String s){try{return Enum.valueOf(type,s.trim().toUpperCase(Locale.ROOT));}catch(Exception ignored){}}
        fallback(r,path,o,d.name(),"Valid values: "+Arrays.toString(type.getEnumConstants())); return d;
    }
    private static void fallback(ValidationReport r,String path,Object bad,Object d,String why){r.fallbackCount++;r.warnings.add("Invalid "+path+"='"+String.valueOf(bad)+"'. "+why+". Using default: "+d+".");}

    public enum Rotation { NONE, CLOCKWISE_90, CLOCKWISE_180, COUNTERCLOCKWISE_90 }
    public enum ChampionType { AI, PLAYER }
    public enum BattleFormat { SINGLES, DOUBLES }
    public enum ChallengerDisconnect { FORFEIT, REQUEUE }
    public enum ChampionDisconnect { REQUEUE, CHALLENGER_WIN }

    public record PlayAreaConfig(String dimension,int centerX,int centerZ,int radius) {}
    public record GymConfig(boolean enabled,String structure,String dimension,int x,int y,int z,Rotation rotation) {}
    public record ArenaPoint(int x,int y,int z,float yaw,float pitch) {}
    public record ArenaConfig(String dimension,ArenaPoint challenger,ArenaPoint champion) {}
    public record ChampionConfig(ChampionType type,String playerName,ArenaConfig arena,BattleFormat battleFormat,long retryCooldownSeconds,boolean requireReadyConfirmation,ChallengerDisconnect challengerDisconnectBehavior,ChampionDisconnect championDisconnectBehavior) {}
    public record QualificationConfig(int tournamentSlots,boolean preserveVoucherClaimOrder) {}
    public record VoucherConfig(String displayName,boolean grantAfterEliteFour,boolean oneOpenClaimPerPlayer) {}
    public record RuntimeConfig(PlayAreaConfig playArea,boolean suppressNaturalGymWorldgen,boolean autoPlaceOnServerStart,LinkedHashMap<String,GymConfig> gyms,ChampionConfig champion,QualificationConfig qualification,VoucherConfig voucher) {
        public static RuntimeConfig defaults(){
            LinkedHashMap<String,GymConfig> g=new LinkedHashMap<>();
            g.put("pewter",new GymConfig(true,"rgs:pewter_gym","minecraft:overworld",0,80,0,Rotation.NONE));
            g.put("cerulean",new GymConfig(true,"rgs:cerulean_gym","minecraft:overworld",0,80,0,Rotation.NONE));
            g.put("vermilion",new GymConfig(true,"rgs:vermilion_gym","minecraft:overworld",0,80,0,Rotation.NONE));
            g.put("celadon",new GymConfig(true,"rgs:celadon_gym","minecraft:overworld",0,80,0,Rotation.NONE));
            g.put("fuchsia",new GymConfig(true,"rgs:fuchsia_gym","minecraft:overworld",0,80,0,Rotation.NONE));
            g.put("saffron",new GymConfig(true,"rgs:saffron_gym","minecraft:overworld",0,80,0,Rotation.NONE));
            g.put("cinnabar",new GymConfig(true,"rgs:cinnabar_gym","minecraft:overworld",0,80,0,Rotation.NONE));
            g.put("blackthorn",new GymConfig(true,"rgs:blackthorn_gym","minecraft:overworld",0,80,0,Rotation.NONE));
            g.put("league",new GymConfig(true,"rgs:kanto_league","minecraft:overworld",0,80,0,Rotation.NONE));
            return new RuntimeConfig(
                new PlayAreaConfig("minecraft:overworld",0,0,3000),true,false,g,
                new ChampionConfig(ChampionType.AI,"",new ArenaConfig("minecraft:overworld",new ArenaPoint(0,80,0,0,0),new ArenaPoint(8,80,0,180,0)),BattleFormat.DOUBLES,3600,true,ChallengerDisconnect.FORFEIT,ChampionDisconnect.REQUEUE),
                new QualificationConfig(15,true), new VoucherConfig("Champion Challenge Voucher",true,true)
            );
        }
    }

    public static final class ValidationReport {
        public int fallbackCount=0;
        public final List<String>warnings=new ArrayList<>();
        public boolean clean(){return fallbackCount==0;}
    }
}
