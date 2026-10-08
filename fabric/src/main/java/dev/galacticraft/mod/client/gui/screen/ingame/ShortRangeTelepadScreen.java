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

package dev.galacticraft.mod.client.gui.screen.ingame;

import dev.architectury.networking.NetworkManager;
import dev.galacticraft.machinelib.client.api.screen.MachineScreen;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.content.block.entity.machine.ShortRangeTelepadBlockEntity;
import dev.galacticraft.mod.network.c2s.TelepadAddressPayload;
import dev.galacticraft.mod.screen.ShortRangeTelepadMenu;
import dev.galacticraft.mod.util.Translations;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.entity.player.Inventory;
import org.lwjgl.glfw.GLFW;

import java.util.function.IntSupplier;

@Environment(EnvType.CLIENT)
public class ShortRangeTelepadScreen extends MachineScreen<ShortRangeTelepadBlockEntity, ShortRangeTelepadMenu> {
    private static final int TEXT_X = 32;
    private static final int BOX_X = 104;
    private static final int BOX_WIDTH = 64;
    private static final int BOX_HEIGHT = 12;
    private static final int ADDRESS_Y = 17;
    private static final int TARGET_Y = 33;
    private static final int BUTTON_Y = 48;
    private static final int STATUS_Y = 68;

    private EditBox addressBox;
    private EditBox targetBox;
    /** Set while the player edits an address, so synced values do not overwrite their input. */
    private boolean editing = false;
    private boolean updating = false;

    public ShortRangeTelepadScreen(ShortRangeTelepadMenu menu, Inventory inv, Component title) {
        super(menu, title, Constant.ScreenTexture.GEOTHERMAL_GENERATOR_SCREEN);
    }

    @Override
    protected void init() {
        super.init();
        this.addressBox = this.addressBox(ADDRESS_Y, Translations.Ui.TELEPAD_ADDRESS);
        this.targetBox = this.addressBox(TARGET_Y, Translations.Ui.TELEPAD_TARGET_ADDRESS);
        this.addRenderableWidget(Button.builder(Component.translatable(Translations.Ui.TELEPAD_SET), button -> this.apply())
                .bounds(this.leftPos + BOX_X, this.topPos + BUTTON_Y, BOX_WIDTH, 14)
                .build());
        this.editing = false;
        this.syncBoxes();
    }

    private EditBox addressBox(int y, String name) {
        EditBox box = new EditBox(this.font, this.leftPos + BOX_X, this.topPos + y, BOX_WIDTH, BOX_HEIGHT, Component.translatable(name));
        box.setMaxLength(String.valueOf(ShortRangeTelepadBlockEntity.MAX_ADDRESS).length());
        box.setFilter(s -> s.chars().allMatch(Character::isDigit));
        box.setResponder(s -> {
            if (!this.updating) this.editing = true;
        });
        return this.addRenderableWidget(box);
    }

    private void apply() {
        NetworkManager.sendToServer(new TelepadAddressPayload(parse(this.addressBox.getValue()), parse(this.targetBox.getValue())));
        this.editing = false;
        this.addressBox.setFocused(false);
        this.targetBox.setFocused(false);
        this.setFocused(null);
    }

    private static int parse(String value) {
        if (value.isEmpty()) return ShortRangeTelepadBlockEntity.NO_ADDRESS;
        try {
            return ShortRangeTelepadBlockEntity.sanitize(Integer.parseInt(value));
        } catch (NumberFormatException e) {
            return ShortRangeTelepadBlockEntity.NO_ADDRESS;
        }
    }

    private void syncBoxes() {
        if (this.editing) return;
        this.updating = true;
        setIfChanged(this.addressBox, this.menu::getAddress);
        setIfChanged(this.targetBox, this.menu::getTargetAddress);
        this.updating = false;
    }

    private static void setIfChanged(EditBox box, IntSupplier address) {
        int value = address.getAsInt();
        String text = value == ShortRangeTelepadBlockEntity.NO_ADDRESS ? "" : String.valueOf(value);
        if (!box.getValue().equals(text)) box.setValue(text);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        this.syncBoxes();
    }

    @Override
    protected void renderMachineBackground(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.renderMachineBackground(graphics, mouseX, mouseY, delta);
        int color = ChatFormatting.DARK_GRAY.getColor();
        graphics.drawString(this.font, Component.translatable(Translations.Ui.TELEPAD_ADDRESS), this.leftPos + TEXT_X, this.topPos + ADDRESS_Y + 2, color, false);
        graphics.drawString(this.font, Component.translatable(Translations.Ui.TELEPAD_TARGET_ADDRESS), this.leftPos + TEXT_X, this.topPos + TARGET_Y + 2, color, false);
    }

    @Override
    protected void renderForeground(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.renderForeground(graphics, mouseX, mouseY, delta);
        MutableComponent status;
        if (this.menu.state.isActive()) {
            int ticksLeft = Math.max(ShortRangeTelepadBlockEntity.TELEPORT_TICKS - this.menu.getTeleportTime(), 0);
            status = Component.translatable(Translations.Ui.TELEPAD_COUNTDOWN, (ticksLeft + 19) / 20).withStyle(ChatFormatting.DARK_GREEN);
        } else {
            status = this.menu.state.getStatusText(this.menu.redstoneMode).copy();
            if (status.getStyle().getColor() == TextColor.fromLegacyFormat(ChatFormatting.GREEN)) {
                status.withStyle(ChatFormatting.DARK_GREEN);
            }
        }
        graphics.drawString(this.font, status, this.leftPos + TEXT_X, this.topPos + STATUS_Y, -1, false);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        EditBox focused = this.addressBox.isFocused() ? this.addressBox : this.targetBox.isFocused() ? this.targetBox : null;
        if (focused != null && keyCode != GLFW.GLFW_KEY_ESCAPE) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                this.apply();
                return true;
            }
            // Keep typed digits from triggering inventory or hotbar keys.
            focused.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
