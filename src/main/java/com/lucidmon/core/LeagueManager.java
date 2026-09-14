package com.lucidmon.core;

import java.util.*;

/**
 * Unstable-2 server control surface.
 * Gym placement/config commands are active. Champion queue/battle integration is intentionally
 * guarded until the exact RCT 0.19.0 battle hooks are integrated in a later unstable build.
 */
public final class LeagueManager {
    private LeagueManager() {}

    public static int cmdStatus(Object ctx,Object src){
        var c=ConfigManager.current;
        var m=MapGenConfigManager.current;
        CommandBridge.feedback(src,"LucidMon "+LucidMon.DISPLAY_VERSION+" | Champion="+c.champion().type()+" | MapGen="+m.mapType(),"aqua");
        CommandBridge.feedback(src,"Core config="+ConfigManager.CONFIG_PATH+" | MapGen config="+MapGenConfigManager.CONFIG_PATH,"gray");
        CommandBridge.feedback(src,"Active test modules: gym placement commands, MapGen pre-flight/config, map reveal, soulpack, virtual pasture. Player Champion battle flow is scaffold-only.","yellow");
        return 1;
    }
    public static int cmdValidateConfig(Object ctx,Object src){
        var r=ConfigManager.lastReport;
        CommandBridge.feedback(src,"Config validation: "+r.fallbackCount+" fallback(s), "+r.warnings.size()+" warning(s).","aqua");
        for(String w:r.warnings) CommandBridge.feedback(src,w,"yellow");
        return 1;
    }
    public static int cmdReload(Object ctx,Object src){ ConfigManager.load(); MapGenConfigManager.load(); int a=cmdValidateConfig(ctx,src); int b=cmdMapGenValidate(ctx,src); return (a>0&&b>0)?1:0; }

    public static int cmdPlaceAllGyms(Object ctx,Object src){
        int ok=0;
        for(String id:ConfigManager.current.gyms().keySet()) ok += placeGym(id,src);
        CommandBridge.feedback(src,"Gym placement commands completed: "+ok+" successful command(s).","green");
        return ok>0?1:0;
    }
    public static int cmdPlaceGym(Object ctx,Object src){ return placeGym(CommandBridge.arg(ctx,"id").toLowerCase(Locale.ROOT),src)>0?1:0; }
    public static int cmdLocateGym(Object ctx,Object src){
        String id=CommandBridge.arg(ctx,"id").toLowerCase(Locale.ROOT);
        var g=ConfigManager.current.gyms().get(id);
        if(g==null){CommandBridge.feedback(src,"Unknown gym id '"+id+"'. Valid: "+ConfigManager.current.gyms().keySet(),"red");return 0;}
        CommandBridge.feedback(src,id+" -> "+g.structure()+" at "+g.x()+" "+g.y()+" "+g.z()+" in "+g.dimension()+" rotation="+g.rotation(),"aqua");
        return 1;
    }
    private static int placeGym(String id,Object src){
        var g=ConfigManager.current.gyms().get(id);
        if(g==null){CommandBridge.feedback(src,"Unknown gym id '"+id+"'. Valid: "+ConfigManager.current.gyms().keySet(),"red");return 0;}
        if(!g.enabled()){CommandBridge.feedback(src,"Gym '"+id+"' is disabled in config.","yellow");return 0;}
        if(g.x()==0&&g.y()==80&&g.z()==0){CommandBridge.feedback(src,"Refusing to place '"+id+"' at the untouched default 0 80 0. Configure coordinates first.","red");return 0;}
        if(g.rotation()!=ConfigManager.Rotation.NONE) CommandBridge.feedback(src,"UNSTABLE.2 note: RGS /place structure does not apply configured rotation yet; placing with NONE orientation.","yellow");
        String cmd="execute in "+g.dimension()+" run place structure "+g.structure()+" "+g.x()+" "+g.y()+" "+g.z();
        int result=CommandBridge.execute(cmd,src);
        if(result>0)CommandBridge.feedback(src,"Placed "+id+" at "+g.x()+" "+g.y()+" "+g.z()+".","green");
        else CommandBridge.feedback(src,"Minecraft reported placement failure for "+id+". Check RGS is installed and the coordinates/chunks are valid.","red");
        return result;
    }

