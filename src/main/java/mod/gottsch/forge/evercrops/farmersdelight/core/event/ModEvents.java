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
package mod.gottsch.forge.evercrops.farmersdelight.core.event;

import mod.gottsch.forge.evercrops.farmersdelight.EverCropsFD;
import mod.gottsch.forge.evercrops.core.persistence.CropCatchUp;
import mod.gottsch.forge.evercrops.core.persistence.CropRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import vectorwing.farmersdelight.common.block.BuddingBushBlock;
import vectorwing.farmersdelight.common.block.RiceBlock;
import vectorwing.farmersdelight.common.block.TomatoBlock;

/**
 * Game-bus event listeners for EverCrops: Farmer's Delight.
 *
 * @author Mark Gottschling on 2026-05-03
 */
@EventBusSubscriber(modid = EverCropsFD.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public class ModEvents {

    @SubscribeEvent
    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel().isClientSide()) return;
        BlockState state = event.getPlacedBlock();
        if (!isTracked(state)) return;
        ServerLevel serverLevel = (ServerLevel) event.getLevel();
        BlockPos pos = event.getPos();
        CropRegistry.put(serverLevel, pos, CropCatchUp.createState(serverLevel, pos));
    }

    @SubscribeEvent
    public static void onBlockBroken(BlockEvent.BreakEvent event) {
        if (event.getLevel().isClientSide()) return;
        BlockState state = event.getState();
        if (isTracked(state)) {
            CropRegistry.remove((ServerLevel) event.getLevel(), event.getPos());
        }
    }

    /**
     * Tracks blocks that have a mixin in this mod. Note:
     * - CabbageBlock, OnionBlock, RicePaniclesBlock extend CropBlock without overriding
     *   randomTick, so they are already tracked by EverCrops's CropBlockMixin/ModEvents.
     * - TomatoBlock overrides randomTick, so it needs its own tracking here.
     *   Rope-logged tomatoes are excluded — their VINE_AGE mechanic is left to vanilla.
     */
    private static boolean isTracked(BlockState state) {
        Block block = state.getBlock();
        if (block instanceof TomatoBlock
                && state.hasProperty(CropBlock.AGE)
                && !state.getValue(TomatoBlock.ROPELOGGED)) return true;
        if (block instanceof BuddingBushBlock
                && state.hasProperty(BuddingBushBlock.AGE)) return true;
        if (block instanceof RiceBlock
                && state.hasProperty(RiceBlock.AGE)) return true;
        return false;
    }
}
