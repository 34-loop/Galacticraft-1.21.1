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
import dev.galacticraft.mod.content.GCBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * The invisible blocks around a {@link ShortRangeTelepadBlock}: a ring on the pad's level and a 3x3
 * roof two blocks above, like the Galacticraft 4 fake telepad blocks. Using or breaking a part acts
 * on the telepad itself.
 */
public class ShortRangeTelepadPartBlock extends Block {
    public static final MapCodec<ShortRangeTelepadPartBlock> CODEC = simpleCodec(ShortRangeTelepadPartBlock::new);
    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);
    private static final VoxelShape BOTTOM_SHAPE = Block.box(0, 0, 0, 16, 3.2, 16);
    private static final VoxelShape TOP_SHAPE = Block.box(0, 8.8, 0, 16, 16, 16);

    public ShortRangeTelepadPartBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.getStateDefinition().any().setValue(PART, Part.BOTTOM_NORTH));
    }

    @Override
    protected @NotNull MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PART);
    }

    /** The telepad this part belongs to, which may no longer be there. */
    public static BlockPos telepadPos(BlockPos pos, BlockState state) {
        Part part = state.getValue(PART);
        return pos.offset(-part.x, -part.y, -part.z);
    }

    private static @Nullable BlockPos telepad(BlockGetter level, BlockPos pos, BlockState state) {
        BlockPos telepad = telepadPos(pos, state);
        return level.getBlockState(telepad).is(GCBlocks.SHORT_RANGE_TELEPAD) ? telepad : null;
    }

    @Override
    protected @NotNull VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(PART).y > 0 ? TOP_SHAPE : BOTTOM_SHAPE;
    }

    @Override
    protected boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }

    @Override
    protected @NotNull RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected @NotNull InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        BlockPos telepad = telepad(level, pos, state);
        if (telepad == null) return InteractionResult.PASS;
        return level.getBlockState(telepad).useWithoutItem(level, player, hit.withPosition(telepad));
    }

    @Override
    public @NotNull BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        BlockPos telepad = telepad(level, pos, state);
        if (telepad != null) {
            level.destroyBlock(telepad, !player.isCreative(), player);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        // A part removed some other way (an explosion, a command) takes the telepad with it.
        if (!level.isClientSide() && !newState.is(this)) {
            BlockPos telepad = telepad(level, pos, state);
            if (telepad != null) level.destroyBlock(telepad, true);
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    public @NotNull ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return new ItemStack(GCBlocks.SHORT_RANGE_TELEPAD);
    }

    /** Each part's offset from the telepad. */
    public enum Part implements StringRepresentable {
        BOTTOM_NORTH_WEST(-1, 0, -1), BOTTOM_NORTH(0, 0, -1), BOTTOM_NORTH_EAST(1, 0, -1),
        BOTTOM_WEST(-1, 0, 0), BOTTOM_EAST(1, 0, 0),
        BOTTOM_SOUTH_WEST(-1, 0, 1), BOTTOM_SOUTH(0, 0, 1), BOTTOM_SOUTH_EAST(1, 0, 1),
        TOP_NORTH_WEST(-1, 2, -1), TOP_NORTH(0, 2, -1), TOP_NORTH_EAST(1, 2, -1),
        TOP_WEST(-1, 2, 0), TOP_CENTER(0, 2, 0), TOP_EAST(1, 2, 0),
        TOP_SOUTH_WEST(-1, 2, 1), TOP_SOUTH(0, 2, 1), TOP_SOUTH_EAST(1, 2, 1);

        private final int x;
        private final int y;
        private final int z;

        Part(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        /** Where this part goes for a telepad at the given position. */
        public BlockPos at(BlockPos telepad) {
            return telepad.offset(this.x, this.y, this.z);
        }

        @Override
        public @NotNull String getSerializedName() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }
}
