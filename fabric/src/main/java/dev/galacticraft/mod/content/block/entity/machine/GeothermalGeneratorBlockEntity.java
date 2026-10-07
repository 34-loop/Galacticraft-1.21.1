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
import dev.galacticraft.machinelib.api.block.entity.MachineBlockEntity;
import dev.galacticraft.machinelib.api.filter.ResourceFilters;
import dev.galacticraft.machinelib.api.machine.MachineStatus;
import dev.galacticraft.machinelib.api.machine.MachineStatuses;
import dev.galacticraft.machinelib.api.menu.MachineMenu;
import dev.galacticraft.machinelib.api.storage.MachineEnergyStorage;
import dev.galacticraft.machinelib.api.storage.MachineItemStorage;
import dev.galacticraft.machinelib.api.storage.StorageSpec;
import dev.galacticraft.machinelib.api.storage.slot.ItemResourceSlot;
import dev.galacticraft.machinelib.api.transfer.TransferType;
import dev.galacticraft.machinelib.api.util.EnergySource;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.Galacticraft;
import dev.galacticraft.mod.content.GCBlockEntityTypes;
import dev.galacticraft.mod.content.GCBlocks;
import dev.galacticraft.mod.content.GCFluids;
import dev.galacticraft.mod.machine.GCMachineStatuses;
import dev.galacticraft.mod.screen.GeothermalGeneratorMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

/**
 * Generates energy while standing on a vapor spout that has sulfuric acid somewhere below it.
 * Output rises and falls slowly between {@link #MIN_GENERATION} and {@link #MAX_GENERATION}.
 */
public class GeothermalGeneratorBlockEntity extends MachineBlockEntity {
    public static final int CHARGE_SLOT = 0;
    public static final long MIN_GENERATION = 30;
    public static final long MAX_GENERATION = 200;
    /** How far below the spout the generator looks for sulfuric acid. */
    public static final int MAX_ACID_DEPTH = 20;
    private static final int SPOUT_SCAN_INTERVAL = 20;

    private static final StorageSpec SPEC = StorageSpec.of(
            MachineItemStorage.spec(
                    ItemResourceSlot.builder(TransferType.PROCESSING)
                            .pos(8, 62)
                            .capacity(1)
                            .filter(ResourceFilters.CAN_INSERT_ENERGY)
                            .icon(Pair.of(InventoryMenu.BLOCK_ATLAS, Constant.SlotSprite.ENERGY))
            ),
            MachineEnergyStorage.spec(
                    Galacticraft.CONFIG.machineEnergyStorageSize(),
                    0,
                    MAX_GENERATION * 2
            )
    );

    private final EnergySource energySource = new EnergySource(this);
    private boolean validSpout = false;
    private boolean spoutScanned = false;
    private long currentEnergyGeneration = 0;

    public GeothermalGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(GCBlockEntityTypes.GEOTHERMAL_GENERATOR, pos, state, SPEC);
    }

    @Override
    protected void tickConstant(@NotNull ServerLevel level, @NotNull BlockPos pos, @NotNull BlockState state, @NotNull ProfilerFiller profiler) {
        super.tickConstant(level, pos, state, profiler);
        profiler.push("charge");
        this.drainPowerToSlot(CHARGE_SLOT);
        profiler.popPush("spout");
        if (!this.spoutScanned || (level.getGameTime() + pos.asLong()) % SPOUT_SCAN_INTERVAL == 0) {
            this.validSpout = hasValidSpout(level, pos);
            this.spoutScanned = true;
        }
        profiler.pop();
    }

    @Override
    public @NotNull MachineStatus tick(@NotNull ServerLevel level, @NotNull BlockPos pos, @NotNull BlockState state, @NotNull ProfilerFiller profiler) {
        profiler.push("push_energy");
        this.energySource.trySpreadEnergy(level, pos, state);
        profiler.pop();

        if (!this.validSpout) {
            this.currentEnergyGeneration = 0;
            return GCMachineStatuses.NOT_GENERATING;
        }
        if (this.energyStorage().isFull()) {
            this.currentEnergyGeneration = 0;
            return MachineStatuses.CAPACITOR_FULL;
        }

        profiler.push("transaction");
        this.currentEnergyGeneration = generationAt(level.getGameTime());
        this.energyStorage().insert(this.currentEnergyGeneration);
        profiler.pop();
        return GCMachineStatuses.GENERATING;
    }

    @Override
    public void tickDisabled(@NotNull ServerLevel level, @NotNull BlockPos pos, @NotNull BlockState state, @NotNull ProfilerFiller profiler) {
        this.currentEnergyGeneration = 0;
    }

    /** Energy produced on the given tick: a slow sine wave between the minimum and maximum output. */
    @VisibleForTesting
    public static long generationAt(long gameTime) {
        double wave = Mth.sin(gameTime / 50.0F) * 0.5 + 0.5;
        return (long) Math.floor(wave * (MAX_GENERATION - MIN_GENERATION)) + MIN_GENERATION;
    }

    /** True if the block below is a vapor spout and sulfuric acid lies under it, with only air in between. */
    @VisibleForTesting
    public static boolean hasValidSpout(BlockGetter level, BlockPos pos) {
        BlockPos spout = pos.below();
        if (!level.getBlockState(spout).is(GCBlocks.VAPOR_SPOUT)) {
            return false;
        }

        BlockPos.MutableBlockPos cursor = spout.mutable();
        for (int depth = 1; depth <= MAX_ACID_DEPTH; depth++) {
            cursor.move(0, -1, 0);
            BlockState below = level.getBlockState(cursor);
            if (below.getFluidState().is(GCFluids.SULFURIC_ACID) || below.getFluidState().is(GCFluids.FLOWING_SULFURIC_ACID)) {
                return true;
            }
            if (!below.isAir()) {
                return false;
            }
        }
        return false;
    }

    public boolean hasValidSpout() {
        return this.validSpout;
    }

    public long getCurrentEnergyGeneration() {
        return this.currentEnergyGeneration;
    }

    @Override
    public @Nullable MachineMenu<? extends MachineBlockEntity> createMenu(int syncId, Inventory inventory, Player player) {
        return new GeothermalGeneratorMenu(syncId, (ServerPlayer) player, this);
    }
}
