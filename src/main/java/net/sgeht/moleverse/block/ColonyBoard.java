package net.sgeht.moleverse.block;

import java.util.List;
import java.util.Locale;

import com.mojang.serialization.MapCodec;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.sgeht.moleverse.entity.burrow.BurrowLink;
import net.sgeht.moleverse.entity.burrow.Colony;
import net.sgeht.moleverse.entity.burrow.ColonyStore;
import net.sgeht.moleverse.entity.burrow.RunLevel;

/**
 * A board on a prepared mound that says what the colony under it is doing.
 *
 * <p>The problem it answers is the one the shaft lantern answers with light: a
 * colony is invisible. It has a centre, a territory and a graph of runs that
 * were really dug, all of it in {@link ColonyStore} and none of it above ground.
 * {@code /moleverse colony links} can already print the lot, but that is a
 * gamemaster command and it prints a table. This is the survival-mode version -
 * fewer numbers, in sentences, standing in the meadow it describes.</p>
 *
 * <p>Deliberately read-only. The debug command prunes stale runs before it
 * reports, which is the right thing for a maintenance tool and the wrong thing
 * for furniture: a right-click that quietly deletes stored data is a trap, and a
 * board that answers differently depending on who read it last is worse than no
 * board.</p>
 *
 * <p>A {@link MoundAttachment}, so it needs a prepared mound underneath - which
 * is also what makes the reading meaningful. The board reports the colony that
 * owns <em>the mound</em>, not the one that owns wherever the player happens to
 * be standing.</p>
 */
public class ColonyBoard extends MoundAttachment {

    public static final MapCodec<ColonyBoard> CODEC = simpleCodec(ColonyBoard::new);

    /** Post and panel, as two boxes: a single one would put an invisible wall either side of the stem. */
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(7.0, 0.0, 7.0, 9.0, 7.0, 9.0),
            Block.box(3.0, 7.0, 7.0, 13.0, 15.0, 9.0));

    public ColonyBoard(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<ColonyBoard> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
            // The colony store lives on the server and is not sent anywhere.
            return InteractionResult.CONSUME;
        }

        serverLevel.playSound(null, pos, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 0.7F, 1.0F);

        // The mound is what a colony owns; the board only sits on it.
        BlockPos mound = pos.below();
        ColonyStore store = ColonyStore.get(serverLevel);
        Colony colony = store.at(mound);

        if (colony == null) {
            // Saying nothing here would read as a broken block. Unclaimed ground
            // is a real answer, and the band of it around every colony is
            // deliberate - see ColonyStore.found.
            serverPlayer.sendSystemMessage(Component.literal("No colony claims this ground.")
                    .withStyle(ChatFormatting.GOLD));
            serverPlayer.sendSystemMessage(Component
                    .literal("  A mound here is somebody's stray digging, not a home.")
                    .withStyle(ChatFormatting.GRAY));
            return InteractionResult.SUCCESS_SERVER;
        }

        report(serverPlayer, store, colony, mound);
        return InteractionResult.SUCCESS_SERVER;
    }

    /**
     * Four lines. A colony has more to say than that, and saying it would make
     * this the table the debug command already is.
     */
    private static void report(ServerPlayer player, ColonyStore store, Colony colony, BlockPos mound) {
        List<BurrowLink> links = store.linksOf(colony.id());

        player.sendSystemMessage(Component.literal("Colony #" + colony.id())
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        player.sendSystemMessage(line("Core", String.format(Locale.ROOT, "%d, %d - %s",
                colony.core().getX(), colony.core().getZ(), distanceTo(colony, mound))));
        player.sendSystemMessage(line("Runs dug", links.isEmpty()
                ? "none yet"
                : links.size() + (links.size() == 1 ? " run" : " runs")));

        if (!links.isEmpty()) {
            player.sendSystemMessage(line("Deepest", describe(deepest(links))));
        }
    }

    private static Component line(String label, String value) {
        return Component.literal("  " + label + ": ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(value).withStyle(ChatFormatting.WHITE));
    }

    /**
     * How far the mound is from the core, on the flat.
     *
     * <p>Straight-line horizontal distance rather than the Chebyshev measure the
     * colony's own box is built on: the box is a rule, this is an answer to "how
     * far do I have to walk", and a player reading a hillside colony should not
     * be told a number that depends on which way the border runs.</p>
     */
    private static String distanceTo(Colony colony, BlockPos mound) {
        double dx = mound.getX() - colony.core().getX();
        double dz = mound.getZ() - colony.core().getZ();
        long away = Math.round(Math.sqrt(dx * dx + dz * dz));
        return away == 0 ? "this mound is the core" : away + " block(s) from here";
    }

    /** The lowest level any of this colony's runs was dug at. */
    private static RunLevel deepest(List<BurrowLink> links) {
        RunLevel found = links.get(0).level();
        for (BurrowLink link : links) {
            if (link.level().depth() > found.depth()) {
                found = link.level();
            }
        }
        return found;
    }

    /** What a level means, rather than what it is called in the save file. */
    private static String describe(RunLevel level) {
        return switch (level) {
            case FEEDING -> "feeding runs, just under the turf";
            case MAIN -> "main runs, the colony's backbone";
            case CHAMBER -> "the chamber level, where the nest is";
        };
    }
}
