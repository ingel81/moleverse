package net.sgeht.moleverse.debug;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.jetbrains.annotations.Nullable;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.sgeht.moleverse.config.MoleverseConfig;
import net.sgeht.moleverse.entity.Mole;
import net.sgeht.moleverse.entity.burrow.BurrowLink;
import net.sgeht.moleverse.entity.burrow.BurrowLog;
import net.sgeht.moleverse.entity.burrow.Colony;
import net.sgeht.moleverse.entity.burrow.ColonyStore;
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
 *
 * <h2>Development runs only</h2>
 *
 * <p>Nothing here is registered outside a development run - see {@link DevGate}.
 * The permission check below stays on top of it rather than being replaced by it:
 * the two answer different questions, and neither one covers the other. The
 * property decides whether an instrument exists at all; the permission decides who
 * may reach it in the run where it does, which still matters on a shared
 * development server.</p>
 *
 * <p>Op alone was the whole of the gate until now, and it is the wrong shape for
 * this tree. {@code colony show} and {@code colony tunnels} are only pictures, but
 * {@code mole burrow} reaches into an animal's decision and {@code mole log}
 * writes the config file. Those are instruments for watching a mechanic being
 * built, not powers a server operator was ever meant to be handed.</p>
 */
public final class MoleServerCommand {

    /** How far from whoever typed the command a mole still counts as "the nearest". */
    private static final int SEARCH_RANGE = 64;

    /** Runs printed by the dump before it starts counting the rest instead. */
    private static final int LINK_DUMP_LIMIT = 20;

    private MoleServerCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        if (!DevGate.isDevelopmentRun()) {
            return;
        }
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
                                        .executes(ctx -> setLogging(ctx.getSource(), false)))))
                .then(Commands.literal("colony")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.literal("info")
                                .executes(MoleServerCommand::colonyInfo))
                        .then(Commands.literal("list")
                                .executes(MoleServerCommand::colonyList))
                        .then(Commands.literal("show")
                                .then(Commands.literal("on")
                                        .executes(ctx -> setOutline(ctx.getSource(), true)))
                                .then(Commands.literal("off")
                                        .executes(ctx -> setOutline(ctx.getSource(), false))))
                        .then(Commands.literal("tunnels")
                                .then(Commands.literal("on")
                                        .executes(ctx -> setTunnels(ctx.getSource(), true)))
                                .then(Commands.literal("off")
                                        .executes(ctx -> setTunnels(ctx.getSource(), false))))
                        .then(Commands.literal("links")
                                .executes(MoleServerCommand::colonyLinks))));
    }

    /** Which colony owns the ground the caller is standing on, if any. */
    private static int colonyInfo(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        BlockPos here = BlockPos.containing(source.getPosition());
        Colony colony = ColonyStore.get(source.getLevel()).at(here);

        if (colony == null) {
            source.sendSuccess(() -> Component.literal("Unclaimed ground - no colony owns this spot.")
                    .withStyle(ChatFormatting.GRAY), false);
            return 0;
        }

        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "Colony #%d - core %d,%d, ground x %d..%d z %d..%d",
                colony.id(), colony.core().getX(), colony.core().getZ(),
                colony.minX(), colony.maxX(), colony.minZ(), colony.maxZ())), false);
        return 1;
    }

    /** Every colony of this level, nearest first. */
    private static int colonyList(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Vec3 from = source.getPosition();
        List<Colony> colonies = new ArrayList<>(ColonyStore.get(source.getLevel()).all());

        if (colonies.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No colonies yet.").withStyle(ChatFormatting.GRAY), false);
            return 0;
        }

        colonies.sort(Comparator.comparingDouble(colony -> colony.core().getCenter().distanceToSqr(from)));
        for (Colony colony : colonies) {
            double away = Math.sqrt(colony.core().getCenter().distanceToSqr(from));
            source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                    "#%d at %d,%d - %.0f blocks away",
                    colony.id(), colony.core().getX(), colony.core().getZ(), away)), false);
        }
        return colonies.size();
    }

    /**
     * Turns the stored-run view on or off.
     *
     * <p>Separate from the client's own {@code /moleverse network} overlay, and
     * it wins while it is on: the client rebuilds runs at the one depth it knows,
     * so a main run would be drawn two blocks too high. This sends what is
     * actually stored.</p>
     */
    private static int setTunnels(CommandSourceStack source, boolean on) {
        TunnelView.setEnabled(on);
        source.sendSuccess(() -> Component.literal("Stored runs " + (on ? "on" : "off")
                + (on ? " - turn on /moleverse network to see them" : ""))
                .withStyle(on ? ChatFormatting.GREEN : ChatFormatting.GRAY), false);
        return 1;
    }

    /**
     * Prints the runs stored for the colony underfoot, after clearing out any
     * whose mounds are gone.
     */
    private static int colonyLinks(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        ColonyStore store = ColonyStore.get(level);

        int pruned = store.prune(level);
        Colony colony = store.at(BlockPos.containing(source.getPosition()));
        if (colony == null) {
            source.sendSuccess(() -> Component.literal("Unclaimed ground - no colony, no runs."
                    + (pruned > 0 ? " Pruned " + pruned + " stale run(s)." : ""))
                    .withStyle(ChatFormatting.GRAY), false);
            return 0;
        }

        List<BurrowLink> links = store.linksOf(colony.id());
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "Colony #%d - %d run(s) stored%s",
                colony.id(), links.size(),
                pruned > 0 ? ", " + pruned + " stale one(s) pruned" : "")), false);

        int shown = Math.min(links.size(), LINK_DUMP_LIMIT);
        for (int i = 0; i < shown; i++) {
            BurrowLink link = links.get(i);
            int deepest = link.depths().stream().mapToInt(Integer::intValue).min().orElse(0);
            int shallowest = link.depths().stream().mapToInt(Integer::intValue).max().orElse(0);
            source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                    "  %d,%d - %d,%d  %s  y %d..%d  %d point(s)  used %dx",
                    link.a().getX(), link.a().getZ(), link.b().getX(), link.b().getZ(),
                    link.level().getSerializedName(), deepest, shallowest,
                    link.pointCount(), link.uses())), false);
        }
        if (links.size() > shown) {
            int rest = links.size() - shown;
            source.sendSuccess(() -> Component.literal("  ... and " + rest + " more")
                    .withStyle(ChatFormatting.GRAY), false);
        }
        return links.size();
    }

    /**
     * Turns the colony outline on or off. It stays on and covers every colony,
     * not only the one underfoot - the point of looking at a border is comparing
     * it with the next one.
     */
    private static int setOutline(CommandSourceStack source, boolean on) {
        ColonyOutline.setEnabled(on);
        source.sendSuccess(() -> Component.literal("Colony outline " + (on ? "on" : "off"))
                .withStyle(on ? ChatFormatting.GREEN : ChatFormatting.GRAY), false);
        return 1;
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

        // And the logger's own override, which is what a dev run reads instead of
        // the config. Without this line the command would report success and
        // change nothing while moleverse.devLogging is set.
        BurrowLog.setOverride(enabled);

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
