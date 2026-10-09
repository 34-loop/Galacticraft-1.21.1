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
package dev.galacticraft.mod.content.block.special;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.AABB;
import com.mojang.serialization.MapCodec;
import org.jetbrains.annotations.Nullable;

/**
 * Looks like a decoration block but searches for players in front of it every 25 ticks. Ported from the Galacticraft 4
 * concealed detector: the search box is 14 blocks wide and deep, from 6 blocks below to 2 blocks above the detector,
 * and grows by 3 blocks on every side while a player is detected so the output does not flicker.
 *
 * <p>The GC4 block outputs 15 while no player is detected and 0 while one is, and this port keeps that.
 */
public class ConcealedDetectorBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<ConcealedDetectorBlock> CODEC = simpleCodec(ConcealedDetectorBlock::new);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty DETECTED = BooleanProperty.create("detected");
    private static final int INTERVAL = 25;
    private static final double RANGE = 14.0D;

    public ConcealedDetectorBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(DETECTED, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, DETECTED);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!oldState.is(state.getBlock()) && !level.isClientSide) {
            level.scheduleTick(pos, this, INTERVAL);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        double hysteresis = state.getValue(DETECTED) ? 3.0D : 0.0D;
        double x = pos.getX();
        double y = pos.getY();
        double z = pos.getZ();
        AABB area = switch (state.getValue(FACING)) {
            case EAST -> new AABB(x + 1 - hysteresis, y - 6 - hysteresis, z - RANGE / 2 + 0.5D - hysteresis, x + RANGE + 1 + hysteresis, y + 2 + hysteresis, z + RANGE / 2 + 0.5D + hysteresis);
            case SOUTH -> new AABB(x - RANGE / 2 + 0.5D - hysteresis, y - 6 - hysteresis, z + 1 - hysteresis, x + RANGE / 2 + 0.5D + hysteresis, y + 2 + hysteresis, z + RANGE + 1 + hysteresis);
            case WEST -> new AABB(x - RANGE - hysteresis, y - 6 - hysteresis, z - RANGE / 2 + 0.5D - hysteresis, x + hysteresis, y + 2 + hysteresis, z + RANGE / 2 + 0.5D + hysteresis);
            default -> new AABB(x - RANGE / 2 + 0.5D - hysteresis, y - 6 - hysteresis, z - RANGE - hysteresis, x + RANGE / 2 + 0.5D + hysteresis, y + 2 + hysteresis, z + hysteresis);
        };
        boolean detected = !level.getEntitiesOfClass(Player.class, area).isEmpty();
        if (detected != state.getValue(DETECTED)) {
            level.setBlock(pos, state.setValue(DETECTED, detected), Block.UPDATE_ALL);
            level.updateNeighborsAt(pos, this);
        }
        level.scheduleTick(pos, this, INTERVAL);
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(DETECTED) ? 0 : 15;
    }
}
