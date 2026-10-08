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

package dev.galacticraft.mod.network.c2s;

import dev.architectury.networking.NetworkManager;
import dev.galacticraft.impl.network.c2s.C2SPayload;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.content.block.entity.machine.ShortRangeTelepadBlockEntity;
import dev.galacticraft.mod.screen.ShortRangeTelepadMenu;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;

public record TelepadAddressPayload(int address, int targetAddress) implements C2SPayload {
    public static final StreamCodec<ByteBuf, TelepadAddressPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, TelepadAddressPayload::address,
            ByteBufCodecs.INT, TelepadAddressPayload::targetAddress,
            TelepadAddressPayload::new
    );
    public static final ResourceLocation ID = Constant.id("telepad_address");
    public static final CustomPacketPayload.Type<TelepadAddressPayload> TYPE = new CustomPacketPayload.Type<>(ID);

    @Override
    public void handle(NetworkManager.@NotNull PacketContext context) {
        ServerPlayer player = (ServerPlayer) context.getPlayer();
        if (player.containerMenu instanceof ShortRangeTelepadMenu menu) {
            ShortRangeTelepadBlockEntity machine = menu.be;
            if (machine.getSecurity().hasAccess(player)) {
                machine.setAddresses(this.address, this.targetAddress);
            }
        }
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
