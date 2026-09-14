package com.lucidmon.core;

import java.lang.reflect.*;
import java.util.*;

/**
 * Reflection-based command bridge. This keeps the LucidMon core classes independent
 * of Yarn/intermediary names while still registering normal Brigadier commands through Fabric API.
 */
public final class CommandBridge {
    private static volatile Object dispatcher;
    private static volatile Object serverSource;
    private static Method dispatcherExecute;

    @FunctionalInterface public interface Handler { int run(Object context,Object source) throws Exception; }
    private CommandBridge() {}

    public static void register() {
        try {
            Class<?> callback = Class.forName("net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback");
            Object event = callback.getField("EVENT").get(null);
            Object listener = Proxy.newProxyInstance(callback.getClassLoader(), new Class<?>[]{callback}, (p,m,args)->{
                if (m.getName().equals("register") && args != null && args.length >= 1) {
                    dispatcher = args[0];
                    installTree(dispatcher);
                    LucidMon.log("Registered /lucidmon command tree.");
                }
                return null;
            });

            // Invoke Event.register through Fabric's public Event API rather than the
            // package-private ArrayBackedEvent implementation returned by EVENT. Reflecting
            // on event.getClass() causes IllegalAccessException on current Fabric API builds.
            Class<?> eventApi = Class.forName("net.fabricmc.fabric.api.event.Event");
            Method reg = eventApi.getMethod("register", Object.class);
            reg.invoke(event, listener);
        } catch (Throwable t) {
            LucidMon.error("Could not register Fabric commands", t);
        }
    }

    private static void installTree(Object d) throws Exception {
        Object root = literal("lucidmon");
        executes(root, LeagueManager::cmdStatus);

        Object config = literal("config");
        then(config, commandLiteral("validate", LeagueManager::cmdValidateConfig));
        then(config, commandLiteral("reload", LeagueManager::cmdReload));
        then(root, config);

        Object gyms = literal("gyms");
        then(gyms, commandLiteral("place", LeagueManager::cmdPlaceAllGyms));
        then(root, gyms);

        Object gym = literal("gym");
        Object gymPlace = literal("place"); then(gymPlace, wordArg("id", LeagueManager::cmdPlaceGym));
        Object gymLocate = literal("locate"); then(gymLocate, wordArg("id", LeagueManager::cmdLocateGym));
        then(gym, gymPlace); then(gym, gymLocate); then(root, gym);

        Object map = literal("map");
        then(map, commandLiteral("start", LeagueManager::cmdMapStart));
        then(map, commandLiteral("stop", LeagueManager::cmdMapStop));
        then(map, commandLiteral("status", LeagueManager::cmdMapStatus));
        then(map, commandLiteral("config", LeagueManager::cmdMapConfig));
        then(root, map);

        Object mapgen = literal("mapgen");
        then(mapgen, commandLiteral("status", LeagueManager::cmdMapGenStatus));
        then(mapgen, commandLiteral("validate", LeagueManager::cmdMapGenValidate));
        then(mapgen, commandLiteral("preflight", LeagueManager::cmdMapGenPreflight));
        then(mapgen, commandLiteral("audit-biomes", LeagueManager::cmdMapGenAuditBiomes));
        then(root, mapgen);

        Object soulpack = literal("soulpack");
        then(soulpack, commandLiteral("reset", LeagueManager::cmdSoulpackReset));
        then(soulpack, commandLiteral("debug", LeagueManager::cmdSoulpackDebug));
        then(root, soulpack);

        then(root, commandLiteral("queue", LeagueManager::cmdQueue));
        then(root, commandLiteral("qualifiers", LeagueManager::cmdQualifiers));

        Object champion = literal("champion");
        then(champion, commandLiteral("ready", LeagueManager::cmdChampionReady));
        Object result = literal("result"); then(result, wordArg("result", LeagueManager::cmdChampionResult));
        then(champion, result); then(root, champion);

        Object challenge = literal("challenge");
        Object info = literal("info"); then(info, wordArg("player", LeagueManager::cmdChallengeInfo));
        Object cancel = literal("cancel"); then(cancel, wordArg("player", LeagueManager::cmdChallengeCancel));
        then(challenge, info); then(challenge, cancel); then(root, challenge);

        Object cooldown = literal("cooldown");
        Object clear = literal("clear"); then(clear, wordArg("player", LeagueManager::cmdCooldownClear));
        then(cooldown, clear); then(root, cooldown);

        Object internal = literal("internal");
        then(internal, commandLiteral("load", LeagueManager::cmdInternalLoad));
        then(internal, commandLiteral("tick", LeagueManager::cmdInternalTick));
        then(internal, commandLiteral("online", LeagueManager::cmdInternalOnline));
        then(internal, commandLiteral("elite_four", LeagueManager::cmdInternalEliteFour));
        then(internal, commandLiteral("voucher_used", LeagueManager::cmdInternalVoucherUsed));
        then(root, internal);

        Method register = Arrays.stream(d.getClass().getMethods())
            .filter(m->m.getName().equals("register")&&m.getParameterCount()==1).findFirst().orElseThrow();
        register.invoke(d, root);
        dispatcherExecute = Arrays.stream(d.getClass().getMethods())
            .filter(m->m.getName().equals("execute")&&m.getParameterCount()==2&&m.getParameterTypes()[0]==String.class)
            .findFirst().orElse(null);
    }

