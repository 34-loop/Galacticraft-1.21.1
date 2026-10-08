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

package dev.galacticraft.mod.client.render.item;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.galacticraft.mod.client.render.block.entity.ShortRangeTelepadBlockEntityRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Renders the short range telepad item as its 3D model, like the Galacticraft 4 item. The item model
 * json ({@code models/item/short_range_telepad.json}) delegates here via {@code minecraft:builtin/entity}.
 */
public class ShortRangeTelepadItemRenderer {
    public void render(ItemStack stack, ItemDisplayContext mode, PoseStack matrices, MultiBufferSource vertexConsumers, int light, int overlay) {
        matrices.pushPose();
        matrices.translate(0.5F, 0.5F, 0.5F);
        switch (mode) {
            case GUI -> {
                matrices.mulPose(Axis.XP.rotationDegrees(30.0F));
                matrices.mulPose(Axis.YP.rotationDegrees(225.0F));
                matrices.scale(0.3F, 0.3F, 0.3F);
            }
            case FIXED -> matrices.scale(0.3F, 0.3F, 0.3F);
            case GROUND -> {
                matrices.translate(0.0F, -0.25F, 0.0F);
                matrices.scale(0.15F, 0.15F, 0.15F);
            }
            case FIRST_PERSON_LEFT_HAND, FIRST_PERSON_RIGHT_HAND -> {
                matrices.mulPose(Axis.YP.rotationDegrees(45.0F));
                matrices.scale(0.2F, 0.2F, 0.2F);
            }
            default -> {
                matrices.mulPose(Axis.YP.rotationDegrees(45.0F));
                matrices.scale(0.15F, 0.15F, 0.15F);
            }
        }
        // The model is three blocks high with its pad at the origin.
        matrices.translate(0.0F, -1.5F, 0.0F);
        ShortRangeTelepadBlockEntityRenderer.renderModel(matrices, vertexConsumers, light, light);
        matrices.popPose();
    }
}
