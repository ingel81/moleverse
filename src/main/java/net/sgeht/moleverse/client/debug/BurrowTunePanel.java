package net.sgeht.moleverse.client.debug;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.sgeht.moleverse.Moleverse;
import net.sgeht.moleverse.client.debug.BurrowKnobs.Section;

/**
 * Tuning panel for the burrow: how densely a corridor is dressed, and what the
 * dark around it sounds like.
 *
 * <p>The same instrument as {@link MolePeekScreen} and for the same reason. A
 * corridor's decoration and a burrow's ambience are numbers whose only meaning is
 * what they look and sound like from inside, and neither can be judged from a
 * source file, a config or a screenshot. So: a strip down the left, the world
 * still rendering and still ticking beside it, and a slider whose effect arrives
 * while it is being dragged. {@link #isPauseScreen()} returns false and the
 * background covers the panel alone, because anything that dims what is being
 * tuned defeats the point of tuning it in game.</p>
 *
 * <h2>The two halves behave differently, and the panel says which is which</h2>
 *
 * <p>The ambience below is client code and reads its numbers per event, so a
 * slider there is heard on the next roll - a second or two for a mote, and as long
 * as the burst delay for a scratch, which is what the burst delay slider is for.
 * Nothing needs rebuilding and nothing is stored.</p>
 *
 * <p>Decoration is placed once, server side, when a run is carved, so a slider
 * there changes what the <em>next</em> stretch of corridor comes out like and
 * touches nothing already standing. "Re-dress nearby" is the shortcut for that,
 * and it is honestly labelled additive: see {@link BurrowRedress} for why it can
 * add a lamp and can never take one away.</p>
 *
 * <h2>Single player, or the host of a LAN world</h2>
 *
 * <p>The decoration sliders write into statics that live in the server's own
 * classes. That works because a single player client runs the server in the same
 * process - and so does the dev client, which publishes every world it enters. On
 * a client connected to somebody else's server those statics are a private copy no
 * decorator will ever read, so the decoration half is greyed out and says so
 * rather than moving a number to no effect. The ambience half stays live there,
 * because it was never the server's business.</p>
 *
 * <h2>Getting a number out again</h2>
 *
 * <p>"Copy values" writes every knob that has moved off its shipped default onto
 * the clipboard and into the log, as lines that can be pasted into the source with
 * the old value in a comment beside them. That is the whole workflow: the panel
 * finds a number, the source keeps it. Nothing here is persisted, and a restart
 * loses everything - which is correct for an instrument and would be a bug for a
 * config.</p>
 *
 * <p>Development tool. Not localised, and reachable only from
 * {@link BurrowTuneCommand}, which registers nothing outside a development
 * run.</p>
 */
public final class BurrowTunePanel extends Screen {

    private static final int PANEL_WIDTH = 176;
    private static final int MARGIN = 8;
    private static final int WIDGET_HEIGHT = 20;
    private static final int ROW_HEIGHT = 22;
    private static final int HEADING_HEIGHT = 13;
    private static final int GAP = 4;

    /** Rows a wheel notch moves the list. Three, because the list is a hundred rows long. */
    private static final int WHEEL_ROWS = 3;

    /** Chat lines "copy values" is willing to spend before deferring to the log. */
    private static final int CHAT_LINE_BUDGET = 12;

    private static final int COLOUR_TITLE = 0xFFFFFFFF;
    private static final int COLOUR_HEADING = 0xFFE0B060;
    private static final int COLOUR_NOTE = 0xFF8894A0;
    private static final int COLOUR_WARN = 0xFFD08040;

    /** One line of the scrolling list: either a heading or a slider, never both. */
    private static final class Row {

        private final String heading;
        private final KnobSlider slider;
        private final String section;
        private final int offset;
        private final int height;

        private int y;
        private boolean shown;

        private Row(String heading, KnobSlider slider, String section, int offset, int height) {
            this.heading = heading;
            this.slider = slider;
            this.section = section;
            this.offset = offset;
            this.height = height;
        }
    }

    private final List<Row> rows = new ArrayList<>();

    /** Where each section's heading starts, so the arrows can jump between them. */
    private final List<Integer> headingOffsets = new ArrayList<>();

    private int listTop;
    private int listBottom;
    private int contentHeight;
    private int maxScroll;

    /** Kept across {@code init}, so a window resize does not throw the reader back to the top. */
    private int scroll;

    private String currentSection = "";
    private boolean ownsServer;
    private boolean announced;

    public BurrowTunePanel() {
        super(Component.literal("Burrow"));
    }

