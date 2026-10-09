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
import dev.galacticraft.mod.content.GCFluids;
import dev.galacticraft.mod.machine.GCMachineStatuses;
import dev.galacticraft.mod.screen.GCMenuTypes;
import dev.galacticraft.mod.util.FluidUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

/**
 * Liquefies gases like the Galacticraft 4 gas liquefier: methane becomes rocket fuel, oxygen
 * becomes liquid oxygen, nitrogen liquid nitrogen and argon liquid argon. Every {@link #PROCESS_TICKS} ticks six millibuckets of gas become three of liquid.
 */
public class GasLiquefierBlockEntity extends MachineBlockEntity {
    public static final int CHARGE_SLOT = 0;
    public static final int GAS_INPUT_SLOT = 1;
    public static final int LIQUID_OUTPUT_SLOT = 2;
    public static final int GAS_TANK = 0;
    public static final int LIQUID_TANK = 1;

    public static final long ENERGY_CONSUMPTION = 60;
    public static final int PROCESS_TICKS = 3;
    private static final long MILLIBUCKET = FluidUtil.bucketsToDroplets(1) / 1000;
    @VisibleForTesting
    public static final long GAS_PER_OPERATION = 6 * MILLIBUCKET;
    @VisibleForTesting
    public static final long LIQUID_PER_OPERATION = 3 * MILLIBUCKET;

    private static final StorageSpec SPEC = StorageSpec.of(
            MachineItemStorage.spec(
                    ItemResourceSlot.builder(TransferType.TRANSFER)
                            .pos(8, 62)
                            .capacity(1)
                            .filter(ResourceFilters.CAN_EXTRACT_ENERGY)
                            .icon(Pair.of(InventoryMenu.BLOCK_ATLAS, Constant.SlotSprite.ENERGY)),
                    ItemResourceSlot.builder(TransferType.PROCESSING)
                            .pos(125, 62)
                            .capacity(1)
                            .filter(ResourceFilters.or(ResourceFilters.canExtractFluid(Gases.METHANE), ResourceFilters.or(ResourceFilters.canExtractFluid(Gases.OXYGEN), ResourceFilters.or(ResourceFilters.canExtractFluid(Gases.NITROGEN), ResourceFilters.canExtractFluid(Gases.ARGON))))),
                    ItemResourceSlot.builder(TransferType.PROCESSING)
                            .pos(152, 62)
                            .capacity(1)
                            .filter(ResourceFilters.or(ResourceFilters.canInsertFluid(GCFluids.FUEL), ResourceFilters.or(ResourceFilters.canInsertFluid(GCFluids.LIQUID_OXYGEN), ResourceFilters.or(ResourceFilters.canInsertFluid(GCFluids.LIQUID_NITROGEN), ResourceFilters.canInsertFluid(GCFluids.LIQUID_ARGON)))))
            ),
            MachineEnergyStorage.spec(
                    Galacticraft.CONFIG.machineEnergyStorageSize(),
                    ENERGY_CONSUMPTION * 2,
                    0
            ),
            MachineFluidStorage.spec(
                    FluidResourceSlot.builder(TransferType.INPUT)
                            .pos(125, 8)
                            .capacity(FluidUtil.bucketsToDroplets(4))
                            .filter((fluid, components) -> liquidOf(fluid) != null),
                    FluidResourceSlot.builder(TransferType.OUTPUT)
                            .pos(152, 8)
                            .capacity(FluidUtil.bucketsToDroplets(2))
                            .filter((fluid, components) -> fluid == GCFluids.FUEL || fluid == GCFluids.LIQUID_OXYGEN || fluid == GCFluids.LIQUID_NITROGEN || fluid == GCFluids.LIQUID_ARGON)
            )
    );

    private final FluidSource fluidSource = new FluidSource(this);
    private int processTicks = 0;

    public GasLiquefierBlockEntity(BlockPos pos, BlockState state) {
        super(GCBlockEntityTypes.GAS_LIQUEFIER, pos, state, SPEC);
    }

    /** The liquid a gas turns into, or null if this machine cannot liquefy it. */
    @VisibleForTesting
    public static @Nullable Fluid liquidOf(@Nullable Fluid gas) {
        if (gas == Gases.METHANE) return GCFluids.FUEL;
        if (gas == Gases.OXYGEN) return GCFluids.LIQUID_OXYGEN;
        if (gas == Gases.NITROGEN) return GCFluids.LIQUID_NITROGEN;
        if (gas == Gases.ARGON) return GCFluids.LIQUID_ARGON;
        return null;
    }

    @Override
    protected void tickConstant(@NotNull ServerLevel level, @NotNull BlockPos pos, @NotNull BlockState state, @NotNull ProfilerFiller profiler) {
        super.tickConstant(level, pos, state, profiler);
        this.chargeFromSlot(CHARGE_SLOT);
        this.takeFluidFromSlot(GAS_INPUT_SLOT, GAS_TANK);
        this.drainFluidToSlot(LIQUID_OUTPUT_SLOT, LIQUID_TANK);
    }

    @Override
    protected @NotNull MachineStatus tick(@NotNull ServerLevel level, @NotNull BlockPos pos, @NotNull BlockState state, @NotNull ProfilerFiller profiler) {
        profiler.push("transfer");
        this.fluidSource.trySpreadFluids(level, pos, state);
        profiler.pop();

        FluidResourceSlot gas = this.fluidStorage().slot(GAS_TANK);
        FluidResourceSlot liquid = this.fluidStorage().slot(LIQUID_TANK);
        Fluid product = liquidOf(gas.getResource());
        if (gas.getAmount() < GAS_PER_OPERATION || product == null) {
            this.processTicks = 0;
            return GCMachineStatuses.NOT_ENOUGH_GAS;
        }
        if (liquid.tryInsert(product, LIQUID_PER_OPERATION) < LIQUID_PER_OPERATION) {
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
            gas.extract(gas.getResource(), GAS_PER_OPERATION);
            liquid.insert(product, LIQUID_PER_OPERATION);
        }
        profiler.pop();
        return GCMachineStatuses.LIQUEFYING;
    }

    @Override
    public @Nullable MachineMenu<? extends MachineBlockEntity> createMenu(int syncId, Inventory inventory, Player player) {
        return new MachineMenu<>(GCMenuTypes.GAS_LIQUEFIER, syncId, player, this);
    }
}
