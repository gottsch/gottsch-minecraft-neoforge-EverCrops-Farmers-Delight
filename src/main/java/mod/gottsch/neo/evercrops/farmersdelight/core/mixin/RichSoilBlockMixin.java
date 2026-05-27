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
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vectorwing.farmersdelight.common.block.RichSoilBlock;
import vectorwing.farmersdelight.common.registry.ModBlocks;

import java.util.Optional;

/**
 * Catch-up growth for Farmer's Delight RichSoilBlock — specifically the mushroom-to-colony
 * conversion that happens when a brown or red mushroom is placed on top of Rich Soil.
 *
 * Vanilla converts the mushroom on the first successful randomTick after the block is loaded.
 * Because randomTick only fires in loaded chunks, the mushroom can sit unconverted indefinitely
 * while the area is unloaded. This mixin detects the missed time and performs the conversion
 * immediately when the chunk reloads.
 *
 * Design notes:
 *   - AVG_GROWTH_TICK_INTERVAL = AVG_CALL_TICK_INTERVAL (1350): every randomTick that fires is
 *     a potential conversion attempt, so the average interval between attempts equals the average
 *     interval between random ticks for a single block.
 *   - The inject is NOT cancellable. Vanilla's randomTick continues to run for the plant-boost
 *     behaviour (tryBoostingPlantsAboveAndBelow). After our convert the block above is a colony,
 *     so vanilla's own convertMushroomToColony returns false and the boost path takes over
 *     instead — entirely correct vanilla behaviour.
 *   - No INVOKE inject is needed. The callDelta gate in CropCatchUp.beginCatchUp keeps catch-up
 *     dormant while the chunk is loaded (normal random ticks keep callDelta small). The
 *     lastGrowthGameTime staleness accumulated during normal play is benign because the callDelta
 *     gate short-circuits before the growth math runs.
 *   - The CropState persists across the block-type transformation when OrganicCompostBlock
 *     ripens into this block; this mixin adopts that existing state on the first tick.
 *
 * @author Mark Gottschling on 2026-05-26
 */
@Mixin(RichSoilBlock.class)
public abstract class RichSoilBlockMixin extends Block {

    // Every randomTick is a potential conversion attempt — no internal probability gate.
    @Unique
    private static final int AVG_GROWTH_TICK_INTERVAL = CropCatchUp.AVG_CALL_TICK_INTERVAL;

    public RichSoilBlockMixin(Properties properties) {
        super(properties);
    }

    @Inject(method = "randomTick", at = @At("HEAD"))
    public void everCropsFD_randomTick(BlockState state, ServerLevel level, BlockPos pos,
                                       RandomSource random, CallbackInfo ci) {
        Optional<CropState> existing = CropRegistry.get(level, pos);
        if (existing.isEmpty()) {
            CropRegistry.put(level, pos, CropCatchUp.createState(level, pos));
            return;
        }
        CropState cropState = existing.get();
        int steps = CropCatchUp.beginCatchUp(level, pos, cropState, AVG_GROWTH_TICK_INTERVAL, false);
        if (steps > 0) {
            // Check whether a vanilla mushroom is sitting on top of this block and convert it.
            // beginCatchUp has already updated timestamps regardless of whether a mushroom is
            // present, so the catch-up interval resets correctly either way.
            BlockPos abovePos = pos.above();
            BlockState aboveState = level.getBlockState(abovePos);
            if (aboveState.is(Blocks.BROWN_MUSHROOM)) {
                level.setBlockAndUpdate(abovePos, ((Block) ModBlocks.BROWN_MUSHROOM_COLONY.get()).defaultBlockState());
            } else if (aboveState.is(Blocks.RED_MUSHROOM)) {
                level.setBlockAndUpdate(abovePos, ((Block) ModBlocks.RED_MUSHROOM_COLONY.get()).defaultBlockState());
            }
        }
        CropRegistry.put(level, pos, cropState);
        // Do NOT cancel — let vanilla continue for its plant-boost behaviour.
    }
}
