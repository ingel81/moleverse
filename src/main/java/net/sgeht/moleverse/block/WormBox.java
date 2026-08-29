package net.sgeht.moleverse.block;

import java.util.HashMap;
import java.util.Map;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.sgeht.moleverse.registry.ModBlocks;
import net.sgeht.moleverse.registry.ModItems;

import org.jetbrains.annotations.Nullable;

/**
 * A bin of earth and scraps that makes worms. The mod's currency press.
 *
 * <p>Everything in this mod is paid for in earthworms, so the rate at which
 * worms appear is the rate at which everything else happens - which is the
 * reason this block is deliberately slow. Breaking molehills is opportunistic
 * and a {@link GruntingPost} is a place a player walks back to; the box is the
 * one source that keeps producing while nobody watches, and it is the only one
 * that can be built up in a row. A fast box would make the exchange station's
 * table meaningless within an evening.</p>
 *
 * <p>It is a relative of the composter and works the same way: feed it, it
 * fills, it sits, it can be emptied. The differences are the two that matter for
 * an economy - what goes in decides the <em>quality</em> of what comes out, not
 * just how fast it arrives, and what comes out has tiers.</p>
 *
 * <p><strong>No block entity.</strong> The whole state of a box is how full it
 * is and what it has been fed, which is a number from zero to eight and one of
 * three qualities: twenty-seven block states, and vanilla stores those in the
 * chunk's palette for free. The ripening delay is a scheduled tick, which the
 * chunk saves along with everything else. A block entity would add a ticking
 * object, a save format and a synchronisation question to hold two small
 * numbers - the same reasoning as {@link ShaftLantern} and the
 * {@link GruntingPost}, and the same honest limit: a scheduled tick does not
 * count down in a chunk nobody is near, so a box ripens while somebody is around
 * to see it.</p>
 *
 * <p>Everything that decides anything happens server side. The client is told
 * the resulting block state like any other block change; its only own work is
 * the crumbs in {@link #animateTick}.</p>
 */
public class WormBox extends Block {

    public static final MapCodec<WormBox> CODEC = simpleCodec(WormBox::new);

    /**
     * How full the box is. Zero to seven while it is being fed, eight when the
     * worms are ready to be taken out.
     *
     * <p>The composter's arrangement, and worth copying whole: the last feeding
     * step and the finished state being the same property is what lets one
     * comparator reading describe the entire block.</p>
     */
    public static final IntegerProperty FILL = IntegerProperty.create("fill", 0, 8);

    /** What the box has been fed with. See {@link Feed}. */
    public static final EnumProperty<Feed> FEED = EnumProperty.create("feed", Feed.class);

    /** Fed full. From here the box only waits. */
    public static final int FULL = 7;

    /** Worms ready. The only state {@link #useWithoutItem} does anything in. */
    public static final int RIPE = 8;

    /**
     * How long a full box takes to turn its contents into worms.
     *
     * <p>Two minutes. Long enough that standing in front of one is pointless -
     * which is the whole intent, because a player who waits at the box is a
     * player not out looking at mounds - and short enough that a row of six is a
     * working farm rather than a monument.</p>
     */
    public static final int RIPEN_TICKS = 2400;

    private static final int MIN_YIELD = 3;

    private static final int MAX_YIELD = 5;

    /**
     * Chance that a well-fed box turns one of its worms into a fat one.
     *
     * <p>Deliberately not a certainty. Rich feed buys a good chance at a better
     * worm, not a recipe for one - the tier stays something a player accumulates
     * rather than orders.</p>
     */
    private static final float FAT_WORM_CHANCE = 0.30F;

    /** Rarer again, and it needs the feed nothing above ground provides. */
    private static final float GLOW_WORM_CHANCE = 0.12F;

    /** Wall thickness, in the sixteenths a model is measured in. */
    private static final double WALL = 2.0;

    /**
     * A bin: a full block with the inside taken out.
     *
     * <p>{@code getCollisionShape} is not overridden, so the hollow is the
     * collision too and a player can stand in an empty box. That is the
     * composter's behaviour and it is the right one - a container you can climb
     * into reads as a container.</p>
     */
    private static final VoxelShape SHAPE = Shapes.join(
            Shapes.block(),
            Block.column(16.0 - 2.0 * WALL, WALL, 16.0),
            BooleanOp.ONLY_FIRST);

