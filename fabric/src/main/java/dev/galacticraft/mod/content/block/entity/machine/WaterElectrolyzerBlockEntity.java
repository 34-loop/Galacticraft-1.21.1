/*
 * Copyright (c) 2019-2026 Team Galacticraft
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package dev.galacticraft.mod.content.block.entity.machine;

import com.mojang.datafixers.util.Pair;
import dev.galacticraft.api.gas.Gases;
import dev.galacticraft.machinelib.api.block.entity.MachineBlockEntity;
import dev.galacticraft.machinelib.api.filter.ResourceFilters;
import dev.galacticraft.machinelib.api.machine.MachineStatus;
import dev.galacticraft.machinelib.api.machine.MachineStatuses;
import dev.galacticraft.machinelib.api.menu.MachineMenu;
import dev.galacticraft.machinelib.api.storage.MachineEnergyStorage;
import dev.galacticraft.machinelib.api.storage.MachineFluidStorage;
import dev.galacticraft.machinelib.api.storage.MachineItemStorage;
import dev.galacticraft.machinelib.api.storage.StorageSpec;
import dev.galacticraft.machinelib.api.storage.slot.FluidResourceSlot;
import dev.galacticraft.machinelib.api.storage.slot.ItemResourceSlot;
import dev.galacticraft.machinelib.api.transfer.TransferType;
import dev.galacticraft.machinelib.api.util.FluidSource;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.Galacticraft;
import dev.galacticraft.mod.content.GCBlockEntityTypes;
import dev.galacticraft.mod.machine.GCMachineStatuses;
import dev.galacticraft.mod.screen.GCMenuTypes;
import dev.galacticraft.mod.util.FluidUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

/**
 * Splits water into oxygen and hydrogen, like the Galacticraft 4 water electrolyzer: every
 * {@link #PROCESS_TICKS} ticks one millibucket of water becomes two of oxygen and four of hydrogen.
 */
public class WaterElectrolyzerBlockEntity extends MachineBlockEntity {
    public static final int CHARGE_SLOT = 0;
    public static final int WATER_INPUT_SLOT = 1;
    public static final int OXYGEN_OUTPUT_SLOT = 2;
    public static final int HYDROGEN_OUTPUT_SLOT = 3;
    public static final int WATER_TANK = 0;
    public static final int OXYGEN_TANK = 1;
    public static final int HYDROGEN_TANK = 2;

    public static final long ENERGY_CONSUMPTION = 60;
    public static final int PROCESS_TICKS = 3;
    @VisibleForTesting
    public static final long MAX_CAPACITY = FluidUtil.bucketsToDroplets(4);
    private static final long MILLIBUCKET = FluidUtil.bucketsToDroplets(1) / 1000;
    @VisibleForTesting
    public static final long WATER_PER_OPERATION = MILLIBUCKET;
    @VisibleForTesting
    public static final long OXYGEN_PER_OPERATION = 2 * MILLIBUCKET;
    @VisibleForTesting
    public static final long HYDROGEN_PER_OPERATION = 4 * MILLIBUCKET;

    private static final StorageSpec SPEC = StorageSpec.of(
            MachineItemStorage.spec(
                    ItemResourceSlot.builder(TransferType.TRANSFER)
                            .pos(8, 62)
                            .capacity(1)
                            .filter(ResourceFilters.CAN_EXTRACT_ENERGY)
                            .icon(Pair.of(InventoryMenu.BLOCK_ATLAS, Constant.SlotSprite.ENERGY)),
                    ItemResourceSlot.builder(TransferType.PROCESSING)
                            .pos(98, 62)
                            .capacity(1)
                            .filter(ResourceFilters.canExtractFluid(Fluids.WATER))
                            .icon(Pair.of(InventoryMenu.BLOCK_ATLAS, Constant.SlotSprite.BUCKET)),
                    ItemResourceSlot.builder(TransferType.PROCESSING)
                            .pos(125, 62)
                            .capacity(1)
                            .filter(ResourceFilters.canInsertFluid(Gases.OXYGEN)),
                    ItemResourceSlot.builder(TransferType.PROCESSING)
                            .pos(152, 62)
                            .capacity(1)
                            .filter(ResourceFilters.canInsertFluid(Gases.HYDROGEN))
            ),
            MachineEnergyStorage.spec(
                    Galacticraft.CONFIG.machineEnergyStorageSize(),
                    ENERGY_CONSUMPTION * 2,
                    0
            ),
            MachineFluidStorage.spec(
                    FluidResourceSlot.builder(TransferType.INPUT)
                            .pos(98, 8)
                            .capacity(MAX_CAPACITY)
                            .filter(ResourceFilters.ofResource(Fluids.WATER)),
                    FluidResourceSlot.builder(TransferType.OUTPUT)
                            .pos(125, 8)
                            .capacity(MAX_CAPACITY)
                            .filter(ResourceFilters.ofResource(Gases.OXYGEN)),
                    FluidResourceSlot.builder(TransferType.OUTPUT)
                            .pos(152, 8)
                            .capacity(MAX_CAPACITY)
                            .filter(ResourceFilters.ofResource(Gases.HYDROGEN))
            )
    );

