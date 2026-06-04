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
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.CommonHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vectorwing.farmersdelight.common.block.MushroomColonyBlock;
import vectorwing.farmersdelight.common.tag.ModTags;

import java.util.Optional;

/**
 * Catch-up growth for Farmer's Delight MushroomColonyBlock (COLONY_AGE 0-3).
 *
 * Vanilla's randomTick gates each growth attempt with {@code random.nextInt(4) == 0}
 * (25% chance per tick). The average number of game ticks between stage advances is therefore
 * {@code AVG_CALL_TICK_INTERVAL * 4 = 5400}.
 *
 * Catch-up mirrors the pattern used by the other FD mixins:
 *   - HEAD inject computes elapsed steps via CropCatchUp and loops the age advance,
 *     re-checking ground conditions and the canCropGrow hook each iteration.
 *   - INVOKE inject keeps lastGrowthGameTime current when vanilla itself grows the colony
 *     (i.e. when no catch-up was applied this tick).
 *
 * Light is NOT required — vanilla mushroom colonies grow regardless of light level.
 * The probability gate is already baked into AVG_GROWTH_TICK_INTERVAL rather than being
 * re-rolled per catch-up step.
 *
 * @author Mark Gottschling on 2026-05-26
 */
@Mixin(MushroomColonyBlock.class)
public abstract class MushroomColonyBlockMixin extends BushBlock {

    // 1/4 probability gate inside vanilla randomTick → average 4 call intervals per growth step.
    @Unique
    private static final int AVG_GROWTH_TICK_INTERVAL = CropCatchUp.AVG_CALL_TICK_INTERVAL * 4; // 5400

    public MushroomColonyBlockMixin(Properties properties) {
        super(properties);
    }

    @Inject(method = "randomTick", at = @At("HEAD"), cancellable = true)
    public void everCropsFD_randomTick(BlockState state, ServerLevel level, BlockPos pos,
                                       RandomSource random, CallbackInfo ci) {
        if (!state.hasProperty(MushroomColonyBlock.COLONY_AGE)) return;

        Optional<CropState> existing = CropRegistry.get(level, pos);
        if (existing.isEmpty()) {
            CropRegistry.put(level, pos, CropCatchUp.createState(level, pos));
            return;
        }
        CropState cropState = existing.get();
        int steps = CropCatchUp.beginCatchUp(level, pos, cropState, AVG_GROWTH_TICK_INTERVAL, false);
        boolean grewAny = false;
        if (steps > 0) {
            MushroomColonyBlock colonyBlock = (MushroomColonyBlock)(Object) this;
            int maxAge = colonyBlock.getMaxAge();
            BlockState currentState = state;
            for (int i = 0; i < steps; i++) {
                int age = currentState.getValue(MushroomColonyBlock.COLONY_AGE);
                if (age >= maxAge) break;
                // Re-check ground tag each step — player may have replaced the block below.
                BlockState groundState = level.getBlockState(pos.below());
                if (!groundState.is(ModTags.Blocks.MUSHROOM_COLONY_GROWABLE_ON)) break;
                // Probability gate is baked into AVG_GROWTH_TICK_INTERVAL; pass true as default
                // so canCropGrow fires event hooks (allows other mods to veto growth).
                if (!CommonHooks.canCropGrow(level, pos, currentState, true)) break;
                currentState = currentState.setValue(MushroomColonyBlock.COLONY_AGE, age + 1);
                level.setBlock(pos, currentState, 2);
                CommonHooks.fireCropGrowPost(level, pos, currentState);
                grewAny = true;
            }
        }
        CropRegistry.put(level, pos, cropState);
        // Catch-up advanced the colony this tick. Skip vanilla's own randomTick so it can't
        // overwrite the caught-up age (computed from the pre-catch-up state) nor double-write
        // the growth timestamp via the setBlock inject below.
        if (grewAny) {
            ci.cancel();
        }
    }

    @Inject(method = "randomTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"))
    public void everCropsFD_randomTick_setBlock(BlockState state, ServerLevel level, BlockPos pos,
                                                RandomSource random, CallbackInfo ci) {
        if (!state.hasProperty(MushroomColonyBlock.COLONY_AGE)) return;
        Optional<CropState> cropState = CropRegistry.get(level, pos);
        if (cropState.isPresent()) {
            cropState.get().setLastGrowthGameTime(level.getGameTime())
                    .setLastGrowthLightLevel(level.getRawBrightness(pos, 0));
            CropRegistry.put(level, pos, cropState.get());
        } else {
            CropRegistry.put(level, pos, CropCatchUp.createState(level, pos));
        }
    }
}
