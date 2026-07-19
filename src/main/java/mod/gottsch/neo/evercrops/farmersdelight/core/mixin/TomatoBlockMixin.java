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

import mod.gottsch.forge.evercrops.api.CropState;
import mod.gottsch.forge.evercrops.api.EverCropsApi;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.neoforged.neoforge.common.CommonHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vectorwing.farmersdelight.common.block.TomatoBlock;

import java.util.Optional;

/**
 * Catch-up growth for Farmer's Delight TomatoBlock.
 * TomatoBlock overrides CropBlock.randomTick, so it is not covered by EverCrops's CropBlockMixin.
 * Applies to ground-planted tomatoes (ROPELOGGED=false) and hanging/rope-climbing tomatoes
 * (HangingTomatoBlock, introduced in FD 1.3.0). Only old-style ROPELOGGED=true ground blocks
 * are skipped — those are a deprecated back-compat state; HangingTomatoBlock is the live path.
 *
 * HangingTomatoBlock extends TomatoBlock but does NOT register ROPELOGGED in its state
 * definition, yet it inherits TomatoBlock.randomTick unchanged. All getValue(ROPELOGGED) calls
 * must be guarded with hasProperty(ROPELOGGED) to prevent IllegalArgumentException.
 * The guard uses AND (not OR) so that HangingTomatoBlock — which has no ROPELOGGED property —
 * falls through to the catch-up path rather than being skipped.
 *
 * @author Mark Gottschling on 2026-05-03
 */
@Mixin(TomatoBlock.class)
public abstract class TomatoBlockMixin extends CropBlock {

    @Unique
    private static final int AVG_GROWTH_TICK_INTERVAL = 7000;

    public TomatoBlockMixin(Properties properties) {
        super(properties);
    }

    @Inject(method = "randomTick", at = @At("HEAD"), cancellable = true)
    public void everCropsFD_randomTick(BlockState state, ServerLevel level, BlockPos pos,
                                       RandomSource random, CallbackInfo ci) {
        // TomatoBlock.getAgeProperty() returns VINE_AGE, not CropBlock.AGE.
        // Using getAgeProperty() via virtual dispatch ensures we advance the correct property.
        IntegerProperty ageProperty = this.getAgeProperty();
        if (!state.hasProperty(ageProperty)) return;
        // Skip only old-style ROPELOGGED=true ground tomatoes (deprecated back-compat state).
        // HangingTomatoBlock has no ROPELOGGED property → hasProperty() is false → AND short-circuits
        // → falls through to catch-up. Using || here would incorrectly skip HangingTomatoBlock.
        if (state.hasProperty(TomatoBlock.ROPELOGGED) && state.getValue(TomatoBlock.ROPELOGGED)) return;

        Optional<CropState> existing = EverCropsApi.get(level, pos);
        if (existing.isEmpty()) {
            EverCropsApi.put(level, pos, EverCropsApi.createState(level, pos));
            return;
        }
        CropState cropState = existing.get();
        // Harvested in place (e.g. Harvest With Ease) — reset the growth clock so pending
        // catch-up isn't re-applied to the replant.
        if (EverCropsApi.handleInPlaceHarvest(level, pos, cropState, state.getValue(ageProperty))) {
            EverCropsApi.put(level, pos, cropState);
            return;
        }
        int steps = EverCropsApi.beginCatchUp(level, pos, cropState, AVG_GROWTH_TICK_INTERVAL, true);
        boolean grewAny = false;
        if (steps > 0) {
            BlockState currentState = state;
            int maxAge = this.getMaxAge();
            for (int i = 0; i < steps; i++) {
                int age = currentState.getValue(ageProperty);
                if (age < maxAge && CommonHooks.canCropGrow(level, pos, currentState, true)) {
                    currentState = currentState.setValue(ageProperty, age + 1);
                    level.setBlock(pos, currentState, 2);
                    CommonHooks.fireCropGrowPost(level, pos, currentState);
                    grewAny = true;
                }
            }
        }
        EverCropsApi.put(level, pos, cropState);
        // Catch-up advanced the vine this tick. Skip vanilla's own randomTick so it can't
        // overwrite the caught-up age nor double-write the growth timestamp. Fruit growth /
        // rope climbing still runs on the next natural random tick once the vine is mature.
        if (grewAny) {
            ci.cancel();
        }
    }

    @Inject(method = "randomTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"))
    public void everCropsFD_randomTick_setBlock(BlockState state, ServerLevel level, BlockPos pos,
                                                RandomSource random, CallbackInfo ci) {
        // TomatoBlock.VINE_AGE = BlockStateProperties.AGE_3, NOT CropBlock.AGE (AGE_7) — different instances.
        if (!state.hasProperty(TomatoBlock.VINE_AGE)) return;
        if (state.hasProperty(TomatoBlock.ROPELOGGED) && state.getValue(TomatoBlock.ROPELOGGED)) return;
        Optional<CropState> cropState = EverCropsApi.get(level, pos);
        if (cropState.isPresent()) {
            cropState.get().setLastGrowthGameTime(level.getGameTime())
                    .setLastGrowthLightLevel(level.getRawBrightness(pos, 0));
            EverCropsApi.put(level, pos, cropState.get());
        } else {
            EverCropsApi.put(level, pos, EverCropsApi.createState(level, pos));
        }
    }
}
