package com.lucidmon.core;

import com.lucidmon.mapgen.KantoMapGen;
import com.lucidmon.mapgen.KantoLayout;

import java.util.*;

/**
 * Unstable-6 server control surface.
 * Gym placement/config commands and Kanto MapGen controls are active. Champion
 * queue/battle integration remains guarded until the exact RCT battle hooks are integrated.
 */
public final class LeagueManager {
    private LeagueManager() {}

    public static int cmdStatus(Object ctx,Object src){
        var c=ConfigManager.current;
        var m=MapGenConfigManager.current;
        CommandBridge.feedback(src,"LucidMon "+LucidMon.DISPLAY_VERSION+" | Champion="+c.champion().type()+" | MapGen="+m.mapType(),"aqua");
        CommandBridge.feedback(src,"Core config="+ConfigManager.CONFIG_PATH+" | MapGen config="+MapGenConfigManager.CONFIG_PATH,"gray");
        CommandBridge.feedback(src,"Natural RGS gyms="+(c.suppressNaturalGymWorldgen()?"SUPPRESSED":"ENABLED")+" | manual gym placement remains available.",c.suppressNaturalGymWorldgen()?"green":"yellow");
        CommandBridge.feedback(src,"Active test modules: Kanto MapGen layout v2, gym placement commands, map reveal, soulpack, virtual pasture. Player Champion battle flow is scaffold-only.","yellow");
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
        if(g.rotation()!=ConfigManager.Rotation.NONE) CommandBridge.feedback(src,"UNSTABLE.6 note: RGS /place structure does not apply configured rotation yet; placing with NONE orientation.","yellow");
        String cmd="execute in "+g.dimension()+" run place structure "+g.structure()+" "+g.x()+" "+g.y()+" "+g.z();
        int result=CommandBridge.execute(cmd,src);
        if(result>0)CommandBridge.feedback(src,"Placed "+id+" at "+g.x()+" "+g.y()+" "+g.z()+".","green");
        else CommandBridge.feedback(src,"Minecraft reported placement failure for "+id+". Check RGS is installed and the coordinates/chunks are valid.","red");
        return result;
    }

