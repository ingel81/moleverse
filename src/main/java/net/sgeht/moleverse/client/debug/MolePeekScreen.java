package net.sgeht.moleverse.client.debug;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.sgeht.moleverse.debug.MoleDebug;

/**
 * Tuning panel for the mole's poses: the rearing one it is named after, and the
 * dig aim that points the direction-neutral digging cycle somewhere. The two
 * one-shot animations are played from here as well, which is the only way to see
 * them before the burrowing state machine exists.
 *
 * <p>A narrow strip on the left, so the mole stays visible while a slider is
 * being dragged. That is the whole point: the numbers only mean something next
 * to what they do, and reading them off a paused, dimmed screen defeats the
 * purpose. Hence {@link #isPauseScreen()} returning false and a background that
 * covers the panel only.</p>
 *
 * <p>Development tool. It is not localised and not meant to ship.</p>
 */
public class MolePeekScreen extends Screen {

    private static final int PANEL_WIDTH = 150;
    private static final int MARGIN = 8;
    private static final int ROW_HEIGHT = 22;
    private static final int WIDGET_HEIGHT = 20;
    private static final int GAP = 4;

    public MolePeekScreen() {
        super(Component.literal("Mole poses"));
    }

    @Override
    protected void init() {
        int x = MARGIN;
        int y = MARGIN + 16;
        int full = PANEL_WIDTH - 2 * MARGIN;
        int half = (full - GAP) / 2;

        addRenderableWidget(new TuningSlider(x, y, "Peek pitch", -180.0F, 180.0F,
                MoleDebug.peekPitchDegrees, v -> MoleDebug.peekPitchDegrees = v));
        y += ROW_HEIGHT;

        addRenderableWidget(new TuningSlider(x, y, "Peek Y", -32.0F, 32.0F,
                MoleDebug.peekOffsetY, v -> MoleDebug.peekOffsetY = v));
        y += ROW_HEIGHT;

        addRenderableWidget(new TuningSlider(x, y, "Peek Z", -32.0F, 32.0F,
                MoleDebug.peekOffsetZ, v -> MoleDebug.peekOffsetZ = v));
        y += ROW_HEIGHT;

        addRenderableWidget(Button.builder(peekLabel(), button -> {
            MoleDebug.forcePeek = !MoleDebug.forcePeek;
            button.setMessage(peekLabel());
        }).bounds(x, y, full, WIDGET_HEIGHT).build());
        y += ROW_HEIGHT;

        addRenderableWidget(new TuningSlider(x, y, "Dig pitch", -180.0F, 180.0F,
                MoleDebug.digPitchDegrees, v -> MoleDebug.digPitchDegrees = v));
        y += ROW_HEIGHT;

        addRenderableWidget(new TuningSlider(x, y, "Dig yaw", -180.0F, 180.0F,
                MoleDebug.digYawDegrees, v -> MoleDebug.digYawDegrees = v));
        y += ROW_HEIGHT;

        addRenderableWidget(Button.builder(digLabel(), button -> {
            MoleDebug.forceDig = !MoleDebug.forceDig;
            button.setMessage(digLabel());
        }).bounds(x, y, full, WIDGET_HEIGHT).build());
        y += ROW_HEIGHT;

        // Side by side: two one-shots are worth one row, not two.
        addRenderableWidget(Button.builder(Component.literal("Burrow"), button -> MoleDebug.playBurrow())
                .bounds(x, y, half, WIDGET_HEIGHT).build());
        addRenderableWidget(Button.builder(Component.literal("Emerge"), button -> MoleDebug.playEmerge())
                .bounds(x + half + GAP, y, half, WIDGET_HEIGHT).build());
        y += ROW_HEIGHT;

        addRenderableWidget(Button.builder(Component.literal("Reset"), button -> {
            MoleDebug.reset();
            rebuildWidgets();
        }).bounds(x, y, full, WIDGET_HEIGHT).build());
    }

    private static Component peekLabel() {
        return Component.literal("Hold peek: " + (MoleDebug.forcePeek ? "on" : "off"));
    }

    private static Component digLabel() {
        return Component.literal("Hold dig: " + (MoleDebug.forceDig ? "on" : "off"));
    }

    /** Panel background only. Dimming the world would hide what is being tuned. */
    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.fill(0, 0, PANEL_WIDTH, this.height, 0xA0101014);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawString(this.font, this.title, MARGIN, MARGIN, 0xFFFFFF);
        guiGraphics.drawString(this.font, "16 units = 1 block",
                MARGIN, this.height - MARGIN - 9, 0xFF8894A0);
    }

    /** Keeps the world ticking and rendering while the panel is open. */
    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** A slider over a float range that writes straight into {@link MoleDebug}. */
    private static final class TuningSlider extends AbstractSliderButton {

        private final String label;
        private final float min;
        private final float max;
        private final java.util.function.Consumer<Float> sink;

        private TuningSlider(int x, int y, String label, float min, float max,
                float initial, java.util.function.Consumer<Float> sink) {
            super(x, y, PANEL_WIDTH - 2 * MARGIN, WIDGET_HEIGHT, Component.empty(),
                    Mth.clamp((initial - min) / (max - min), 0.0F, 1.0F));
            this.label = label;
            this.min = min;
            this.max = max;
            this.sink = sink;
            updateMessage();
        }

        private float currentValue() {
            return this.min + (float) this.value * (this.max - this.min);
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal(String.format("%s: %.1f", this.label, currentValue())));
        }

        @Override
        protected void applyValue() {
            this.sink.accept(currentValue());
        }
    }
}
