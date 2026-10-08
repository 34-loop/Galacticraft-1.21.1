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
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.Galacticraft;
import dev.galacticraft.mod.content.GCBlockEntityTypes;
import dev.galacticraft.mod.machine.GCMachineStatuses;
import dev.galacticraft.mod.screen.ShortRangeTelepadMenu;
import dev.galacticraft.mod.util.Translations;
import dev.galacticraft.mod.world.telepad.TelepadAddressRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.List;

/**
 * Teleports living entities standing on it to another telepad, like the Galacticraft 4 short range
 * telepad. Each telepad claims a numeric address; after {@link #TELEPORT_TICKS} ticks on the pad,
 * entities are sent to the telepad whose address is set as the target, if it is in the same
 * dimension and within {@link #RANGE} blocks. Both telepads pay {@link #ENERGY_PER_TELEPORT}.
 */
public class ShortRangeTelepadBlockEntity extends MachineBlockEntity {
    public static final int CHARGE_SLOT = 0;
    public static final int NO_ADDRESS = -1;
    public static final int MAX_ADDRESS = 999_999;
    public static final int TELEPORT_TICKS = 150;
    public static final int RANGE = 256;
    public static final long ENERGY_PER_TELEPORT = 2500;
    /** Height of the pad, where teleported entities are placed. */
    public static final double PAD_HEIGHT = 0.45;
    private static final int TARGET_SCAN_INTERVAL = 20;

    private static final StorageSpec SPEC = StorageSpec.of(
            MachineItemStorage.spec(
                    ItemResourceSlot.builder(TransferType.TRANSFER)
                            .pos(8, 62)
                            .capacity(1)
                            .filter(ResourceFilters.CAN_EXTRACT_ENERGY)
                            .icon(Pair.of(InventoryMenu.BLOCK_ATLAS, Constant.SlotSprite.ENERGY))
            ),
            MachineEnergyStorage.spec(
                    Galacticraft.CONFIG.machineEnergyStorageSize(),
                    100,
                    0
            )
    );

    public enum TargetStatus {
        NONE, NOT_FOUND, OTHER_DIMENSION, TOO_FAR, VALID
    }

    private int address = NO_ADDRESS;
    private int targetAddress = NO_ADDRESS;
    private boolean addressClaimed = false;
    private TargetStatus targetStatus = TargetStatus.NONE;
    private int teleportTime = 0;

    public ShortRangeTelepadBlockEntity(BlockPos pos, BlockState state) {
        super(GCBlockEntityTypes.SHORT_RANGE_TELEPAD, pos, state, SPEC);
    }

    @Override
    protected void tickConstant(@NotNull ServerLevel level, @NotNull BlockPos pos, @NotNull BlockState state, @NotNull ProfilerFiller profiler) {
        super.tickConstant(level, pos, state, profiler);
        this.chargeFromSlot(CHARGE_SLOT);
        if ((level.getGameTime() + pos.asLong()) % TARGET_SCAN_INTERVAL == 0) {
            this.refreshAddresses(level);
        }
    }

    @Override
    protected @NotNull MachineStatus tick(@NotNull ServerLevel level, @NotNull BlockPos pos, @NotNull BlockState state, @NotNull ProfilerFiller profiler) {
        if (this.address == NO_ADDRESS) return this.idle(GCMachineStatuses.TELEPAD_NO_ADDRESS);
        if (!this.addressClaimed) return this.idle(GCMachineStatuses.TELEPAD_ADDRESS_IN_USE);
        switch (this.targetStatus) {
            case NONE -> {
                return this.idle(GCMachineStatuses.TELEPAD_NO_TARGET);
            }
            case NOT_FOUND -> {
                return this.idle(GCMachineStatuses.TELEPAD_TARGET_NOT_FOUND);
            }
            case OTHER_DIMENSION, TOO_FAR -> {
                return this.idle(GCMachineStatuses.TELEPAD_TARGET_TOO_FAR);
            }
            case VALID -> {
            }
        }
        if (!this.energyStorage().canExtract(ENERGY_PER_TELEPORT)) return this.idle(MachineStatuses.NOT_ENOUGH_ENERGY);

        List<LivingEntity> passengers = level.getEntitiesOfClass(LivingEntity.class, passengerArea(pos));
        if (passengers.isEmpty()) return this.idle(GCMachineStatuses.TELEPAD_READY);

        if (++this.teleportTime >= TELEPORT_TICKS) {
            this.teleportTime = 0;
            this.teleport(level, passengers);
        }
        return GCMachineStatuses.TELEPORTING;
    }

