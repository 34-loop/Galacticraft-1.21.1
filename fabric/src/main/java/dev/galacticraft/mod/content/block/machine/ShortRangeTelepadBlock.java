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
import dev.galacticraft.mod.content.block.entity.machine.ShortRangeTelepadBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;

/**
 * A single block pad. The Galacticraft 4 telepad was a 3x3x3 multiblock with an OBJ model; this port
 * keeps its behaviour but not its size.
 */
public class ShortRangeTelepadBlock extends MachineBlock {
    private static final MapCodec<ShortRangeTelepadBlock> CODEC = simpleCodec(ShortRangeTelepadBlock::new);
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, ShortRangeTelepadBlockEntity.PAD_HEIGHT * 16, 16);

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
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof ShortRangeTelepadBlockEntity telepad) {
            telepad.releaseAddress();
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(ACTIVE)) {
            for (int i = 0; i < 4; i++) {
                double x = pos.getX() + random.nextDouble();
                double z = pos.getZ() + random.nextDouble();
                level.addParticle(ParticleTypes.PORTAL, x, pos.getY() + ShortRangeTelepadBlockEntity.PAD_HEIGHT + random.nextDouble() * 2.0, z, 0.0, -0.3, 0.0);
            }
        }
    }
}
