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
import dev.galacticraft.api.universe.celestialbody.CelestialBody;
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
import dev.galacticraft.mod.content.item.GCItems;
import dev.galacticraft.mod.machine.GCMachineStatuses;
import dev.galacticraft.mod.screen.GCMenuTypes;
import dev.galacticraft.mod.util.FluidUtil;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.Comparator;

/**
 * Combines hydrogen with carbon into methane, like the Galacticraft 4 methane synthesizer. Carbon
 * comes from carbon fragments or from carbon dioxide drawn out of the atmosphere through an
 * atmospheric valve, which only works where carbon dioxide is one of the three main atmospheric gases.
 */
public class MethaneSynthesizerBlockEntity extends MachineBlockEntity {
    public static final int CHARGE_SLOT = 0;
    public static final int HYDROGEN_INPUT_SLOT = 1;
    public static final int VALVE_SLOT = 2;
    public static final int CARBON_SLOT = 3;
    public static final int METHANE_OUTPUT_SLOT = 4;
    public static final int HYDROGEN_TANK = 0;
    public static final int CARBON_DIOXIDE_TANK = 1;
    public static final int METHANE_TANK = 2;

    public static final long ENERGY_CONSUMPTION = 45;
    public static final int PROCESS_TICKS = 3;
    /** Number of operations one carbon fragment lasts for. */
    public static final int OPERATIONS_PER_FRAGMENT = 40;
    private static final long MILLIBUCKET = FluidUtil.bucketsToDroplets(1) / 1000;
    @VisibleForTesting
    public static final long HYDROGEN_PER_OPERATION = 16 * MILLIBUCKET;
    @VisibleForTesting
    public static final long CARBON_DIOXIDE_PER_OPERATION = MILLIBUCKET;
    @VisibleForTesting
    public static final long METHANE_PER_OPERATION = 2 * MILLIBUCKET;
    @VisibleForTesting
    public static final long CARBON_DIOXIDE_INTAKE = 4 * MILLIBUCKET;

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
                            .filter(ResourceFilters.canExtractFluid(Gases.HYDROGEN)),
                    ItemResourceSlot.builder(TransferType.PROCESSING)
                            .pos(125, 62)
                            .capacity(1)
                            .filter(ResourceFilters.ofResource(GCItems.ATMOSPHERIC_VALVE)),
                    ItemResourceSlot.builder(TransferType.INPUT)
                            .pos(71, 62)
                            .filter(ResourceFilters.ofResource(GCItems.CARBON_FRAGMENTS)),
                    ItemResourceSlot.builder(TransferType.PROCESSING)
                            .pos(152, 62)
                            .capacity(1)
                            .filter(ResourceFilters.canInsertFluid(Gases.METHANE))
            ),
            MachineEnergyStorage.spec(
                    Galacticraft.CONFIG.machineEnergyStorageSize(),
                    ENERGY_CONSUMPTION * 2,
                    0
            ),
            MachineFluidStorage.spec(
                    FluidResourceSlot.builder(TransferType.INPUT)
                            .pos(98, 8)
                            .capacity(FluidUtil.bucketsToDroplets(4))
                            .filter(ResourceFilters.ofResource(Gases.HYDROGEN)),
                    FluidResourceSlot.builder(TransferType.INPUT)
                            .pos(125, 8)
                            .capacity(FluidUtil.bucketsToDroplets(2))
                            .filter(ResourceFilters.ofResource(Gases.CARBON_DIOXIDE)),
                    FluidResourceSlot.builder(TransferType.OUTPUT)
                            .pos(152, 8)
                            .capacity(FluidUtil.bucketsToDroplets(2))
                            .filter(ResourceFilters.ofResource(Gases.METHANE))
            )
    );

    private final FluidSource fluidSource = new FluidSource(this);
    private int processTicks = 0;
    /** Operations left from the carbon fragment currently being used up. */
    private int fragmentOperations = 0;

    public MethaneSynthesizerBlockEntity(BlockPos pos, BlockState state) {
        super(GCBlockEntityTypes.METHANE_SYNTHESIZER, pos, state, SPEC);
    }

    @Override
    protected void tickConstant(@NotNull ServerLevel level, @NotNull BlockPos pos, @NotNull BlockState state, @NotNull ProfilerFiller profiler) {
        super.tickConstant(level, pos, state, profiler);
        this.chargeFromSlot(CHARGE_SLOT);
        this.takeFluidFromSlot(HYDROGEN_INPUT_SLOT, HYDROGEN_TANK, Gases.HYDROGEN);
        this.drainFluidToSlot(METHANE_OUTPUT_SLOT, METHANE_TANK);

        if (!this.itemStorage().slot(VALVE_SLOT).isEmpty() && canDrawCarbonDioxide(level, pos)) {
            this.fluidStorage().slot(CARBON_DIOXIDE_TANK).insert(Gases.CARBON_DIOXIDE, CARBON_DIOXIDE_INTAKE);
        }
    }

    @Override
    protected @NotNull MachineStatus tick(@NotNull ServerLevel level, @NotNull BlockPos pos, @NotNull BlockState state, @NotNull ProfilerFiller profiler) {
        profiler.push("transfer");
        this.fluidSource.trySpreadFluids(level, pos, state);
        profiler.pop();

        FluidResourceSlot hydrogen = this.fluidStorage().slot(HYDROGEN_TANK);
        FluidResourceSlot carbonDioxide = this.fluidStorage().slot(CARBON_DIOXIDE_TANK);
        FluidResourceSlot methane = this.fluidStorage().slot(METHANE_TANK);
        if (hydrogen.getAmount() < HYDROGEN_PER_OPERATION) {
            this.processTicks = 0;
            return GCMachineStatuses.NOT_ENOUGH_HYDROGEN;
        }
        if (this.fragmentOperations == 0 && this.itemStorage().slot(CARBON_SLOT).isEmpty()
                && carbonDioxide.getAmount() < CARBON_DIOXIDE_PER_OPERATION) {
            this.processTicks = 0;
            return GCMachineStatuses.NOT_ENOUGH_CARBON;
        }
        if (methane.isFull()) {
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
            if (this.consumeCarbon(carbonDioxide)) {
                hydrogen.extract(Gases.HYDROGEN, HYDROGEN_PER_OPERATION);
                methane.insert(Gases.METHANE, METHANE_PER_OPERATION);
            }
        }
        profiler.pop();
        return GCMachineStatuses.SYNTHESIZING;
    }

    /** Uses carbon from a carbon fragment if one is available, otherwise from the carbon dioxide tank. */
    private boolean consumeCarbon(FluidResourceSlot carbonDioxide) {
        if (this.fragmentOperations == 0 && this.itemStorage().slot(CARBON_SLOT).consumeOne() != null) {
            this.fragmentOperations = OPERATIONS_PER_FRAGMENT;
        }
        if (this.fragmentOperations > 0) {
            this.fragmentOperations--;
            return true;
        }
        return carbonDioxide.extract(Gases.CARBON_DIOXIDE, CARBON_DIOXIDE_PER_OPERATION) > 0;
    }

    /** Carbon dioxide can be drawn in through open air on worlds where it is a main atmospheric gas. */
    private static boolean canDrawCarbonDioxide(Level level, BlockPos pos) {
        BlockPos above = pos.above();
        return level.getBlockState(above).isAir() && !level.isBreathable(above) && hasCarbonDioxideAtmosphere(level);
    }

    @VisibleForTesting
    public static boolean hasCarbonDioxideAtmosphere(Level level) {
        Holder<CelestialBody<?, ?>> body = level.galacticraft$getCelestialBody();
        if (body == null) return false;
        return isMainGas(body.value().atmosphere().composition(), Gases.CARBON_DIOXIDE_ID);
    }

    /** True if the gas is one of the three most abundant gases in the composition. */
    @VisibleForTesting
    public static boolean isMainGas(Object2DoubleMap<ResourceKey<Fluid>> composition, ResourceLocation gas) {
        return composition.object2DoubleEntrySet().stream()
                .sorted(Comparator.comparingDouble(Object2DoubleMap.Entry<ResourceKey<Fluid>>::getDoubleValue).reversed())
                .limit(3)
                .anyMatch(entry -> entry.getKey().location().equals(gas));
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider lookup) {
        super.loadAdditional(tag, lookup);
        this.fragmentOperations = tag.getInt(Constant.Nbt.CARBON_FRAGMENT_OPERATIONS);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider lookup) {
        super.saveAdditional(tag, lookup);
        tag.putInt(Constant.Nbt.CARBON_FRAGMENT_OPERATIONS, this.fragmentOperations);
    }

    @Override
    public @Nullable MachineMenu<? extends MachineBlockEntity> createMenu(int syncId, Inventory inventory, Player player) {
        return new MachineMenu<>(GCMenuTypes.METHANE_SYNTHESIZER, syncId, player, this);
    }
}
