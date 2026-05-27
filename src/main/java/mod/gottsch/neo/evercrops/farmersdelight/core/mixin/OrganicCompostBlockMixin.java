/*
 * This file is part of EverCrops: Farmer's Delight.
 * Copyright (c) 2026 Mark Gottschling (gottsch)
 *
 * EverCrops: Farmer's Delight is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * EverCrops: Farmer's Delight is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with EverCrops: Farmer's Delight.  If not, see <http://www.gnu.org/licenses/lgpl>.
 */
package mod.gottsch.neo.evercrops.farmersdelight.core.mixin;

import mod.gottsch.forge.evercrops.core.persistence.CropCatchUp;
import mod.gottsch.forge.evercrops.core.persistence.CropRegistry;
import mod.gottsch.forge.evercrops.core.persistence.CropState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vectorwing.farmersdelight.common.block.OrganicCompostBlock;
import vectorwing.farmersdelight.common.registry.ModBlocks;
import vectorwing.farmersdelight.common.tag.ModTags;

import java.util.Optional;

/**
 * Catch-up growth for Farmer's Delight OrganicCompostBlock (COMPOSTING stages 0-7 → Rich Soil).
 *
 * Each random tick, vanilla tests a probability gate built from nearby compost activators,
 * water, and sky light. If the gate passes, the composting stage advances by one. When the
 * block reaches stage 7 and the gate passes again, it transforms into a Rich Soil block.
 *
 * Catch-up strategy:
 *   - Compute the current gate probability from the block's real neighbourhood (mirrors FD logic).
 *   - Derive AVG_GROWTH_TICK_INTERVAL = AVG_CALL_TICK_INTERVAL / chance.
 *   - Apply 'steps' stage advances in one shot. If steps reach or exceed the number of
 *     transitions needed to transform, place Rich Soil immediately (multi-step catch-up).
 *   - Cancel vanilla's own randomTick body when catch-up fires, because vanilla still holds
 *     the old BlockState parameter and would overwrite our setBlock if its chance roll landed.
 *   - A second INVOKE inject keeps lastGrowthGameTime current when vanilla itself advances
 *     the block normally (no catch-up needed).
 *   - The CropState at this position is NOT removed after a transformation to Rich Soil.
 *     RichSoilBlockMixin will pick it up on the next random tick and use it as its own state.
 *
 * @author Mark Gottschling on 2026-05-26
 */
@Mixin(OrganicCompostBlock.class)
public abstract class OrganicCompostBlockMixin extends Block {

    public OrganicCompostBlockMixin(Properties properties) {
        super(properties);
    }

    @Inject(method = "randomTick", at = @At("HEAD"), cancellable = true)
    public void everCropsFD_randomTick(BlockState state, ServerLevel level, BlockPos pos,
                                       RandomSource random, CallbackInfo ci) {
        if (!state.hasProperty(OrganicCompostBlock.COMPOSTING)) return;

        Optional<CropState> existing = CropRegistry.get(level, pos);
        if (existing.isEmpty()) {
            CropRegistry.put(level, pos, CropCatchUp.createState(level, pos));
            return;
        }
        CropState cropState = existing.get();

        // Probability is dynamic: compute it from the current neighbourhood to get an accurate
        // average interval. Mirrors the chance formula inside OrganicCompostBlock.randomTick.
        float chance = everCropsFD_computeChance(level, pos);
        if (chance <= 0.0F) {
            CropRegistry.put(level, pos, cropState);
            return;
        }
        int avgGrowthInterval = Math.max(1, (int) (CropCatchUp.AVG_CALL_TICK_INTERVAL / chance));

        int steps = CropCatchUp.beginCatchUp(level, pos, cropState, avgGrowthInterval, false);
        if (steps > 0) {
            int currentStage = state.getValue(OrganicCompostBlock.COMPOSTING);
            OrganicCompostBlock self = (OrganicCompostBlock)(Object) this;
            int maxStage = self.getMaxCompostingStage();
            // Number of transitions to reach Rich Soil: advance (maxStage - currentStage) stages,
            // then one final transition that replaces the block.
            int transitionsToTransform = (maxStage - currentStage) + 1;

            if (steps >= transitionsToTransform) {
                // Enough time elapsed — transform straight to Rich Soil.
                level.setBlock(pos, ((Block) ModBlocks.RICH_SOIL.get()).defaultBlockState(), 3);
            } else {
                // Partially advance the composting stage.
                level.setBlock(pos, state.setValue(OrganicCompostBlock.COMPOSTING, currentStage + steps), 3);
            }
            CropRegistry.put(level, pos, cropState);
            ci.cancel();
        } else {
            CropRegistry.put(level, pos, cropState);
        }
    }

    /**
     * Mirrors the chance computation from OrganicCompostBlock.randomTick so that
     * the average growth interval is derived from the block's actual surroundings.
     */
    @Unique
    private float everCropsFD_computeChance(ServerLevel level, BlockPos pos) {
        float chance = 0.0F;
        boolean hasWater = false;
        int maxLight = 0;
        for (BlockPos neighborPos : BlockPos.betweenClosed(pos.offset(-1, -1, -1), pos.offset(1, 1, 1))) {
            BlockState neighborState = level.getBlockState(neighborPos);
            if (neighborState.is(ModTags.Blocks.COMPOST_ACTIVATORS)) {
                chance += 0.02F;
            }
            if (neighborState.getFluidState().is(FluidTags.WATER)) {
                hasWater = true;
            }
            int light = level.getBrightness(LightLayer.SKY, neighborPos.above());
            if (light > maxLight) {
                maxLight = light;
            }
        }
        chance += maxLight > 12 ? 0.1F : 0.05F;
        chance += hasWater ? 0.1F : 0.0F;
        return chance;
    }

    /**
     * Keeps lastGrowthGameTime current when vanilla itself advances the composting stage or
     * transforms the block to Rich Soil (the ci.cancel() path does not reach here).
     * Fires for both setBlock call sites inside randomTick (stage advance and transformation).
     */
    @Inject(method = "randomTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"))
    public void everCropsFD_randomTick_setBlock(BlockState state, ServerLevel level, BlockPos pos,
                                                RandomSource random, CallbackInfo ci) {
        Optional<CropState> cropState = CropRegistry.get(level, pos);
        if (cropState.isPresent()) {
            cropState.get().setLastGrowthGameTime(level.getGameTime())
                    .setLastGrowthLightLevel(level.getRawBrightness(pos, 0));
            CropRegistry.put(level, pos, cropState.get());
        }
        // CropState is intentionally left in place even when vanilla transforms to Rich Soil;
        // RichSoilBlockMixin will adopt it on the first tick of the new block.
    }
}