    @Override
    protected void init() {
        this.ownsServer = BurrowRedress.ownsTheServer();
        this.rows.clear();
        this.headingOffsets.clear();

        int full = PANEL_WIDTH - 2 * MARGIN;
        int half = (full - GAP) / 2;
        int arrow = 18;

        addRenderableWidget(Button.builder(Component.literal("<"), button -> jump(-1))
                .bounds(MARGIN, 18, arrow, WIDGET_HEIGHT).build());
        addRenderableWidget(Button.builder(Component.literal(">"), button -> jump(1))
                .bounds(MARGIN + full - arrow, 18, arrow, WIDGET_HEIGHT).build());

        this.listTop = 18 + WIDGET_HEIGHT + 6;
        int footerTop = this.height - MARGIN - 2 * ROW_HEIGHT;
        this.listBottom = footerTop - 12;

        int offset = 0;
        for (Section section : BurrowKnobs.sections()) {
            this.headingOffsets.add(offset);
            this.rows.add(new Row(section.title(), null, section.title(), offset, HEADING_HEIGHT));
            offset += HEADING_HEIGHT;

            boolean live = !section.serverSide() || this.ownsServer;
            for (BurrowKnob knob : section.knobs()) {
                KnobSlider slider = new KnobSlider(MARGIN, this.listTop, full, knob);
                slider.active = live;
                slider.setTooltip(Tooltip.create(Component.literal(live
                        ? knob.origin()
                        : knob.origin() + "\nServer side: this client does not run the burrow.")));
                addRenderableWidget(slider);
                this.rows.add(new Row(null, slider, section.title(), offset, ROW_HEIGHT));
                offset += ROW_HEIGHT;
            }
        }
        this.contentHeight = offset;

        // Left pressable even on a remote client, unlike the sliders above it. A
        // greyed out slider says everything a slider can say; a button that
        // refuses has room to say why, and this is the one thing in the panel
        // somebody will try first and wonder about.
        addRenderableWidget(Button.builder(Component.literal("re-dress nearby (additive)"),
                        button -> BurrowRedress.redress())
                .tooltip(Tooltip.create(Component.literal(
                        "Runs the decorator over the corridor around you again.\n"
                                + "It can only add: a lowered density needs fresh corridor.")))
                .bounds(MARGIN, footerTop, full, WIDGET_HEIGHT).build());

        addRenderableWidget(Button.builder(Component.literal("copy values"), button -> copyValues())
                .tooltip(Tooltip.create(Component.literal(
                        "Every knob that has moved, as source lines,\non the clipboard and in the log.")))
                .bounds(MARGIN, footerTop + ROW_HEIGHT, half, WIDGET_HEIGHT).build());

        addRenderableWidget(Button.builder(Component.literal("reset"), button -> {
                    BurrowKnobs.reset();
                    rebuildWidgets();
                })
                .tooltip(Tooltip.create(Component.literal("Back to the shipped values.")))
                .bounds(MARGIN + half + GAP, footerTop + ROW_HEIGHT, half, WIDGET_HEIGHT).build());

        layout();

        if (!this.ownsServer && !this.announced) {
            this.announced = true;
            BurrowRedress.say("Burrow panel: the decoration sliders are inert on a client that "
                    + "does not run the world. The ambience ones work.");
        }
    }

    /**
     * Puts every row where the current scroll says it goes, and hides the ones
     * that would be cut off.
     *
     * <p>Hiding rather than clipping: an invisible widget takes no click and draws
     * nothing, so a half row at the edge of the list can neither be dragged by
     * accident nor spill over the buttons under it. It costs a row of empty space
     * at the bottom, which is cheaper than a scissor rectangle that would have to
     * be lifted again for the footer.</p>
     */
    private void layout() {
        int view = Math.max(0, this.listBottom - this.listTop);
        this.maxScroll = Math.max(0, this.contentHeight - view);
        this.scroll = Mth.clamp(this.scroll, 0, this.maxScroll);

        String top = null;
        for (Row row : this.rows) {
            row.y = this.listTop - this.scroll + row.offset;
            row.shown = row.y >= this.listTop && row.y + row.height <= this.listBottom;
            if (row.slider != null) {
                row.slider.visible = row.shown;
                row.slider.setY(row.y + 1);
            }
            if (row.shown && top == null) {
                top = row.section;
            }
        }
        this.currentSection = top != null ? top : this.rows.isEmpty() ? "" : this.rows.getFirst().section;
    }

