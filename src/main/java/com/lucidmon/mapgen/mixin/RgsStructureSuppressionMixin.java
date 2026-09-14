package com.lucidmon.mapgen.mixin;

import com.lucidmon.core.ConfigManager;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;

/**
 * Prevents RGS gyms and the Kanto League from being selected by normal world
 * structure generation while LucidMon owns their placement.
 *
 * This hook is intentionally at ChunkGenerator's structure-selection boundary:
 * /place structure and LucidMon's manual gym commands do not pass through this
 * path, so administrators can still place the exact RGS templates after the map
 * is finalized. Set gyms.suppressNaturalWorldgen=false to restore native RGS
 * natural spawning.
 */
@Mixin(ChunkGenerator.class)
public abstract class RgsStructureSuppressionMixin {
    private static final Set<String> LUCIDMON_MANAGED_RGS_STRUCTURES = Set.of(
            "pewter_gym",
            "cerulean_gym",
            "vermilion_gym",
            "celadon_gym",
            "fuchsia_gym",
            "saffron_gym",
            "cinnabar_gym",
            "blackthorn_gym",
            "kanto_league"
    );

    @Inject(method = "tryGenerateStructure", at = @At("HEAD"), cancellable = true)
    private void lucidmon$suppressNaturalRgsGym(
            StructureSet.StructureSelectionEntry selected,
            StructureManager structureManager,
            RegistryAccess registryAccess,
            RandomState randomState,
            StructureTemplateManager templateManager,
            long seed,
            ChunkAccess chunk,
            ChunkPos chunkPos,
            SectionPos sectionPos,
            CallbackInfoReturnable<Boolean> cir) {
        if (!ConfigManager.current.suppressNaturalGymWorldgen()) return;

        selected.structure().unwrapKey().ifPresent(key -> {
            var id = key.location();
            if (id.getNamespace().equals("rgs") && LUCIDMON_MANAGED_RGS_STRUCTURES.contains(id.getPath())) {
                cir.setReturnValue(false);
            }
        });
    }
}