    private static Object commandLiteral(String name, Handler h) throws Exception { Object b=literal(name); executes(b,h); return b; }
    private static Object literal(String name) throws Exception {
        Class<?> c=Class.forName("com.mojang.brigadier.builder.LiteralArgumentBuilder");
        return c.getMethod("literal",String.class).invoke(null,name);
    }
    private static Object wordArg(String name, Handler h) throws Exception {
        Class<?> sat=Class.forName("com.mojang.brigadier.arguments.StringArgumentType");
        Object argType=sat.getMethod("word").invoke(null);
        Class<?> rat=Class.forName("com.mojang.brigadier.builder.RequiredArgumentBuilder");
        Class<?> at=Class.forName("com.mojang.brigadier.arguments.ArgumentType");
        Object b=rat.getMethod("argument",String.class,at).invoke(null,name,argType);
        executes(b,h); return b;
    }
    private static void executes(Object b, Handler h) throws Exception {
        Class<?> command=Class.forName("com.mojang.brigadier.Command");
        Object proxy=Proxy.newProxyInstance(command.getClassLoader(),new Class<?>[]{command},(p,m,args)->{
            if(m.getName().equals("run")){
                Object ctx=args[0]; Object src=contextSource(ctx);
                try{return h.run(ctx,src);}catch(Throwable t){LucidMon.error("Command failed",t);feedback(src,"LucidMon command failed: "+t.getMessage(),"red");return 0;}
            }
            return 0;
        });
        Method ex=Arrays.stream(b.getClass().getMethods()).filter(m->m.getName().equals("executes")&&m.getParameterCount()==1).findFirst().orElseThrow();
        ex.invoke(b,proxy);
    }
    private static void then(Object parent,Object child)throws Exception{
        Method m=Arrays.stream(parent.getClass().getMethods()).filter(x->x.getName().equals("then")&&x.getParameterCount()==1).findFirst().orElseThrow();
        m.invoke(parent,child);
    }
    private static Object contextSource(Object ctx)throws Exception{return ctx.getClass().getMethod("getSource").invoke(ctx);}

    public static String arg(Object ctx,String name){
        try{return String.valueOf(ctx.getClass().getMethod("getArgument",String.class,Class.class).invoke(ctx,name,String.class));}
        catch(Exception e){return "";}
    }
    public static void captureServerSource(Object source){ if(source!=null) serverSource=source; }
    public static Object serverSource(){return serverSource;}

    public static int executeServer(String command){return execute(command,serverSource);}
    public static int execute(String command,Object source){
        if(dispatcher==null||source==null)return 0;
        try{
            Method m=dispatcherExecute;
            if(m==null)m=Arrays.stream(dispatcher.getClass().getMethods())
                .filter(x->x.getName().equals("execute")&&x.getParameterCount()==2&&x.getParameterTypes()[0]==String.class).findFirst().orElse(null);
            if(m==null)return 0;
            Object r=m.invoke(dispatcher,command,source); return r instanceof Number n?n.intValue():0;
        }catch(InvocationTargetException e){LucidMon.warn("Command failed: /"+command+" -> "+e.getTargetException().getMessage());return 0;}
        catch(Throwable t){LucidMon.warn("Command bridge error for /"+command+": "+t.getMessage());return 0;}
    }

    public static void feedback(Object source,String message,String color){
        LucidMon.log("[Command] "+message);
        if(source!=null) execute("tellraw @s "+jsonText(message,color),source);
    }
    public static String jsonText(String msg,String color){return "{\"text\":"+quote(msg)+",\"color\":"+quote(color==null?"white":color)+"}";}
    public static String quote(String s){
        if(s==null)s=""; StringBuilder b=new StringBuilder("\"");
        for(char c:s.toCharArray()) switch(c){case '\\'->b.append("\\\\");case '"'->b.append("\\\"");case '\n'->b.append("\\n");case '\r'->b.append("\\r");default->b.append(c);} return b.append('"').toString();
    }
}
