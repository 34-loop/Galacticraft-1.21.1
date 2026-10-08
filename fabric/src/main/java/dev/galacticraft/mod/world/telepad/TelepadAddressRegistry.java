/*
 * Copyright (c) 2019-2026 Team Galacticraft
 * Copyright (c) 2026 Colin Vaughn
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

package dev.galacticraft.mod.world.telepad;

import dev.galacticraft.mod.Constant;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

/**
 * Server-wide map from short range telepad addresses to the telepads that claimed them.
 * Stored with the overworld so addresses stay unique across all dimensions.
 */
public class TelepadAddressRegistry extends SavedData {
    private static final String ID = Constant.MOD_ID + "_telepad_addresses";
    private static final String ENTRIES = "entries";
    private static final String ADDRESS = "address";
    private static final String LOCATION = "location";

    private final Int2ObjectMap<GlobalPos> telepads = new Int2ObjectOpenHashMap<>();

    public static TelepadAddressRegistry get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(TelepadAddressRegistry::new, TelepadAddressRegistry::load, null),
                ID);
    }

    public @Nullable GlobalPos get(int address) {
        return this.telepads.get(address);
    }

    public void put(int address, GlobalPos location) {
        if (!location.equals(this.telepads.put(address, location))) {
            this.setDirty();
        }
    }

    /** Releases the address if it still belongs to the telepad at the given location. */
    public void remove(int address, GlobalPos location) {
        if (location.equals(this.telepads.get(address))) {
            this.telepads.remove(address);
            this.setDirty();
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag entries = new ListTag();
        for (Int2ObjectMap.Entry<GlobalPos> entry : this.telepads.int2ObjectEntrySet()) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putInt(ADDRESS, entry.getIntKey());
            GlobalPos.CODEC.encodeStart(NbtOps.INSTANCE, entry.getValue())
                    .ifSuccess(location -> entryTag.put(LOCATION, location));
            entries.add(entryTag);
        }
        tag.put(ENTRIES, entries);
        return tag;
    }

    private static TelepadAddressRegistry load(CompoundTag tag, HolderLookup.Provider registries) {
        TelepadAddressRegistry registry = new TelepadAddressRegistry();
        for (Tag entry : tag.getList(ENTRIES, Tag.TAG_COMPOUND)) {
            CompoundTag entryTag = (CompoundTag) entry;
            GlobalPos.CODEC.parse(NbtOps.INSTANCE, entryTag.get(LOCATION))
                    .ifSuccess(location -> registry.telepads.put(entryTag.getInt(ADDRESS), location));
        }
        return registry;
    }
}
