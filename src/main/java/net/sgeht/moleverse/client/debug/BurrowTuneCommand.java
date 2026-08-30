package net.sgeht.moleverse.client.debug;

import com.mojang.brigadier.CommandDispatcher;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.sgeht.moleverse.debug.DevGate;

/**
 * Opens the burrow tuning panel.
 *
 * <pre>
 * /moleverse burrow panel     slider panel for decoration and ambience
 * /moleverse burrow tuned     print every value that has moved
 * </pre>
 *
 * <p>Registered through {@code RegisterClientCommandsEvent}, like the pose panel
 * next door, because a screen is client business and nothing here asks the server
 * for anything. It shares the {@code burrow} literal with the server-side
 * {@code /moleverse burrow enter|leave|carve|info} on purpose - the two belong to
 * the same subject and the split is an implementation detail nobody typing a
 * command should have to know.</p>
 *
 * <p>That sharing is safe, and the reason is worth writing down. When the client
 * dispatcher meets {@code /moleverse burrow enter} it walks to the {@code burrow}
 * node, finds no literal called {@code enter} and no argument children to fall
 * back on, and returns a parse with the reader still holding text. Brigadier turns
 * that into {@code dispatcherUnknownArgument}, which is one of the two exceptions
 * NeoForge's {@code ClientCommandHandler} answers by handing the line to the
 * server untouched. Add an argument child under {@code burrow} here and that stops
 * being true - the parse would fail inside the argument instead, and every server
 * side burrow command would die on the client with a red error.</p>
 *
 * <h2>Development only</h2>
 *
 * <p>Nothing is registered outside a development run - see {@link DevGate}, which
 * every instrument in the mod now asks. The reason this one needs it most: the
 * sliders write into statics that decide how the burrow is carved and dressed,
 * and a shipped client must not carry a way to move worldgen numbers out from
 * under the game. A dev instrument that is merely undocumented is one somebody
 * eventually finds.</p>
 */
public final class BurrowTuneCommand {

    private BurrowTuneCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        if (!DevGate.isDevelopmentRun()) {
            return;
        }
        dispatcher.register(Commands.literal("moleverse")
                .then(Commands.literal("burrow")
                        .then(Commands.literal("panel")
                                .executes(context -> {
                                    // Deferred: the chat screen is still closing at this point.
                                    Minecraft.getInstance().execute(() -> Minecraft.getInstance()
                                            .setScreen(new BurrowTunePanel()));
                                    return 1;
                                }))
                        .then(Commands.literal("tuned")
                                .executes(context -> report(context.getSource())))));
    }

    /** The same list the panel's copy button writes, without opening anything. */
    private static int report(CommandSourceStack source) {
        var lines = BurrowKnobs.changedLines();
        if (lines.isEmpty()) {
            return message(source, "Burrow: every value is the shipped one.");
        }
        message(source, "Burrow: " + lines.size() + " value(s) moved.");
        for (String line : lines) {
            message(source, "  " + line);
        }
        return lines.size();
    }

    private static int message(CommandSourceStack source, String text) {
        source.sendSuccess(() -> Component.literal(text).withStyle(ChatFormatting.AQUA), false);
        return 1;
    }
}
