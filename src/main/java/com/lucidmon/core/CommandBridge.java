package com.lucidmon.core;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

/**
 * LucidMon command bridge backed directly by Fabric's public command API and Brigadier.
 *
 * Earlier unstable builds used reflection to stay mapping-agnostic, but that proved brittle
 * against Fabric's package-private event implementation and Brigadier's overloaded builder
 * methods. This class now uses the typed APIs that LucidMon already compiles against.
 */
public final class CommandBridge {
    private static volatile CommandDispatcher<CommandSourceStack> dispatcher;
    private static volatile CommandSourceStack serverSource;

    @FunctionalInterface public interface Handler { int run(Object context,Object source) throws Exception; }
    private CommandBridge() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((d, registryAccess, environment) -> {
            dispatcher = d;
            try {
                installTree(d);
                LucidMon.log("Registered /lucidmon command tree.");
            } catch (Throwable t) {
                // Keep a command-tree bug from crashing world creation. The error remains loud
                // in the log, and datapack functions that depend on /lucidmon will fail clearly.
                LucidMon.error("Could not install /lucidmon command tree", t);
            }
        });
    }

    private static void installTree(CommandDispatcher<CommandSourceStack> d) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("lucidmon")
            .executes(ctx -> runHandler(LeagueManager::cmdStatus, ctx));

        root.then(Commands.literal("config")
            .then(commandLiteral("validate", LeagueManager::cmdValidateConfig))
            .then(commandLiteral("reload", LeagueManager::cmdReload)));

        root.then(Commands.literal("gyms")
            .then(commandLiteral("place", LeagueManager::cmdPlaceAllGyms)));

        root.then(Commands.literal("gym")
            .then(Commands.literal("place").then(wordArg("id", LeagueManager::cmdPlaceGym)))
            .then(Commands.literal("locate").then(wordArg("id", LeagueManager::cmdLocateGym))));

        root.then(Commands.literal("map")
            .then(commandLiteral("start", LeagueManager::cmdMapStart))
            .then(commandLiteral("stop", LeagueManager::cmdMapStop))
            .then(commandLiteral("status", LeagueManager::cmdMapStatus))
            .then(commandLiteral("config", LeagueManager::cmdMapConfig)));

        root.then(Commands.literal("mapgen")
            .then(commandLiteral("status", LeagueManager::cmdMapGenStatus))
            .then(commandLiteral("validate", LeagueManager::cmdMapGenValidate))
            .then(commandLiteral("preflight", LeagueManager::cmdMapGenPreflight))
            .then(commandLiteral("audit-biomes", LeagueManager::cmdMapGenAuditBiomes)));

        root.then(Commands.literal("soulpack")
            .then(commandLiteral("reset", LeagueManager::cmdSoulpackReset))
            .then(commandLiteral("debug", LeagueManager::cmdSoulpackDebug)));

        root.then(commandLiteral("queue", LeagueManager::cmdQueue));
        root.then(commandLiteral("qualifiers", LeagueManager::cmdQualifiers));

        root.then(Commands.literal("champion")
            .then(commandLiteral("ready", LeagueManager::cmdChampionReady))
            .then(Commands.literal("result").then(wordArg("result", LeagueManager::cmdChampionResult))));

        root.then(Commands.literal("challenge")
            .then(Commands.literal("info").then(wordArg("player", LeagueManager::cmdChallengeInfo)))
            .then(Commands.literal("cancel").then(wordArg("player", LeagueManager::cmdChallengeCancel))));

        root.then(Commands.literal("cooldown")
            .then(Commands.literal("clear").then(wordArg("player", LeagueManager::cmdCooldownClear))));

        root.then(Commands.literal("internal")
            .then(commandLiteral("load", LeagueManager::cmdInternalLoad))
            .then(commandLiteral("tick", LeagueManager::cmdInternalTick))
            .then(commandLiteral("online", LeagueManager::cmdInternalOnline))
            .then(commandLiteral("elite_four", LeagueManager::cmdInternalEliteFour))
            .then(commandLiteral("voucher_used", LeagueManager::cmdInternalVoucherUsed)));

        d.register(root);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> commandLiteral(String name, Handler handler) {
        return Commands.literal(name).executes(ctx -> runHandler(handler, ctx));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> wordArg(String name, Handler handler) {
        return Commands.argument(name, StringArgumentType.word())
            .executes(ctx -> runHandler(handler, ctx));
    }

    private static int runHandler(Handler handler, CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        try {
            return handler.run(ctx, source);
        } catch (Throwable t) {
            LucidMon.error("Command failed", t);
            feedback(source, "LucidMon command failed: " + t.getMessage(), "red");
            return 0;
        }
    }

    public static String arg(Object ctx,String name){
        if (ctx instanceof CommandContext<?> context) {
            try { return String.valueOf(context.getArgument(name, String.class)); }
            catch (Exception ignored) { return ""; }
        }
        return "";
    }

    public static void captureServerSource(Object source){
        if(source instanceof CommandSourceStack s) serverSource=s;
    }
    public static Object serverSource(){return serverSource;}

    public static int executeServer(String command){return execute(command,serverSource);}
    public static int execute(String command,Object source){
        CommandDispatcher<CommandSourceStack> d = dispatcher;
        if(d==null || !(source instanceof CommandSourceStack s)) return 0;
        try {
            return d.execute(command,s);
        } catch(Throwable t){
            LucidMon.warn("Command failed: /"+command+" -> "+t.getMessage());
            return 0;
        }
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