    public static int cmdMapGenStatus(Object ctx,Object src){
        var m=MapGenConfigManager.current;
        var k=m.kanto();
        CommandBridge.feedback(src,"MapGen profile="+m.mapType()+" | config="+MapGenConfigManager.CONFIG_PATH,"aqua");
        CommandBridge.feedback(src,"Kanto profile: center="+k.centerX()+","+k.centerZ()+" diameter="+k.playableDiameter()+" requiredBiomes="+k.requiredBiomeCount()+" routes="+k.routesEnabled()+" MtMoon="+k.mtMoonEnabled(),"gray");
        if(m.mapType()==MapGenConfigManager.MapType.KANTO_ARCHIPELAGO)
            CommandBridge.feedback(src,"UNSTABLE.2 PRE-FLIGHT: KANTO_ARCHIPELAGO terrain generation is NOT active yet. Do not create the final Kanto world with this build.","red");
        else
            CommandBridge.feedback(src,"STANDARD selected: LucidMon MapGen makes no terrain-generation changes.","green");
        return 1;
    }
    public static int cmdMapGenValidate(Object ctx,Object src){
        var r=MapGenConfigManager.lastReport;
        CommandBridge.feedback(src,"MapGen validation: "+r.fallbackCount+" fallback(s), "+r.warnings.size()+" warning(s).","aqua");
        for(String w:r.warnings) CommandBridge.feedback(src,w,"yellow");
        return 1;
    }
    public static int cmdMapGenPreflight(Object ctx,Object src){
        cmdMapGenStatus(ctx,src);
        cmdMapGenValidate(ctx,src);
        var m=MapGenConfigManager.current;
        var k=m.kanto();
        if(m.mapType()==MapGenConfigManager.MapType.KANTO_ARCHIPELAGO){
            CommandBridge.feedback(src,"Pre-flight profile values loaded: startCity="+k.startingCityStructure()+" @ "+k.startingCityX()+","+k.startingCityZ()+" | volcano="+k.volcanoX()+","+k.volcanoZ()+" | MtMoon="+k.mtMoonX()+","+k.mtMoonZ(),"aqua");
            CommandBridge.feedback(src,"BLOCKER: absolute-coordinate Kanto chunk generator is not enabled in this build. Keep the final world folder absent until a generator-enabled build is installed.","red");
            return 0;
        }
        CommandBridge.feedback(src,"STANDARD pre-flight PASS: this MapGen module will not alter normal world generation.","green");
        return 1;
    }
    public static int cmdMapGenAuditBiomes(Object ctx,Object src){
        CommandBridge.feedback(src,"Biome audit requires a generated KANTO_ARCHIPELAGO world. The audit command is reserved now but cannot run until the Kanto generator is enabled in a later unstable build.","yellow");
        return 0;
    }

    public static int cmdMapStart(Object ctx,Object src){return forward(src,"function lucid_map:start","Started LucidMon Map Reveal.");}
    public static int cmdMapStop(Object ctx,Object src){return forward(src,"function lucid_map:stop","Stopped LucidMon Map Reveal.");}
    public static int cmdMapStatus(Object ctx,Object src){return forward(src,"function lucid_map:status","Map Reveal status requested.");}
    public static int cmdMapConfig(Object ctx,Object src){return forward(src,"function lucid_map:show_config","Map Reveal config requested.");}
    public static int cmdSoulpackReset(Object ctx,Object src){return forward(src,"function soulpack:reset_recovery_state","Soulpack recovery state reset for this command source.");}
    public static int cmdSoulpackDebug(Object ctx,Object src){return forward(src,"function soulpack:debug_state","Soulpack debug requested.");}
    private static int forward(Object src,String command,String ok){int r=CommandBridge.execute(command,src);if(r>0)CommandBridge.feedback(src,ok,"green");return r;}

    private static int championPending(Object src){CommandBridge.feedback(src,"Player Champion queue/battle integration is not enabled in v0.1.1-unstable.2. The JSON5 section is parsed now so its format is stable for testing.","yellow");return 0;}
    public static int cmdQueue(Object c,Object s){return championPending(s);} public static int cmdQualifiers(Object c,Object s){return championPending(s);}
    public static int cmdChampionReady(Object c,Object s){return championPending(s);} public static int cmdChampionResult(Object c,Object s){return championPending(s);}
    public static int cmdChallengeInfo(Object c,Object s){return championPending(s);} public static int cmdChallengeCancel(Object c,Object s){return championPending(s);}
    public static int cmdCooldownClear(Object c,Object s){return championPending(s);}

    public static int cmdInternalLoad(Object ctx,Object src){ CommandBridge.captureServerSource(src); ConfigManager.load(); MapGenConfigManager.load(); return 1; }
    public static int cmdInternalTick(Object ctx,Object src){ CommandBridge.captureServerSource(src); return 1; }
    public static int cmdInternalOnline(Object ctx,Object src){ return 1; }
    public static int cmdInternalEliteFour(Object ctx,Object src){ return championPending(src); }
    public static int cmdInternalVoucherUsed(Object ctx,Object src){ return championPending(src); }
}