    @Override
    public void tickDisabled(@NotNull ServerLevel level, @NotNull BlockPos pos, @NotNull BlockState state, @NotNull ProfilerFiller profiler) {
        this.teleportTime = 0;
    }

    /** The countdown winds down instead of resetting, like the original telepad. */
    private MachineStatus idle(MachineStatus status) {
        this.teleportTime = Math.max(this.teleportTime - 1, 0);
        return status;
    }

    private void teleport(ServerLevel level, List<LivingEntity> passengers) {
        GlobalPos target = TelepadAddressRegistry.get(level.getServer()).get(this.targetAddress);
        if (target == null || !(level.getBlockEntity(target.pos()) instanceof ShortRangeTelepadBlockEntity destination)) {
            this.targetStatus = TargetStatus.NOT_FOUND;
            return;
        }
        if (!destination.isReceiving(this.targetAddress)) {
            notify(passengers, Component.translatable(Translations.Ui.TELEPAD_TARGET_INVALID));
            return;
        }
        if (!destination.energyStorage().canExtract(ENERGY_PER_TELEPORT)) {
            notify(passengers, Component.translatable(Translations.Ui.TELEPAD_TARGET_NO_ENERGY));
            return;
        }

        BlockPos to = target.pos();
        level.playSound(null, this.worldPosition, SoundEvents.ENDERMAN_TELEPORT, SoundSource.BLOCKS, 1.0F, 1.0F);
        for (LivingEntity passenger : passengers) {
            passenger.teleportTo(to.getX() + 0.5, to.getY() + PAD_HEIGHT, to.getZ() + 0.5);
            passenger.resetFallDistance();
        }
        level.playSound(null, to, SoundEvents.ENDERMAN_TELEPORT, SoundSource.BLOCKS, 1.0F, 1.0F);
        this.energyStorage().extract(ENERGY_PER_TELEPORT);
        destination.energyStorage().extract(ENERGY_PER_TELEPORT);
    }

    private static void notify(List<LivingEntity> passengers, Component message) {
        for (LivingEntity passenger : passengers) {
            if (passenger instanceof ServerPlayer player) {
                player.displayClientMessage(message, true);
            }
        }
    }

