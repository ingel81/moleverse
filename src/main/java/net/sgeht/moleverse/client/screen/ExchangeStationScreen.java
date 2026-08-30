package net.sgeht.moleverse.client.screen;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.sgeht.moleverse.Moleverse;
import net.sgeht.moleverse.block.entity.ExchangeStationBlockEntity.Tier;
import net.sgeht.moleverse.menu.ExchangeStationMenu;

import org.jetbrains.annotations.Nullable;

/**
 * The exchange station's screen.
 *
 * <p>The station is a crate of boards over a shaft, and this is that crate with
 * the lid off: feed on the left, the shaft in the middle, what came back on the
 * right. It borrowed the dispenser's background until the station learnt to
 * grade its input, which is the point at which a three-by-three grid stopped
 * being able to say what the block does - a grid can hold nine slots and
 * nothing else, and there are now three things to show that are not slots.</p>
 *
 * <p>Those three:</p>
 * <ul>
 * <li><strong>The gauge</strong>, under the feed slots: four sockets, one lit,
 * plus the feed's own icon and the two numbers of its rate. It is drawn from
 * {@link ExchangeStationMenu#grade()}, which asks the block entity's own
 * grading code about the menu's own input handler - so the reading on the
 * client is produced by the same method that will spend the worms on the
 * server, not by a copy of its table.</li>
 * <li><strong>The shaft</strong>, which lights with fresh earth for half a
 * second when a trade happens. A trade is otherwise invisible: it is one mole,
 * underground, behind a shut crate, and the slots would have changed the same
 * way if a player had taken something out.</li>
 * <li><strong>Three area tooltips</strong>, because the rates, the one-armful
 * rule and what the shaft is are all things a player would otherwise have to be
 * told outside the game.</li>
 * </ul>
 *
 * <p>The background and its two overlay sprites come out of
 * {@code art/generators/exchange_gui.py}, which is also where the coordinates
 * below are decided - it prints them with {@code --layout}.</p>
 */
public class ExchangeStationScreen extends AbstractContainerScreen<ExchangeStationMenu> {

    private static final Identifier BACKGROUND = Moleverse.id("textures/gui/exchange_station.png");

    /** The sheet is 256x256 with the 176x166 window in its top-left corner. */
    private static final int SHEET_SIZE = 256;

    // The two sprites parked beside the window, as (u, v) on the sheet.
    private static final int SHAFT_ACTIVE_U = 176;
    private static final int SHAFT_ACTIVE_V = 0;
    private static final int SOCKET_LIT_U = 176;
    private static final int SOCKET_LIT_V = 56;
    private static final int SOCKET_SIZE = 5;

    // Everything below is window-relative and mirrors the generator's LAYOUT.
    private static final int SHAFT_X = 71;
    private static final int SHAFT_Y = 16;
    private static final int SHAFT_WIDTH = 34;
    private static final int SHAFT_HEIGHT = 52;

    private static final int[][] SOCKETS = {{20, 42}, {28, 42}, {36, 42}, {44, 42}};

    private static final int GAUGE_ICON_X = 18;
    private static final int GAUGE_ICON_Y = 49;
    private static final int GAUGE_PRICE_X = 40;
    private static final int GAUGE_PAYOUT_X = 58;
    private static final int GAUGE_TEXT_Y = 52;

    /** The feed slots and the gauge under them, as one hover area. */
    private static final int FEED_X = 15;
    private static final int FEED_Y = 16;
    private static final int FEED_WIDTH = 54;
    private static final int FEED_HEIGHT = 52;

    private static final int FINDS_X = 106;
    private static final int FINDS_Y = 25;
    private static final int FINDS_WIDTH = 54;
    private static final int FINDS_HEIGHT = 36;

    /** Clear of the cross beam at y=70..73, which vanilla's default of 72 would print through. */
    private static final int INVENTORY_LABEL_Y = 75;

    /** Dark enough to read on the boards without being the black vanilla uses on grey. */
    private static final int LABEL_COLOUR = 0xFF231A11;

    /** {@code WOOD[5]}: the gauge plate is a recess, so its numbers are the light ones. */
    private static final int GAUGE_COLOUR = 0xFFC4A474;

    /** Half a second. Long enough to catch out of the corner of an eye, short enough not to queue up. */
    private static final int FLASH_TICKS = 10;

    private static final String KEY = "gui." + Moleverse.MOD_ID + ".exchange_station.";

    /** One stack per tier for the gauge and the tooltip, built once rather than per frame. */
    private final Map<Tier, ItemStack> feedIcons = new EnumMap<>(Tier.class);

    /**
     * The trade counter as last seen. Negative until the first value arrives,
     * which is what stops the shaft flashing for trades that happened before
     * anybody opened the crate.
     */
    private int lastTrades = -1;

    private int flash;

