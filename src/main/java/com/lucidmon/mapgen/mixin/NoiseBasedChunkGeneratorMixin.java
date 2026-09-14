package com.lucidmon.mapgen.mixin;

import com.lucidmon.mapgen.KantoBiomeSource;
import com.lucidmon.mapgen.KantoTerrainShaper;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;

/**
 * Keeps vanilla's Overworld noise/caves/features pipeline, then constrains the
 * macro terrain whenever the lucidmon:kanto biome source is active.
 */
@Mixin(NoiseBasedChunkGenerator.class)
public abstract class NoiseBasedChunkGeneratorMixin {
    private boolean lucidmon$isKanto() {
        NoiseBasedChunkGenerator self = (NoiseBasedChunkGenerator)(Object)this;
        return KantoTerrainShaper.enabled() && self.getBiomeSource() instanceof KantoBiomeSource;
    }

    @Inject(method = "fillFromNoise", at = @At("RETURN"), cancellable = true)
    private void lucidmon$shapeNoise(Blender blender, RandomState random, StructureManager manager, ChunkAccess chunk,
                                     CallbackInfoReturnable<CompletableFuture<ChunkAccess>> cir) {
        if (!lucidmon$isKanto()) return;
        CompletableFuture<ChunkAccess> original = cir.getReturnValue();
        cir.setReturnValue(original.thenApply(result -> {
            KantoTerrainShaper.shapeChunk(result, random);
            return result;
        }));
    }

    @Inject(method = "getBaseHeight", at = @At("HEAD"), cancellable = true)
    private void lucidmon$baseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState random,
                                     CallbackInfoReturnable<Integer> cir) {
        if (!lucidmon$isKanto()) return;
        cir.setReturnValue(KantoTerrainShaper.baseHeight(x, z, type, random));
    }

    @Inject(method = "getBaseColumn", at = @At("HEAD"), cancellable = true)
    private void lucidmon$baseColumn(int x, int z, LevelHeightAccessor level, RandomState random,
                                     CallbackInfoReturnable<NoiseColumn> cir) {
        if (!lucidmon$isKanto()) return;
        cir.setReturnValue(KantoTerrainShaper.baseColumn(x, z, level.getMinBuildHeight(), level.getMaxBuildHeight(), random));
    }

    @Inject(
        method = "buildSurface(Lnet/minecraft/server/level/WorldGenRegion;Lnet/minecraft/world/level/StructureManager;Lnet/minecraft/world/level/levelgen/RandomState;Lnet/minecraft/world/level/chunk/ChunkAccess;)V",
        at = @At("RETURN")
    )
    private void lucidmon$routes(WorldGenRegion level, StructureManager manager, RandomState random, ChunkAccess chunk, CallbackInfo ci) {
        if (lucidmon$isKanto()) KantoTerrainShaper.paintRoutes(chunk);
    }

    @Inject(method = "applyCarvers", at = @At("RETURN"))
    private void lucidmon$canonicalTunnels(WorldGenRegion level, long seed, RandomState random, BiomeManager biomeManager,
                                            StructureManager structureManager, ChunkAccess chunk, GenerationStep.Carving step,
                                            CallbackInfo ci) {
        if (lucidmon$isKanto()) KantoTerrainShaper.carveCanonicalTunnels(chunk);
    }
}