    public static int cmdMapGenStatus(Object ctx,Object src){
        var m=MapGenConfigManager.current;
        var k=m.kanto();
        var layout=KantoLayout.current();
        CommandBridge.feedback(src,"MapGen profile="+m.mapType()+" | config="+MapGenConfigManager.CONFIG_PATH,"aqua");
        CommandBridge.feedback(src,"Kanto layout v2: center="+k.centerX()+","+k.centerZ()+" diameter="+k.playableDiameter()+" requiredBiomes="+k.requiredBiomeCount()+" routes="+k.routesEnabled()+" MtMoon="+k.mtMoonEnabled(),"gray");
        CommandBridge.feedback(src,"City hierarchy: starter="+layout.startingCity().x()+","+layout.startingCity().z()+" r="+layout.startingCity().radius()+" | central="+layout.centralCity().x()+","+layout.centralCity().z()+" r="+layout.centralCity().radius(),"gray");
        CommandBridge.feedback(src,"Boundary policy="+k.outsidePlayableAreaMode()+" biome="+k.outsidePlayableAreaBiome()+" | playableHalf="+k.playableHalfExtent()+" safeLandHalf="+k.safeLandHalfExtent()+" oceanBuffer="+k.oceanBufferBlocks(),"gray");
        if(m.mapType()==MapGenConfigManager.MapType.KANTO_ARCHIPELAGO) {
            String color=KantoMapGen.isActive()?"green":"yellow";
            CommandBridge.feedback(src,"Generator activation: "+KantoMapGen.activationMessage(),color);
        } else {
            CommandBridge.feedback(src,"STANDARD selected: LucidMon MapGen makes no terrain-generation changes.","green");
        }
        return 1;
    }
    public static int cmdMapGenValidate(Object ctx,Object src){
        var r=MapGenConfigManager.lastReport;
        CommandBridge.feedback(src,"MapGen validation: "+r.fallbackCount+" fallback(s), "+r.errors.size()+" error(s), "+r.warnings.size()+" warning(s).","aqua");
        for(String e:r.errors) CommandBridge.feedback(src,e,"red");
        for(String w:r.warnings) CommandBridge.feedback(src,w,"yellow");
        return r.errors.isEmpty()?1:0;
    }
    public static int cmdMapGenPreflight(Object ctx,Object src){
        cmdMapGenStatus(ctx,src);
        cmdMapGenValidate(ctx,src);
        var m=MapGenConfigManager.current;
        var k=m.kanto();
        var layout=KantoLayout.current();
        if(m.mapType()==MapGenConfigManager.MapType.KANTO_ARCHIPELAGO){
            if(!MapGenConfigManager.lastReport.errors.isEmpty()){
                CommandBridge.feedback(src,"BLOCKER: KANTO_ARCHIPELAGO has boundary/profile validation errors. Fix them before world creation.","red");
                return 0;
            }
            CommandBridge.feedback(src,"Profile values: starter="+layout.startingCity().x()+","+layout.startingCity().z()+" | central="+layout.centralCity().x()+","+layout.centralCity().z()+" | volcano="+layout.volcano().x()+","+layout.volcano().z()+" | MtMoon="+k.mtMoonX()+","+k.mtMoonZ(),"aqua");
            if(!KantoMapGen.isActive()){
                CommandBridge.feedback(src,"BLOCKER: Kanto world preset is not active. Singleplayer: create a NEW world with World Type 'LucidMon: Kanto Archipelago'. Dedicated server: set level-type="+KantoMapGen.WORLD_PRESET_ID+" before creating the world.","red");
                return 0;
            }
            CommandBridge.feedback(src,"KANTO pre-flight PASS: layout v2 generator active, profile lock verified, boundary policy loaded.","green");
            return 1;
        }
        CommandBridge.feedback(src,"STANDARD pre-flight PASS: this MapGen module will not alter normal world generation.","green");
        return 1;
    }
    public static int cmdMapGenAuditBiomes(Object ctx,Object src){
        if(!KantoMapGen.isActive()){
            CommandBridge.feedback(src,"Biome audit requires an active KANTO_ARCHIPELAGO world.","yellow");
            return 0;
        }
        CommandBridge.feedback(src,"Kanto generator is active. Full 53-biome generated-world audit scanning is not yet implemented in unstable.6; use this build for layout-v2 terrain/biome verification first.","yellow");
        return 0;
    }

    public static int cmdMapStart(Object ctx,Object src){return forward(src,"function lucid_map:start","Started LucidMon Map Reveal.");}
    public static int cmdMapStop(Object ctx,Object src){return forward(src,"function lucid_map:stop","Stopped LucidMon Map Reveal.");}
    public static int cmdMapStatus(Object ctx,Object src){return forward(src,"function lucid_map:status","Map Reveal status requested.");}
    public static int cmdMapConfig(Object ctx,Object src){return forward(src,"function lucid_map:show_config","Map Reveal config requested.");}
    public static int cmdSoulpackReset(Object ctx,Object src){return forward(src,"function soulpack:reset_recovery_state","Soulpack recovery state reset for this command source.");}
    public static int cmdSoulpackDebug(Object ctx,Object src){return forward(src,"function soulpack:debug_state","Soulpack debug requested.");}
    private static int forward(Object src,String command,String ok){int r=CommandBridge.execute(command,src);if(r>0)CommandBridge.feedback(src,ok,"green");return r;}

    private static int championPending(Object src){CommandBridge.feedback(src,"Player Champion queue/battle integration is not enabled in v0.1.2-unstable.6. The JSON5 section remains parsed for forward compatibility.","yellow");return 0;}
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
