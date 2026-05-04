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
package mod.gottsch.forge.evercrops.farmersdelight.core.mixin;

import mod.gottsch.forge.evercrops.core.persistence.CropCatchUp;
import mod.gottsch.forge.evercrops.core.persistence.CropRegistry;
import mod.gottsch.forge.evercrops.core.persistence.CropState;
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
 * Only applies to ground-planted tomatoes (ROPELOGGED=false); rope variants have
 * different multi-block mechanics and are skipped.
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

    @Inject(method = "randomTick", at = @At("HEAD"))
    public void everCropsFD_randomTick(BlockState state, ServerLevel level, BlockPos pos,
                                       RandomSource random, CallbackInfo ci) {
        // TomatoBlock.getAgeProperty() returns VINE_AGE, not CropBlock.AGE.
        // Using getAgeProperty() via virtual dispatch ensures we advance the correct property.
        IntegerProperty ageProperty = this.getAgeProperty();
        if (!state.hasProperty(ageProperty)) return;
        if (state.getValue(TomatoBlock.ROPELOGGED)) return;

        Optional<CropState> existing = CropRegistry.get(level, pos);
        if (existing.isEmpty()) {
            CropRegistry.put(level, pos, CropCatchUp.createState(level, pos));
            return;
        }
        CropState cropState = existing.get();
        int steps = CropCatchUp.beginCatchUp(level, pos, cropState, AVG_GROWTH_TICK_INTERVAL, true);
        if (steps > 0) {
            BlockState currentState = state;
            int maxAge = this.getMaxAge();
            for (int i = 0; i < steps; i++) {
                int age = currentState.getValue(ageProperty);
                if (age < maxAge && CommonHooks.canCropGrow(level, pos, currentState, true)) {
                    currentState = currentState.setValue(ageProperty, age + 1);
                    level.setBlock(pos, currentState, 2);
                    CommonHooks.fireCropGrowPost(level, pos, currentState);
                }
            }
        }
        CropRegistry.put(level, pos, cropState);
    }

    @Inject(method = "randomTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"))
    public void everCropsFD_randomTick_setBlock(BlockState state, ServerLevel level, BlockPos pos,
                                                RandomSource random, CallbackInfo ci) {
        if (!state.hasProperty(CropBlock.AGE)) return;
        if (state.getValue(TomatoBlock.ROPELOGGED)) return;
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
