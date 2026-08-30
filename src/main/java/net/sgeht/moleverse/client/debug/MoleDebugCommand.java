package net.sgeht.moleverse.client.debug;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.sgeht.moleverse.debug.DevGate;
import net.sgeht.moleverse.debug.MoleDebug;

/**
 * Client-side command for tuning the mole's poses in game.
 *
 * <pre>
 * /moleverse peek force &lt;true|false&gt;   hold every mole in the reared pose
 * /moleverse peek pitch &lt;degrees&gt;       body angle, negative lifts the nose
 * /moleverse peek y &lt;units&gt;             vertical correction, positive moves down
 * /moleverse peek z &lt;units&gt;             depth correction, positive moves back
 * /moleverse peek panel                 open the slider panel
 * /moleverse peek show                  print the current values
 * /moleverse peek reset                 back to the compiled defaults
 *
 * /moleverse dig force &lt;true|false&gt;    hold every mole in the digging pose
 * /moleverse dig pitch &lt;degrees&gt;        dig angle, positive lowers the nose, 90 is straight down
 * /moleverse dig yaw &lt;degrees&gt;          dig direction, relative to the body's facing
 * /moleverse dig burrow                 play mole_burrow once on every mole
 * /moleverse dig emerge                 play mole_emerge once on every mole
 *
 * /moleverse network &lt;on|off&gt;     draw the mounds, their links and any mole's path
 * </pre>
 *
 * <p>{@code panel}, {@code show} and {@code reset} cover both poses. They stay
 * under {@code peek} rather than moving up a level so the command the docs name
 * keeps working.</p>
 *
 * <p>Registered through {@code RegisterClientCommandsEvent}, so it runs entirely
 * on the client and needs no server, no permissions and no config file. The
 * server-side {@code /moleverse mole ...} tree shares this root and still
 * reaches the server: NeoForge passes on anything the client dispatcher parses
 * as an unknown argument.</p>
 *
 * <h2>Development runs only</h2>
 *
 * <p>Nothing here is registered outside a development run - see {@link DevGate}.
 * "No permissions" above is exactly why: a client command needs none, so until
 * this gate existed every one of these was a line any player of a shipped build
 * could type. Two of them matter more than the rest. {@code forcePeek} and
 * {@code forceDig} are not rendering flags - {@code Mole} reads them in its own
 * tick and holds the animal still - and the two play counters are read by every
 * mole in the world. Those reads sit in shipped code and are meant to be
 * unreachable, which they are only as long as nothing can write the statics
 * behind them; this registration is the only thing that ever could.</p>
 *
 * <p>Gated here rather than at the call site in {@code MoleverseClient}, so the
 * decision sits with the instrument. A gate in the wiring is one somebody
 * copying the wiring forgets.</p>
 */
public final class MoleDebugCommand {

    private MoleDebugCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        if (!DevGate.isDevelopmentRun()) {
            return;
        }
        dispatcher.register(Commands.literal("moleverse")
                .then(Commands.literal("peek")
                        .then(Commands.literal("force")
                                .then(Commands.argument("enabled", BoolArgumentType.bool())
                                        .executes(ctx -> {
                                            MoleDebug.forcePeek = BoolArgumentType.getBool(ctx, "enabled");
                                            return report(ctx.getSource());
                                        })))
                        .then(Commands.literal("pitch")
                                .then(Commands.argument("degrees", FloatArgumentType.floatArg(-180.0F, 180.0F))
                                        .executes(ctx -> {
                                            MoleDebug.peekPitchDegrees = FloatArgumentType.getFloat(ctx, "degrees");
                                            return report(ctx.getSource());
                                        })))
                        .then(Commands.literal("y")
                                .then(Commands.argument("units", FloatArgumentType.floatArg(-64.0F, 64.0F))
                                        .executes(ctx -> {
                                            MoleDebug.peekOffsetY = FloatArgumentType.getFloat(ctx, "units");
                                            return report(ctx.getSource());
                                        })))
                        .then(Commands.literal("z")
                                .then(Commands.argument("units", FloatArgumentType.floatArg(-64.0F, 64.0F))
                                        .executes(ctx -> {
                                            MoleDebug.peekOffsetZ = FloatArgumentType.getFloat(ctx, "units");
                                            return report(ctx.getSource());
                                        })))
                        .then(Commands.literal("reset")
                                .executes(ctx -> {
                                    MoleDebug.reset();
                                    return report(ctx.getSource());
                                }))
                        .then(Commands.literal("show")
                                .executes(ctx -> report(ctx.getSource())))
                        .then(Commands.literal("panel")
                                .executes(ctx -> {
                                    // Deferred: the chat screen is still closing at this point.
                                    Minecraft.getInstance().execute(
                                            () -> Minecraft.getInstance().setScreen(new MolePeekScreen()));
                                    return 1;
                                })))
                .then(Commands.literal("dig")
                        .then(Commands.literal("force")
                                .then(Commands.argument("enabled", BoolArgumentType.bool())
                                        .executes(ctx -> {
                                            MoleDebug.forceDig = BoolArgumentType.getBool(ctx, "enabled");
                                            return report(ctx.getSource());
                                        })))
                        .then(Commands.literal("pitch")
                                .then(Commands.argument("degrees", FloatArgumentType.floatArg(-180.0F, 180.0F))
                                        .executes(ctx -> {
                                            MoleDebug.digPitchDegrees = FloatArgumentType.getFloat(ctx, "degrees");
                                            return report(ctx.getSource());
                                        })))
                        .then(Commands.literal("yaw")
                                .then(Commands.argument("degrees", FloatArgumentType.floatArg(-180.0F, 180.0F))
                                        .executes(ctx -> {
                                            MoleDebug.digYawDegrees = FloatArgumentType.getFloat(ctx, "degrees");
                                            return report(ctx.getSource());
                                        })))
                        .then(Commands.literal("burrow")
                                .executes(ctx -> {
                                    MoleDebug.playBurrow();
                                    return message(ctx.getSource(), "playing mole_burrow");
                                }))
                        .then(Commands.literal("emerge")
                                .executes(ctx -> {
                                    MoleDebug.playEmerge();
                                    return message(ctx.getSource(), "playing mole_emerge");
                                })))
                .then(Commands.literal("network")
                        .then(Commands.literal("on")
                                .executes(ctx -> message(ctx.getSource(), MoleNetworkOverlay.toggle(true))))
                        .then(Commands.literal("off")
                                .executes(ctx -> message(ctx.getSource(), MoleNetworkOverlay.toggle(false))))));
    }

    private static int report(CommandSourceStack source) {
        return message(source, MoleDebug.describe());
    }

    private static int message(CommandSourceStack source, String text) {
        source.sendSuccess(() -> Component.literal(text).withStyle(ChatFormatting.AQUA), false);
        return 1;
    }
}