    private final FluidSource fluidSource = new FluidSource(this);
    private int processTicks = 0;

    public WaterElectrolyzerBlockEntity(BlockPos pos, BlockState state) {
        super(GCBlockEntityTypes.WATER_ELECTROLYZER, pos, state, SPEC);
    }

    @Override
    protected void tickConstant(@NotNull ServerLevel level, @NotNull BlockPos pos, @NotNull BlockState state, @NotNull ProfilerFiller profiler) {
        super.tickConstant(level, pos, state, profiler);
        this.chargeFromSlot(CHARGE_SLOT);

        ItemResourceSlot waterInput = this.itemStorage().slot(WATER_INPUT_SLOT);
        Item previousItem = waterInput.getResource();
        DataComponentPatch previousComponents = waterInput.getComponents();
        long previousAmount = waterInput.getAmount();
        long previousModifications = waterInput.getModifications();

        this.takeFluidFromSlot(WATER_INPUT_SLOT, WATER_TANK, Fluids.WATER);

        // NeoForge fluid handlers replace the slot contents with a modified copy; make sure the
        // menu notices, as the refinery does for oil buckets.
        if (waterInput.getModifications() == previousModifications
                && (waterInput.getResource() != previousItem
                || waterInput.getAmount() != previousAmount
                || !waterInput.getComponents().equals(previousComponents))) {
            waterInput.markModified();
        }

        this.drainFluidToSlot(OXYGEN_OUTPUT_SLOT, OXYGEN_TANK);
        this.drainFluidToSlot(HYDROGEN_OUTPUT_SLOT, HYDROGEN_TANK);
    }

    @Override
    protected @NotNull MachineStatus tick(@NotNull ServerLevel level, @NotNull BlockPos pos, @NotNull BlockState state, @NotNull ProfilerFiller profiler) {
        profiler.push("transfer");
        this.fluidSource.trySpreadFluids(level, pos, state);
        profiler.pop();

        FluidResourceSlot water = this.fluidStorage().slot(WATER_TANK);
        FluidResourceSlot oxygen = this.fluidStorage().slot(OXYGEN_TANK);
        FluidResourceSlot hydrogen = this.fluidStorage().slot(HYDROGEN_TANK);
        if (water.isEmpty()) {
            this.processTicks = 0;
            return GCMachineStatuses.NOT_ENOUGH_WATER;
        }
        if (oxygen.isFull() && hydrogen.isFull()) {
            this.processTicks = 0;
            return MachineStatuses.OUTPUT_FULL;
        }
        if (!this.energyStorage().canExtract(ENERGY_CONSUMPTION)) {
            return MachineStatuses.NOT_ENOUGH_ENERGY;
        }

        profiler.push("transaction");
        this.energyStorage().extract(ENERGY_CONSUMPTION);
        if (++this.processTicks >= PROCESS_TICKS) {
            this.processTicks = 0;
            if (water.extract(Fluids.WATER, WATER_PER_OPERATION) > 0) {
                // Like the original machine, gas that does not fit in a full tank is lost.
                oxygen.insert(Gases.OXYGEN, OXYGEN_PER_OPERATION);
                hydrogen.insert(Gases.HYDROGEN, HYDROGEN_PER_OPERATION);
            }
        }
        profiler.pop();
        return GCMachineStatuses.ELECTROLYZING;
    }

    @Override
    public @Nullable MachineMenu<? extends MachineBlockEntity> createMenu(int syncId, Inventory inventory, Player player) {
        return new MachineMenu<>(GCMenuTypes.WATER_ELECTROLYZER, syncId, player, this);
    }
}
