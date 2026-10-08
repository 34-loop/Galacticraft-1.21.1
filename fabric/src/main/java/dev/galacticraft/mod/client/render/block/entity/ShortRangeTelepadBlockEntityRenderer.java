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

package dev.galacticraft.mod.client.render.block.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.client.model.GCModel;
import dev.galacticraft.mod.client.model.GCModelLoader;
import dev.galacticraft.mod.client.model.GCModelState;
import dev.galacticraft.mod.client.model.GCRenderTypes;
import dev.galacticraft.mod.content.block.entity.machine.ShortRangeTelepadBlockEntity;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;

/**
 * Draws the Galacticraft 4 {@code telepad_short} OBJ over the whole 3x3x3 telepad, with the same
 * transform as the original renderer.
 */
@Environment(EnvType.CLIENT)
public class ShortRangeTelepadBlockEntityRenderer implements BlockEntityRenderer<ShortRangeTelepadBlockEntity> {
    public static final ResourceLocation MODEL = Constant.id("models/misc/short_range_telepad.json");
    private static final GCModelState BOTTOM = new GCModelState("Bottom");
    private static final GCModelState TOP = new GCModelState("Top");
    private static final GCModelState CONNECTOR = new GCModelState("Connector");

    private static GCModel model;

    public ShortRangeTelepadBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(ShortRangeTelepadBlockEntity blockEntity, float tickDelta, PoseStack matrices, MultiBufferSource vertexConsumers, int light, int overlay) {
        BlockPos pos = blockEntity.getBlockPos();
        int roofLight = blockEntity.getLevel() != null ? LevelRenderer.getLightColor(blockEntity.getLevel(), pos.above(2)) : light;
        matrices.pushPose();
        matrices.translate(0.5F, 0.0F, 0.5F);
        renderModel(matrices, vertexConsumers, light, roofLight);
        matrices.popPose();
    }

    /** Renders the telepad with its pad at the origin, centred on the y axis. */
    public static void renderModel(PoseStack matrices, MultiBufferSource vertexConsumers, int light, int roofLight) {
        if (model == null) {
            model = GCModelLoader.INSTANCE.getModel(MODEL);
        }
        VertexConsumer consumer = vertexConsumers.getBuffer(GCRenderTypes.obj(GCRenderTypes.OBJ_ATLAS));
        matrices.pushPose();
        matrices.scale(0.745F, 1.0F, 0.745F);
        model.render(matrices, BOTTOM, consumer, light, OverlayTexture.NO_OVERLAY);
        matrices.translate(0.0F, -0.7F, 0.0F);
        model.render(matrices, TOP, consumer, roofLight, OverlayTexture.NO_OVERLAY);
        model.render(matrices, CONNECTOR, consumer, roofLight, OverlayTexture.NO_OVERLAY);
        matrices.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(ShortRangeTelepadBlockEntity blockEntity) {
        return true;
    }

    /** Used by NeoForge to cull the renderer: the model covers the whole 3x3x3 telepad. */
    public AABB getRenderBoundingBox(ShortRangeTelepadBlockEntity blockEntity) {
        BlockPos pos = blockEntity.getBlockPos();
        return new AABB(pos.getX() - 1, pos.getY(), pos.getZ() - 1, pos.getX() + 2, pos.getY() + 3, pos.getZ() + 2);
    }
}