    /** Scrolls to the previous or next section heading. */
    private void jump(int direction) {
        if (this.headingOffsets.isEmpty()) {
            return;
        }
        int index = 0;
        for (int i = 0; i < this.headingOffsets.size(); i++) {
            if (this.headingOffsets.get(i) <= this.scroll + 1) {
                index = i;
            }
        }
        this.scroll = this.headingOffsets.get(
                Mth.clamp(index + direction, 0, this.headingOffsets.size() - 1));
        layout();
    }

    /**
     * Writes every moved knob out as pasteable source lines.
     *
     * <p>Three places, because they answer different questions: the clipboard is
     * the one that ends up in the file, the log is the one that survives the
     * session, and the chat is the one that says it worked without leaving the
     * game. Chat gets a budget - past a dozen lines the list stops being something
     * anybody reads out of a chat window.</p>
     */
    private void copyValues() {
        List<String> lines = BurrowKnobs.changedLines();
        if (lines.isEmpty()) {
            BurrowRedress.say("Nothing has moved - every knob is on its shipped value.");
            return;
        }

        String text = String.join(System.lineSeparator(), lines);
        Minecraft.getInstance().keyboardHandler.setClipboard(text);
        Moleverse.LOGGER.info("Burrow tuning: {} value(s) off the shipped defaults{}{}",
                lines.size(), System.lineSeparator(), text);

        BurrowRedress.say(lines.size() + " value(s) copied to the clipboard and written to the log:");
        for (int i = 0; i < Math.min(lines.size(), CHAT_LINE_BUDGET); i++) {
            BurrowRedress.say("  " + lines.get(i));
        }
        if (lines.size() > CHAT_LINE_BUDGET) {
            BurrowRedress.say("  ... and " + (lines.size() - CHAT_LINE_BUDGET) + " more, in the log.");
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX < PANEL_WIDTH && this.maxScroll > 0 && scrollY != 0.0) {
            this.scroll = Mth.clamp(
                    this.scroll - (int) Math.signum(scrollY) * WHEEL_ROWS * ROW_HEIGHT,
                    0, this.maxScroll);
            layout();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    /** Panel background only. Dimming the world would hide what is being tuned. */
    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.fill(0, 0, PANEL_WIDTH, this.height, 0xA0101014);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        guiGraphics.drawString(this.font, this.title, MARGIN, MARGIN, COLOUR_TITLE);

        // The section name sits between the two arrows rather than on a button of
        // its own: it is a label, and a label that can be pressed is a lie.
        int nameWidth = this.font.width(this.currentSection);
        guiGraphics.drawString(this.font, this.currentSection,
                (PANEL_WIDTH - nameWidth) / 2, 18 + (WIDGET_HEIGHT - 8) / 2, COLOUR_TITLE);

        for (Row row : this.rows) {
            if (row.shown && row.heading != null) {
                guiGraphics.drawString(this.font, row.heading, MARGIN, row.y + 3, COLOUR_HEADING);
                guiGraphics.fill(MARGIN, row.y + HEADING_HEIGHT - 1,
                        PANEL_WIDTH - MARGIN, row.y + HEADING_HEIGHT, 0x40FFFFFF);
            }
        }

        guiGraphics.drawString(this.font,
                this.ownsServer ? "our world - everything live" : "remote server - ambience only",
                MARGIN, this.listBottom + 2, this.ownsServer ? COLOUR_NOTE : COLOUR_WARN);
    }

    /** Keeps the world ticking and rendering while the panel is open. */
    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** A slider over one {@link BurrowKnob}, which is where it reads and writes. */
    private static final class KnobSlider extends AbstractSliderButton {

        private final BurrowKnob knob;

        private KnobSlider(int x, int y, int width, BurrowKnob knob) {
            super(x, y, width, WIDGET_HEIGHT, Component.empty(), Mth.clamp(knob.fraction(), 0.0, 1.0));
            this.knob = knob;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal(this.knob.text()));
        }

        /**
         * Writes the value through, then puts the handle on whatever the field
         * could actually hold.
         *
         * <p>The second half is only visible on the integer knobs, and it is the
         * half that matters: a handle resting between two whole numbers claims a
         * precision the field does not have, and the value it shows is not the
         * value the burrow is using.</p>
         */
        @Override
        protected void applyValue() {
            double span = this.knob.max() - this.knob.min();
            double settled = this.knob.set(this.knob.min() + this.value * span);
            if (span > 0.0) {
                this.value = Mth.clamp((settled - this.knob.min()) / span, 0.0, 1.0);
            }
        }
    }
}