    /** Entities standing on the pad: the block itself and the two blocks of air above it. */
    @VisibleForTesting
    public static AABB passengerArea(BlockPos pos) {
        return new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 2, pos.getZ() + 1);
    }

    /** True if this telepad currently owns the given address and can accept arrivals. */
    public boolean isReceiving(int address) {
        if (this.level instanceof ServerLevel serverLevel) this.refreshAddresses(serverLevel);
        return this.address == address && this.addressClaimed;
    }

    /** Claims this telepad's address if free and re-evaluates the target. */
    private void refreshAddresses(ServerLevel level) {
        TelepadAddressRegistry registry = TelepadAddressRegistry.get(level.getServer());
        GlobalPos here = GlobalPos.of(level.dimension(), this.worldPosition);
        this.addressClaimed = this.address != NO_ADDRESS && claim(level, registry, this.address, here);
        this.targetStatus = this.findTarget(level, registry, here);
    }

    /** An address can be taken if nobody holds it or its holder no longer exists. */
    private static boolean claim(ServerLevel level, TelepadAddressRegistry registry, int address, GlobalPos here) {
        GlobalPos holder = registry.get(address);
        if (holder != null && !holder.equals(here) && isStillHeld(level, holder, address)) return false;
        registry.put(address, here);
        return true;
    }

    private static boolean isStillHeld(ServerLevel level, GlobalPos holder, int address) {
        ServerLevel holderLevel = level.getServer().getLevel(holder.dimension());
        if (holderLevel == null) return false;
        // Telepads in unloaded chunks keep their address.
        if (!holderLevel.isLoaded(holder.pos())) return true;
        return holderLevel.getBlockEntity(holder.pos()) instanceof ShortRangeTelepadBlockEntity telepad && telepad.address == address;
    }

    private TargetStatus findTarget(ServerLevel level, TelepadAddressRegistry registry, GlobalPos here) {
        if (this.targetAddress == NO_ADDRESS) return TargetStatus.NONE;
        GlobalPos target = registry.get(this.targetAddress);
        if (target == null || target.equals(here)) return TargetStatus.NOT_FOUND;
        if (!target.dimension().equals(level.dimension())) return TargetStatus.OTHER_DIMENSION;
        if (target.pos().distSqr(this.worldPosition) >= (double) RANGE * RANGE) return TargetStatus.TOO_FAR;
        return TargetStatus.VALID;
    }

    /** Sets both addresses from the menu; {@link #NO_ADDRESS} clears one. */
    public void setAddresses(int address, int targetAddress) {
        if (this.level instanceof ServerLevel serverLevel && this.address != address && this.address != NO_ADDRESS) {
            TelepadAddressRegistry.get(serverLevel.getServer()).remove(this.address, GlobalPos.of(serverLevel.dimension(), this.worldPosition));
        }
        this.address = sanitize(address);
        this.targetAddress = sanitize(targetAddress);
        this.teleportTime = 0;
        if (this.level instanceof ServerLevel serverLevel) this.refreshAddresses(serverLevel);
        this.setChanged();
    }

    /** Releases this telepad's address when the block is removed. */
    public void releaseAddress() {
        if (this.level instanceof ServerLevel serverLevel && this.address != NO_ADDRESS) {
            TelepadAddressRegistry.get(serverLevel.getServer()).remove(this.address, GlobalPos.of(serverLevel.dimension(), this.worldPosition));
        }
    }

    @VisibleForTesting
    public static int sanitize(int address) {
        return address < 0 || address > MAX_ADDRESS ? NO_ADDRESS : address;
    }

    public int getAddress() {
        return this.address;
    }

    public int getTargetAddress() {
        return this.targetAddress;
    }

    public boolean isAddressClaimed() {
        return this.addressClaimed;
    }

    public TargetStatus getTargetStatus() {
        return this.targetStatus;
    }

    public int getTeleportTime() {
        return this.teleportTime;
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider lookup) {
        super.loadAdditional(tag, lookup);
        this.address = sanitize(tag.contains(Constant.Nbt.TELEPAD_ADDRESS) ? tag.getInt(Constant.Nbt.TELEPAD_ADDRESS) : NO_ADDRESS);
        this.targetAddress = sanitize(tag.contains(Constant.Nbt.TELEPAD_TARGET_ADDRESS) ? tag.getInt(Constant.Nbt.TELEPAD_TARGET_ADDRESS) : NO_ADDRESS);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider lookup) {
        super.saveAdditional(tag, lookup);
        tag.putInt(Constant.Nbt.TELEPAD_ADDRESS, this.address);
        tag.putInt(Constant.Nbt.TELEPAD_TARGET_ADDRESS, this.targetAddress);
    }

    @Override
    public @Nullable MachineMenu<? extends MachineBlockEntity> createMenu(int syncId, Inventory inventory, Player player) {
        return new ShortRangeTelepadMenu(syncId, (ServerPlayer) player, this);
    }
}