    public ExchangeStationScreen(ExchangeStationMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    protected void init() {
        super.init();
        this.inventoryLabelY = INVENTORY_LABEL_Y;
        for (Tier tier : Tier.values()) {
            this.feedIcons.put(tier, new ItemStack(tier.feed()));
        }
    }

    /**
     * Watches the station's trade counter.
     *
     * <p>A counter rather than an event: a menu can only send numbers, and a
     * number that changed is all the screen needs to know that a mole came up.
     * Which trade it was, and what it paid, are already in the output slots.</p>
     */
    @Override
    protected void containerTick() {
        super.containerTick();
        int trades = this.menu.trades();
        if (trades != this.lastTrades) {
            this.flash = this.lastTrades < 0 ? 0 : FLASH_TICKS;
            this.lastTrades = trades;
        } else if (this.flash > 0) {
            this.flash--;
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
        this.renderAreaTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int left = (this.width - this.imageWidth) / 2;
        int top = (this.height - this.imageHeight) / 2;
        graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, left, top,
                0.0F, 0.0F, this.imageWidth, this.imageHeight, SHEET_SIZE, SHEET_SIZE);

        // Interpolated against the tick that is running, so the fade is smooth
        // at any frame rate rather than in ten visible steps.
        float fade = Math.max(0.0F, (this.flash - partialTick) / FLASH_TICKS);
        if (fade > 0.0F) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND,
                    left + SHAFT_X, top + SHAFT_Y, SHAFT_ACTIVE_U, SHAFT_ACTIVE_V,
                    SHAFT_WIDTH, SHAFT_HEIGHT, SHEET_SIZE, SHEET_SIZE, ARGB.white(fade));
        }

        Tier grade = this.menu.grade();
        if (grade != null) {
            int[] socket = SOCKETS[grade.ordinal()];
            graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND,
                    left + socket[0], top + socket[1], SOCKET_LIT_U, SOCKET_LIT_V,
                    SOCKET_SIZE, SOCKET_SIZE, SHEET_SIZE, SHEET_SIZE);
        }
    }

    /**
     * The labels, and the gauge's reading.
     *
     * <p>Drawn here rather than in {@link #renderBg} because this is the pass
     * the container screen runs with the window's own origin already pushed onto
     * the matrix - so the coordinates are the ones the generator printed, with
     * no {@code leftPos} arithmetic to get wrong twice.</p>
     */
    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, LABEL_COLOUR, false);
        graphics.drawString(this.font, this.playerInventoryTitle,
                this.inventoryLabelX, this.inventoryLabelY, LABEL_COLOUR, false);

        Tier grade = this.menu.grade();
        if (grade == null) {
            return;
        }
        graphics.renderItem(this.feedIcons.get(grade), GAUGE_ICON_X, GAUGE_ICON_Y);
        graphics.drawString(this.font, String.valueOf(grade.price()),
                GAUGE_PRICE_X, GAUGE_TEXT_Y, GAUGE_COLOUR, false);
        graphics.drawString(this.font, String.valueOf(grade.payout()),
                GAUGE_PAYOUT_X, GAUGE_TEXT_Y, GAUGE_COLOUR, false);
    }

    /**
     * The three tooltips that are not a slot's.
     *
     * <p>Stands down whenever the slot under the pointer has something in it:
     * that item's own tooltip has already been asked for by
     * {@code renderTooltip}, and the last caller to set one wins. Hovering an
     * empty feed slot is the case this exists for - it is exactly when a player
     * wants to know what may go in it.</p>
     */
    private void renderAreaTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (this.hoveredSlot != null && this.hoveredSlot.hasItem()) {
            return;
        }
        List<Component> lines = this.areaLines(mouseX, mouseY);
        if (lines != null) {
            graphics.setComponentTooltipForNextFrame(this.font, lines, mouseX, mouseY);
        }
    }

    private @Nullable List<Component> areaLines(int mouseX, int mouseY) {
        if (this.isHovering(FEED_X, FEED_Y, FEED_WIDTH, FEED_HEIGHT, mouseX, mouseY)) {
            return this.feedLines();
        }
        if (this.isHovering(FINDS_X, FINDS_Y, FINDS_WIDTH, FINDS_HEIGHT, mouseX, mouseY)) {
            return List.of(
                    text("finds").withStyle(ChatFormatting.WHITE),
                    text("finds_hint").withStyle(ChatFormatting.GRAY));
        }
        if (this.isHovering(SHAFT_X, SHAFT_Y, SHAFT_WIDTH, SHAFT_HEIGHT, mouseX, mouseY)) {
            return List.of(text("shaft").withStyle(ChatFormatting.GRAY));
        }
        return null;
    }

    /**
     * The whole ladder, poorest first, with the current one picked out.
     *
     * <p>All four every time rather than only the one in the box. The tiers are
     * the mechanic, and a player holding a fat worm for the first time should
     * be able to find out what it is worth by pointing at the crate rather than
     * by feeding one and watching.</p>
     */
    private List<Component> feedLines() {
        Tier grade = this.menu.grade();
        List<Component> lines = new ArrayList<>();
        lines.add(text("feed").withStyle(ChatFormatting.WHITE));
        lines.add(text("feed_hint").withStyle(ChatFormatting.GRAY));
        for (Tier tier : Tier.values()) {
            lines.add(Component.translatable(KEY + "tier",
                            this.feedIcons.get(tier).getHoverName(), tier.price(), tier.payout())
                    .withStyle(tier == grade ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY));
        }
        if (grade == null) {
            lines.add(text("idle").withStyle(ChatFormatting.GRAY));
        }
        return lines;
    }

    private static MutableComponent text(String name) {
        return Component.translatable(KEY + name);
    }
}
