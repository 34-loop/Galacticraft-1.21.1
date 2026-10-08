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

package dev.galacticraft.mod.content.block.machine;

import com.mojang.serialization.MapCodec;
import dev.galacticraft.machinelib.api.block.MachineBlock;
import dev.galacticraft.machinelib.api.block.entity.MachineBlockEntity;
import dev.galacticraft.mod.content.GCBlocks;
import dev.galacticraft.mod.content.block.entity.machine.ShortRangeTelepadBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * The Galacticraft 4 short range telepad: a pad with a ring of {@link ShortRangeTelepadPartBlock}s
 * around it and a roof of them two blocks above, 3x3x3 in all. The model is drawn by the block
 * entity renderer.
 */
public class ShortRangeTelepadBlock extends MachineBlock {
    private static final MapCodec<ShortRangeTelepadBlock> CODEC = simpleCodec(ShortRangeTelepadBlock::new);
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, ShortRangeTelepadBlockEntity.PAD_HEIGHT * 16, 16);
    private static final DustParticleOptions PARTICLE = new DustParticleOptions(new Vector3f(0.3F, 0.5F, 1.0F), 1.0F);

    public ShortRangeTelepadBlock(Properties settings) {
        super(settings);
    }

    @Override
    protected @NotNull MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public @NotNull MachineBlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ShortRangeTelepadBlockEntity(pos, state);
    }

    @Override
    protected @NotNull VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }

    @Override
    public @NotNull RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    /** Like the original, the telepad needs room for all its parts. */
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        for (ShortRangeTelepadPartBlock.Part part : ShortRangeTelepadPartBlock.Part.values()) {
            BlockPos pos = part.at(context.getClickedPos());
            if (level.isOutsideBuildHeight(pos) || !level.getBlockState(pos).canBeReplaced(context)) {
                return null;
            }
        }
        return super.getStateForPlacement(context);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        super.onPlace(state, level, pos, oldState, moved);
        if (level.isClientSide() || oldState.is(this)) return;
        for (ShortRangeTelepadPartBlock.Part part : ShortRangeTelepadPartBlock.Part.values()) {
            BlockPos partPos = part.at(pos);
            if (!level.isOutsideBuildHeight(partPos) && level.getBlockState(partPos).canBeReplaced()) {
                level.setBlockAndUpdate(partPos, GCBlocks.SHORT_RANGE_TELEPAD_PART.defaultBlockState().setValue(ShortRangeTelepadPartBlock.PART, part));
            }
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())) {
            if (level.getBlockEntity(pos) instanceof ShortRangeTelepadBlockEntity telepad) {
                telepad.releaseAddress();
            }
            if (!level.isClientSide()) {
                for (ShortRangeTelepadPartBlock.Part part : ShortRangeTelepadPartBlock.Part.values()) {
                    BlockPos partPos = part.at(pos);
                    BlockState partState = level.getBlockState(partPos);
                    if (partState.is(GCBlocks.SHORT_RANGE_TELEPAD_PART) && partState.getValue(ShortRangeTelepadPartBlock.PART) == part) {
                        level.removeBlock(partPos, false);
                    }
                }
            }
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    /** Blue sparks rise from the pad and fall from the roof, like the original telepad. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        int count = state.getValue(ACTIVE) ? 4 : 1;
        for (int i = 0; i < count; i++) {
            level.addParticle(PARTICLE, pos.getX() + 0.2 + random.nextDouble() * 0.6, pos.getY() + 0.5, pos.getZ() + 0.2 + random.nextDouble() * 0.6, 0.0, 1.4, 0.0);
            double x = pos.getX() + (random.nextBoolean() ? random.nextDouble() * 0.2 : 0.8 + random.nextDouble() * 0.2);
            double z = pos.getZ() + random.nextDouble();
            if (random.nextBoolean()) {
                double swap = x - pos.getX();
                x = pos.getX() + (z - pos.getZ());
                z = pos.getZ() + swap;
            }
            level.addParticle(PARTICLE, x, pos.getY() + 2.4, z, 0.0, -2.95, 0.0);
        }
    }
}
