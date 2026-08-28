package net.sgeht.moleverse.client.debug;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.sgeht.moleverse.debug.MoleDebug;

/**
 * Client-side command for tuning the mole's rearing pose in game.
 *
 * <pre>
 * /moleverse peek force &lt;true|false&gt;   hold every mole in the reared pose
 * /moleverse peek pitch &lt;degrees&gt;       body angle, negative lifts the nose
 * /moleverse peek y &lt;units&gt;             vertical correction, positive moves down
 * /moleverse peek z &lt;units&gt;             depth correction, positive moves back
 * /moleverse peek panel                 open the slider panel
 * /moleverse peek show                  print the current values
 * /moleverse peek reset                 back to the compiled defaults
 * </pre>
 *
 * <p>Registered through {@code RegisterClientCommandsEvent}, so it runs entirely
 * on the client and needs no server, no permissions and no config file.</p>
 */
public final class MoleDebugCommand {

    private MoleDebugCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
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
                                }))));
    }

    private static int report(CommandSourceStack source) {
        source.sendSuccess(
                () -> Component.literal(MoleDebug.describe()).withStyle(ChatFormatting.AQUA),
                false);
        return 1;
    }
}
