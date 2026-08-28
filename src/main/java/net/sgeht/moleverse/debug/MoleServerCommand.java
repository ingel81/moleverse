package net.sgeht.moleverse.debug;

import java.util.Locale;

import org.jetbrains.annotations.Nullable;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.sgeht.moleverse.config.MoleverseConfig;
import net.sgeht.moleverse.entity.Mole;
import net.sgeht.moleverse.entity.burrow.MoleBurrowGoal;

/**
 * Server-side debug commands for the burrowing mechanic.
 *
 * <pre>
 * /moleverse mole burrow        the nearest mole digs in now, cooldown ignored
 * /moleverse mole log on|off    flip debug logging without editing the config
 * </pre>
 *
 * <p>Registered through {@code RegisterCommandsEvent} rather than the
 * {@code RegisterClientCommandsEvent} the pose commands use, because mob AI runs
 * on the server and there is nothing on the client to command. (No link to that
 * class from here: this one is common code and must not name a client class.)</p>
 *
 * <p>Both trees are rooted at the same {@code moleverse} literal, which works
 * because NeoForge's client dispatcher hands a command it cannot parse on to the
 * server: an input whose second word matches none of the client literals raises
 * Brigadier's <em>unknown argument</em>, and that is one of the two errors
 * {@code ClientCommandHandler.runCommand} falls through on.</p>
 */
public final class MoleServerCommand {

    /** How far from whoever typed the command a mole still counts as "the nearest". */
    private static final int SEARCH_RANGE = 64;

    private MoleServerCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("moleverse")
                // Gated one level down rather than at the root, because the root
                // is shared with the client-side pose commands, which need no
                // permission at all.
                .then(Commands.literal("mole")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.literal("burrow")
                                .executes(MoleServerCommand::burrow))
                        .then(Commands.literal("log")
                                .then(Commands.literal("on")
                                        .executes(ctx -> setLogging(ctx.getSource(), true)))
                                .then(Commands.literal("off")
                                        .executes(ctx -> setLogging(ctx.getSource(), false))))));
    }

    /**
     * Sends the nearest mole down at once, past its cooldown and its boredom
     * timer. The guards are not skipped - a leashed or waterlogged mole still
     * refuses, and reporting that reason back is the point.
     */
    private static int burrow(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Vec3 from = source.getPosition();

        Mole mole = nearestMole(source.getLevel(), from);
        if (mole == null) {
            source.sendFailure(Component.literal("No mole within " + SEARCH_RANGE + " blocks."));
            return 0;
        }

        String who = String.format(Locale.ROOT, "mole #%d, %.1f blocks away", mole.getId(), Math.sqrt(mole.distanceToSqr(from)));

        if (mole.getBurrowState().isBusy()) {
            source.sendFailure(Component.literal(who + " is busy: "
                    + mole.getBurrowState().name().toLowerCase(Locale.ROOT) + "."));
            return 0;
        }

        MoleBurrowGoal goal = mole.getBurrowGoal();
        if (goal == null) {
            // Goals are only built for a mole that entered a ServerLevel, and
            // that is the only kind this search can find.
            source.sendFailure(Component.literal(who + " has no burrow goal."));
            return 0;
        }

        // The answer arrives from the mole's next AI tick, a tick or two from
        // now, because that is where the mechanic makes its decision.
        goal.forceBurrow(what -> source.sendSuccess(
                () -> Component.literal(who + ": " + what).withStyle(ChatFormatting.AQUA), false));
        return 1;
    }

    private static int setLogging(CommandSourceStack source, boolean enabled) {
        MoleverseConfig.DEBUG_LOGGING.set(enabled);
        // set() only touches the config held in memory and says so; without the
        // save the file and the running value would drift apart.
        MoleverseConfig.DEBUG_LOGGING.save();

        source.sendSuccess(() -> Component
                .literal("Moleverse debug logging is " + (enabled ? "on" : "off") + " (logger moleverse.mole).")
                .withStyle(ChatFormatting.AQUA), true);
        return 1;
    }

    private static @Nullable Mole nearestMole(ServerLevel level, Vec3 from) {
        AABB box = AABB.ofSize(from, SEARCH_RANGE * 2.0, SEARCH_RANGE * 2.0, SEARCH_RANGE * 2.0);
        Mole nearest = null;
        double best = (double) SEARCH_RANGE * SEARCH_RANGE;

        for (Mole mole : level.getEntitiesOfClass(Mole.class, box)) {
            double distance = mole.distanceToSqr(from);
            if (distance < best) {
                best = distance;
                nearest = mole;
            }
        }
        return nearest;
    }
}
