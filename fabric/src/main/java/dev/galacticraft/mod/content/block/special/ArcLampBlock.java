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
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The Galacticraft 4 arc lamp. It lights its surroundings at full strength and goes dark while powered by redstone.
 * Hostile mobs within {@value #RANGE} blocks walk away from it, checked once a second like the roughly one-in-twenty
 * ticks of GC4.
 *
 * <p>Deviation from GC4: the area is lit by the block's own light level instead of invisible light blocks placed
 * across a 14 block range, and the lamp does not need a wrench to turn.
 */
public class ArcLampBlock extends Block {
    /** The direction from the lamp towards the block it is attached to. */
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    private static final double RANGE = 14.0D;
    private static final VoxelShape[] SHAPES = new VoxelShape[6];

    static {
        SHAPES[Direction.DOWN.ordinal()] = Block.box(2, 0, 2, 14, 7, 14);
        SHAPES[Direction.UP.ordinal()] = Block.box(2, 9, 2, 14, 16, 14);
        SHAPES[Direction.NORTH.ordinal()] = Block.box(2, 2, 0, 14, 14, 7);
        SHAPES[Direction.SOUTH.ordinal()] = Block.box(2, 2, 9, 14, 14, 16);
        SHAPES[Direction.WEST.ordinal()] = Block.box(0, 2, 2, 7, 14, 14);
        SHAPES[Direction.EAST.ordinal()] = Block.box(9, 2, 2, 16, 14, 14);
    }

    public ArcLampBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.DOWN).setValue(LIT, true));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(FACING).ordinal()];
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getClickedFace().getOpposite();
        BlockState state = this.defaultBlockState()
                .setValue(FACING, facing)
                .setValue(LIT, !context.getLevel().hasNeighborSignal(context.getClickedPos()));
        return state.canSurvive(context.getLevel(), context.getClickedPos()) ? state : null;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        BlockPos support = pos.relative(facing);
        return level.getBlockState(support).isFaceSturdy(level, support, facing.getOpposite());
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return direction == state.getValue(FACING) && !state.canSurvive(level, pos)
                ? Blocks.AIR.defaultBlockState()
                : super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!oldState.is(state.getBlock()) && !level.isClientSide) {
            level.scheduleTick(pos, this, 20);
        }
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean movedByPiston) {
        if (!level.isClientSide) {
            boolean lit = !level.hasNeighborSignal(pos);
            if (lit != state.getValue(LIT)) {
                level.setBlock(pos, state.setValue(LIT, lit), Block.UPDATE_CLIENTS);
            }
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(LIT)) {
            Vec3 lamp = Vec3.atCenterOf(pos);
            List<PathfinderMob> mobs = level.getEntitiesOfClass(PathfinderMob.class, new AABB(pos).inflate(RANGE), mob -> mob instanceof Enemy);
            for (PathfinderMob mob : mobs) {
                Vec3 target = LandRandomPos.getPosAway(mob, 28, 11, lamp);
                if (target != null && target.distanceToSqr(lamp) > mob.position().distanceToSqr(lamp)) {
                    mob.getNavigation().moveTo(target.x, target.y, target.z, 1.3D);
                }
            }
        }
        level.scheduleTick(pos, this, 20);
    }
}
