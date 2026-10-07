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

import dev.galacticraft.machinelib.client.api.screen.MachineScreen;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.content.block.entity.machine.GeothermalGeneratorBlockEntity;
import dev.galacticraft.mod.screen.GeothermalGeneratorMenu;
import dev.galacticraft.mod.util.DrawableUtil;
import dev.galacticraft.mod.util.Translations;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;

@Environment(EnvType.CLIENT)
public class GeothermalGeneratorScreen extends MachineScreen<GeothermalGeneratorBlockEntity, GeothermalGeneratorMenu> {
    private static final int TEXT_X = 36;
    private static final int TEXT_Y = 40;
    private static final Component NO_SPOUT = Component.translatable(Translations.Ui.GEOTHERMAL_NO_SPOUT).setStyle(Constant.Text.RED_STYLE);

    public GeothermalGeneratorScreen(GeothermalGeneratorMenu menu, Inventory inv, Component title) {
        super(menu, title, Constant.ScreenTexture.GEOTHERMAL_GENERATOR_SCREEN);
    }

    @Override
    protected void renderMachineBackground(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.renderMachineBackground(graphics, mouseX, mouseY, delta);
        Component text = this.menu.hasValidSpout()
                ? Component.translatable(Translations.Ui.GEOTHERMAL_GENERATING, DrawableUtil.getEnergyDisplay(this.menu.getEnergyGeneration())).setStyle(Constant.Text.DARK_GRAY_STYLE)
                : NO_SPOUT;
        graphics.drawString(this.font, text, this.leftPos + TEXT_X, this.topPos + TEXT_Y, 0xFFFFFF, false);
    }

    @Override
    public void appendEnergyTooltip(List<Component> list) {
        super.appendEnergyTooltip(list);
        if (this.menu.getEnergyGeneration() > 0) {
            list.add(Component.translatable(Translations.Ui.GJT, DrawableUtil.getEnergyDisplay(this.menu.getEnergyGeneration())).setStyle(Constant.Text.LIGHT_PURPLE_STYLE));
        }
    }
}
