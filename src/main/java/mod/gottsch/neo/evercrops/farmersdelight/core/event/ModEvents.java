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
package mod.gottsch.neo.evercrops.farmersdelight.core.event;

import mod.gottsch.neo.evercrops.farmersdelight.EverCropsFD;
import mod.gottsch.forge.evercrops.api.EverCropsApi;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import vectorwing.farmersdelight.common.block.BuddingBushBlock;
import vectorwing.farmersdelight.common.block.MushroomColonyBlock;
import vectorwing.farmersdelight.common.block.OrganicCompostBlock;
import vectorwing.farmersdelight.common.block.RiceBlock;
import vectorwing.farmersdelight.common.block.RichSoilBlock;
import vectorwing.farmersdelight.common.block.TomatoBlock;

/**
 * Game-bus event listeners for EverCrops: Farmer's Delight.
 *
 * @author Mark Gottschling on 2026-05-03
 */
@EventBusSubscriber(modid = EverCropsFD.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public class ModEvents {

    /**
     * Explicitly register this mod's crop predicate with the shared cleanup set.
     * Called from {@link mod.gottsch.neo.evercrops.farmersdelight.EverCropsFD} constructor
     * so registration is guaranteed before any game events fire.
     */
    public static void registerPredicates() {
        EverCropsApi.registerCleanupPredicate(ModEvents::isTracked);
    }

    @SubscribeEvent
    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel().isClientSide()) return;
        BlockState state = event.getPlacedBlock();
        if (!isTracked(state)) return;
        ServerLevel serverLevel = (ServerLevel) event.getLevel();
        BlockPos pos = event.getPos();
        EverCropsApi.put(serverLevel, pos, EverCropsApi.createState(serverLevel, pos));
    }

    @SubscribeEvent
    public static void onBlockBroken(BlockEvent.BreakEvent event) {
        if (event.getLevel().isClientSide()) return;
        BlockState state = event.getState();
        if (isTracked(state)) {
            EverCropsApi.remove((ServerLevel) event.getLevel(), event.getPos());
        }
    }

    /**
     * Tracks blocks that have a mixin in this mod. Note:
     * - CabbageBlock, OnionBlock, RicePaniclesBlock extend CropBlock without overriding
     *   randomTick, so they are already tracked by EverCrops's CropBlockMixin/ModEvents.
     * - TomatoBlock overrides randomTick, so it needs its own tracking here.
     *   Both ground tomatoes (ROPELOGGED=false) and HangingTomatoBlock (FD 1.3+, no ROPELOGGED)
     *   are tracked. Only old-style ROPELOGGED=true states are excluded — that is a deprecated
     *   back-compat state; HangingTomatoBlock is the live rope-climbing path.
     * - OrganicCompostBlock: COMPOSTING property (0-7 → Rich Soil transformation).
     * - RichSoilBlock: no age property; tracked for mushroom-to-colony catch-up conversion.
     * - MushroomColonyBlock: COLONY_AGE property (0-3).
     */
    private static boolean isTracked(BlockState state) {
        Block block = state.getBlock();
        // Track ground tomatoes (ROPELOGGED=false) and HangingTomatoBlock (no ROPELOGGED property).
        // Skip only old-style ROPELOGGED=true blocks; those are a deprecated back-compat state.
        // HangingTomatoBlock is placed programmatically (climbRopeAbove/setBlockAndUpdate), so
        // EntityPlaceEvent does not fire for it — the mixin's first-tick fallback handles that case.
        if (block instanceof TomatoBlock
                && state.hasProperty(TomatoBlock.VINE_AGE)
                && (!state.hasProperty(TomatoBlock.ROPELOGGED) || !state.getValue(TomatoBlock.ROPELOGGED))) return true;
        if (block instanceof BuddingBushBlock
                && state.hasProperty(BuddingBushBlock.AGE)) return true;
        if (block instanceof RiceBlock
                && state.hasProperty(RiceBlock.AGE)) return true;
        if (block instanceof OrganicCompostBlock
                && state.hasProperty(OrganicCompostBlock.COMPOSTING)) return true;
        if (block instanceof RichSoilBlock) return true;
        if (block instanceof MushroomColonyBlock
                && state.hasProperty(MushroomColonyBlock.COLONY_AGE)) return true;
        return false;
    }
}