    public WormBox(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState()
                .setValue(FILL, 0)
                .setValue(FEED, Feed.PLAIN));
    }

    @Override
    public MapCodec<WormBox> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FILL, FEED);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /**
     * Clicks land on the whole cube, not on the walls.
     *
     * <p>Without this the hollow middle is a hole to reach through: a player
     * aiming into the box hits whatever is behind it, which is exactly where
     * everybody aims when feeding one.</p>
     */
    @Override
    protected VoxelShape getInteractionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.block();
    }

    /** Mobs do not path into an open bin, for the same reason they do not into a composter. */
    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    /**
     * Feeding the box.
     *
     * <p>Anything it will not take falls through to the default behaviour, which
     * ends in {@link #useWithoutItem} - so a player holding a shovel can still
     * empty a ripe box without putting it away first.</p>
     */
    @Override
    protected InteractionResult useItemOn(
            ItemStack stack,
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hitResult) {
        FeedValue value = FeedTable.valueOf(stack);
        if (value == null || state.getValue(FILL) >= FULL) {
            return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
        }

        if (level instanceof ServerLevel serverLevel) {
            feed(serverLevel, pos, state, value, player);
            player.awardStat(Stats.ITEM_USED.get(stack.getItem()));
            stack.consume(1, player);
        }
        return InteractionResult.SUCCESS;
    }

    /** Taking the worms out. Does nothing at all until the box is ripe. */
    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (state.getValue(FILL) != RIPE) {
            return InteractionResult.PASS;
        }

        if (level instanceof ServerLevel serverLevel) {
            harvest(serverLevel, pos, state, player);
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * One helping of feed.
     *
     * <p>Two things happen here and they are independent on purpose. The
     * <em>level</em> only sometimes goes up - the chance is the feed's value, and
     * that is what makes leaves cheap and rotten flesh worth carrying home. The
     * <em>quality</em> is recorded either way, because the item was eaten either
     * way: a player who feeds three glow mycelium and is told by the dice that
     * none of them counted would rightly call the block broken.</p>
     *
     * <p>The first helping into an empty box always counts, which is the
     * composter's rule. It exists so that the block visibly answers the very
     * first thing a player ever puts into it.</p>
     */
    private void feed(ServerLevel level, BlockPos pos, BlockState state, FeedValue feed, @Nullable Player player) {
        int fill = state.getValue(FILL);
        boolean advanced = fill == 0 || level.getRandom().nextFloat() < feed.chance();

        BlockState updated = state
                .setValue(FILL, advanced ? fill + 1 : fill)
                .setValue(FEED, state.getValue(FEED).atLeast(feed.quality()));

        if (updated != state) {
            level.setBlock(pos, updated, Block.UPDATE_ALL);
            level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(player, updated));
        }

        if (updated.getValue(FILL) == FULL) {
            level.scheduleTick(pos, this, RIPEN_TICKS);
        }

        level.playSound(null, pos,
                advanced ? SoundEvents.COMPOSTER_FILL_SUCCESS : SoundEvents.COMPOSTER_FILL,
                SoundSource.BLOCKS, 1.0F, 1.0F);
        level.sendParticles(ParticleTypes.COMPOSTER,
                pos.getX() + 0.5, pos.getY() + 1.05, pos.getZ() + 0.5,
                advanced ? 8 : 3, 0.25, 0.0, 0.25, 0.0);
    }

    /**
     * Three to five worms, at most one of them better than common.
     *
     * <p>One special roll per harvest rather than one per worm. A box that can
     * hand over five fat worms at once would make the tier a matter of patience;
     * as it stands, the tiers are things that turn up.</p>
     */
    private static void harvest(ServerLevel level, BlockPos pos, BlockState state, @Nullable Player player) {
        RandomSource random = level.getRandom();
        int worms = MIN_YIELD + random.nextInt(MAX_YIELD - MIN_YIELD + 1);

        ItemStack special = rollSpecial(state.getValue(FEED), random);
        if (!special.isEmpty()) {
            worms--;
            Block.popResourceFromFace(level, pos, Direction.UP, special);
        }
        if (worms > 0) {
            Block.popResourceFromFace(level, pos, Direction.UP,
                    new ItemStack(ModItems.EARTHWORM.get(), worms));
        }

        BlockState emptied = state.setValue(FILL, 0).setValue(FEED, Feed.PLAIN);
        level.setBlock(pos, emptied, Block.UPDATE_ALL);
        level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(player, emptied));
        level.playSound(null, pos, SoundEvents.COMPOSTER_EMPTY, SoundSource.BLOCKS, 1.0F, 1.0F);
    }

    /** The one worm in a harvest that may be better than an earthworm, or nothing. */
    private static ItemStack rollSpecial(Feed feed, RandomSource random) {
        if (feed == Feed.GLOWING && random.nextFloat() < GLOW_WORM_CHANCE) {
            return new ItemStack(ModItems.GLOW_WORM.get());
        }
        if (feed != Feed.PLAIN && random.nextFloat() < FAT_WORM_CHANCE) {
            return new ItemStack(ModItems.FAT_WORM.get());
        }
        return ItemStack.EMPTY;
    }

    /** The ripening delay running out. */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(FILL) == FULL) {
            level.setBlock(pos, state.setValue(FILL, RIPE), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.COMPOSTER_READY, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
    }

    /**
     * A box that arrives already full still ripens.
     *
     * <p>Nothing in play places one - the item always gives an empty box - but a
     * command, a structure or a schematic can, and a box stuck at seven forever
     * would be a bug with no visible cause.</p>
     */
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        if (state.getValue(FILL) == FULL) {
            level.scheduleTick(pos, this, RIPEN_TICKS);
        }
    }

    /** A comparator reads how full the box is: zero to seven filling, eight ready. */
    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        return state.getValue(FILL);
    }

    /**
     * A ripe box stirs.
     *
     * <p>The block has one model for every state, so this is currently the only
     * thing that tells a player from across the room that a box is done - and
     * even once there are per-level models it is worth keeping, because it is the
     * finished state that has to carry across a room.</p>
     */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(FILL) != RIPE || random.nextInt(3) != 0) {
            return;
        }
        level.addParticle(ParticleTypes.COMPOSTER,
                pos.getX() + 0.3 + random.nextDouble() * 0.4,
                pos.getY() + 0.9,
                pos.getZ() + 0.3 + random.nextDouble() * 0.4,
                0.0, 0.0, 0.0);
    }

    /**
     * What a box has been fed with, and therefore what it can produce.
     *
     * <p>Three values rather than two booleans: quality is a ladder, and an enum
     * says so. Glowing implies rich, which is true by the feed - anything that
     * makes a box glow is also good food - and a pair of flags would have to be
     * kept consistent by hand.</p>
     *
     * <p>It never drops during a cycle, see {@link #atLeast}. A player who feeds
     * one prize item and then a barrow of leaves has still fed the box the prize
     * item.</p>
     */
    public enum Feed implements StringRepresentable {

        /** Soil and greenery. Earthworms and nothing else. */
        PLAIN("plain"),

        /** Something with substance in it went in: a fat worm becomes possible. */
        RICH("rich"),

        /** Fungus from the burrow went in. See {@link FeedTable}. */
        GLOWING("glowing");

        private final String name;

        Feed(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }

        /** The better of the two. Declared order is the ladder. */
        Feed atLeast(Feed other) {
            return this.ordinal() >= other.ordinal() ? this : other;
        }
    }

    /** One row of the feed table: how likely it is to count, and what it does to the box. */
    private record FeedValue(float chance, Feed quality) {
    }

    /**
     * What a worm box eats.
     *
     * <p>Plant matter is not listed here. Anything the vanilla composter accepts
     * feeds a worm box at the same value, read straight off NeoForge's
     * {@code compostables} data map through {@link ComposterBlock#getValue} -
     * which is a hundred entries this mod does not have to keep in step with
     * vanilla, and it means a modded plant that composts also feeds worms without
     * anybody doing anything.</p>
     *
     * <p>The table below is what that map does not cover, and it is the whole
     * design of the block:</p>
     * <ul>
     * <li><strong>Soil</strong> is bedding. It fills a box and produces nothing
     * better, which is what stops a shovel from being a worm farm.</li>
     * <li><strong>Rotten flesh and bone meal</strong> are the rich feed. Both are
     * true to how a worm bin is actually run - decaying protein and a mineral
     * grit - and both are things a player already has too many of. Bone meal
     * being the composter's <em>output</em> is the loop {@code IDEAS.md} asks
     * for: compost the greenery, feed the compost to the worms.</li>
     * <li><strong>Glow mycelium</strong> is the only feed that makes glow worms,
     * and it grows nowhere but the burrow below. That is deliberate. A glow worm
     * should not be a rare roll on a common box, because rarity alone is just a
     * slower version of the same thing; it should mean the player has been down
     * there. It also puts the glow where it comes from - the worm shines because
     * of what it ate, which is roughly true of every glowing larva there is - and
     * it makes the burrow's own material worth carrying back up, which nothing
     * else so far does.</li>
     * </ul>
     *
     * <p>Built on first use rather than at class load. This class is loaded
     * during block registration, when {@code ModItems} has not run yet and
     * {@code get()} on any of it would throw; a holder class defers that to the
     * first feeding and the JVM makes it thread-safe for free.</p>
     */
    private static final class FeedTable {

        private static final float BEDDING = 0.3F;

        private static final Map<Item, FeedValue> VALUES = build();

        private FeedTable() {
        }

        /**
         * The feed value of a stack, or {@code null} if a worm box will not take it.
         */
        private static @Nullable FeedValue valueOf(ItemStack stack) {
            FeedValue listed = VALUES.get(stack.getItem());
            if (listed != null) {
                return listed;
            }
            // -1 for anything that is not compostable at all.
            float compostable = ComposterBlock.getValue(stack);
            return compostable > 0.0F ? new FeedValue(compostable, Feed.PLAIN) : null;
        }

        private static Map<Item, FeedValue> build() {
            Map<Item, FeedValue> values = new HashMap<>();

            put(values, BEDDING, Feed.PLAIN,
                    ModBlocks.LOOSE_SOIL.get(),
                    Items.DIRT,
                    Items.COARSE_DIRT,
                    Items.ROOTED_DIRT,
                    Items.PODZOL,
                    Items.MUD,
                    Items.MUDDY_MANGROVE_ROOTS);

            put(values, 0.65F, Feed.RICH, Items.BONE_MEAL);
            put(values, 0.85F, Feed.RICH, Items.ROTTEN_FLESH);
            put(values, 0.65F, Feed.GLOWING, ModBlocks.GLOW_MYCELIUM.get());

            return Map.copyOf(values);
        }

        private static void put(Map<Item, FeedValue> values, float chance, Feed quality, ItemLike... items) {
            FeedValue value = new FeedValue(chance, quality);
            for (ItemLike item : items) {
                values.put(item.asItem(), value);
            }
        }
    }
}
