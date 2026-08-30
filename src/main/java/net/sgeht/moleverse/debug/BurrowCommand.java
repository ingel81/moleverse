package net.sgeht.moleverse.debug;

import java.util.List;
import java.util.Locale;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.sgeht.moleverse.dimension.BurrowGeometry;
import net.sgeht.moleverse.dimension.CorridorCarver;
import net.sgeht.moleverse.dimension.Junctions;
import net.sgeht.moleverse.dimension.LevelShafts;
import net.sgeht.moleverse.dimension.ModDimensions;
import net.sgeht.moleverse.entity.burrow.BurrowLink;
import net.sgeht.moleverse.entity.burrow.Colony;
import net.sgeht.moleverse.entity.burrow.ColonyStore;

/**
 * Getting into the burrow below without playing for it, and seeing what is
 * carved down there.
 *
 * <pre>
 * /moleverse burrow enter    map this position down and stand in it
 * /moleverse burrow leave    back to the overworld position this one maps to
 * /moleverse burrow carve    carve every run of the colony underfoot
 * /moleverse burrow info     what this position maps to, both ways
 * </pre>
 *
 * <p>Separate from the shrink post on purpose. The post is the way a player is
 * meant to get down there and it asks for a prepared mound, a colony and a
 * fitting; this asks for nothing, because the first thing anybody wants to know
 * about a new dimension is what it looks like.</p>
 *
 * <h2>Development runs only</h2>
 *
 * <p>Nothing here is registered outside a development run - see {@link DevGate}.
 * "Asks for nothing" is exactly why: the post is the way into the burrow, and a
 * command that skips the mound, the colony and the fitting is a way around the
 * only mechanic that gives arriving down there any meaning. The permission check
 * below stays on top of the property - it decides who may reach the tree in a run
 * where it exists, which the property does not answer.</p>
 *
 * <p>{@code carve} is the one that would have cost something. It writes blocks
 * into a live world and there is no undo, so leaving it reachable by any operator
 * of a shipped build meant shipping a way to rewrite a dimension by accident.</p>
 */
public final class BurrowCommand {

    /** How far around a mound its runs are collected when carving by hand. */
    private static final int CARVE_RANGE = 48;

    private BurrowCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        if (!DevGate.isDevelopmentRun()) {
            return;
        }
        dispatcher.register(Commands.literal("moleverse")
                .then(Commands.literal("burrow")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.literal("enter").executes(BurrowCommand::enter))
                        .then(Commands.literal("leave").executes(BurrowCommand::leave))
                        .then(Commands.literal("carve").executes(BurrowCommand::carve))
                        .then(Commands.literal("info").executes(BurrowCommand::info))));
    }

    private static int enter(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel burrow = ModDimensions.burrowLevel(source.getServer());

        if (burrow == null) {
            source.sendFailure(Component.literal("No burrow dimension - the datapack did not load."));
            return 0;
        }

        BlockPos here = BlockPos.containing(source.getPosition());
        BlockPos there = BurrowGeometry.toBurrow(here);
        CorridorCarver.carveChamber(burrow, there);

        player.teleportTo(burrow, there.getX() + 0.5, there.getY() + 1.0, there.getZ() + 0.5,
                java.util.Set.of(), player.getYRot(), player.getXRot(), false);
        source.sendSuccess(() -> Component.literal("Below at " + describe(there)), false);
        return 1;
    }

    private static int leave(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel overworld = source.getServer().overworld();

        BlockPos back = BurrowGeometry.toOverworld(BlockPos.containing(source.getPosition()));
        int surface = overworld.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                back.getX(), back.getZ());

        player.teleportTo(overworld, back.getX() + 0.5, surface, back.getZ() + 0.5,
                java.util.Set.of(), player.getYRot(), player.getXRot(), false);
        source.sendSuccess(() -> Component.literal("Back up at " + back.getX() + ", " + back.getZ()), false);
        return 1;
    }

    private static int carve(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        ServerLevel burrow = ModDimensions.burrowLevel(source.getServer());

        if (burrow == null) {
            source.sendFailure(Component.literal("No burrow dimension - the datapack did not load."));
            return 0;
        }
        if (ModDimensions.isBurrow(level)) {
            source.sendFailure(Component.literal("Run this above ground - the runs are stored there."));
            return 0;
        }

        BlockPos here = BlockPos.containing(source.getPosition());
        ColonyStore store = ColonyStore.get(level);
        Colony colony = store.at(here);
        if (colony == null) {
            source.sendFailure(Component.literal("Unclaimed ground - no colony, no runs to carve."));
            return 0;
        }

        List<BurrowLink> links = store.linksOf(colony.id());
        int carved = 0;
        for (BurrowLink link : links) {
            carved += CorridorCarver.carve(burrow, link);
        }
        carved += CorridorCarver.carveChamber(burrow, BurrowGeometry.toBurrow(colony.core()));
        LevelShafts.connect(burrow, links);
        Junctions.cut(burrow, links);

        int total = carved;
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "Colony #%d: %d run(s), %d block(s) cleared below",
                colony.id(), links.size(), total)), false);
        return links.size();
    }

    private static int info(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        BlockPos here = BlockPos.containing(source.getPosition());
        boolean below = ModDimensions.isBurrow(source.getLevel());

        BlockPos other = below ? BurrowGeometry.toOverworld(here) : BurrowGeometry.toBurrow(here);
        source.sendSuccess(() -> Component.literal(
                (below ? "In the burrow at " : "Above ground at ") + describe(here)
                        + " -> " + describe(other))
                .withStyle(ChatFormatting.GRAY), false);
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "scale %d horizontal, %d vertical, corridor %dx%d",
                BurrowGeometry.SCALE, BurrowGeometry.VERTICAL_SCALE,
                BurrowGeometry.CORRIDOR_WIDTH, BurrowGeometry.CORRIDOR_HEIGHT))
                .withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    private static String describe(BlockPos pos) {
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }
}
