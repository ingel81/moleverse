package net.sgeht.moleverse.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.sgeht.moleverse.menu.ExchangeStationMenu;

/**
 * The exchange station's screen.
 *
 * <p>The dispenser's background, unaltered. Its three-by-three grid is exactly
 * the nine slots the station has, so a texture of our own would draw the same
 * frames in the same places - and a hand-drawn one would be a 176x166 image to
 * keep in step with every later change to the slot count. The top row is the
 * input and the six under it are the output; which is which is told by the
 * slots refusing a click, not by the picture, so nothing is lost by borrowing
 * it.</p>
 */
public class ExchangeStationScreen extends AbstractContainerScreen<ExchangeStationMenu> {

    private static final Identifier BACKGROUND =
            Identifier.withDefaultNamespace("textures/gui/container/dispenser.png");

    /** The texture sheet is 256x256 with the 176x166 window in its top-left corner. */
    private static final int SHEET_SIZE = 256;

    public ExchangeStationScreen(ExchangeStationMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    protected void init() {
        super.init();
        // The dispenser centres its title; a left-aligned one collides with the grid.
        this.titleLabelX = (this.imageWidth - this.font.width(this.title)) / 2;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int left = (this.width - this.imageWidth) / 2;
        int top = (this.height - this.imageHeight) / 2;
        graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, left, top,
                0.0F, 0.0F, this.imageWidth, this.imageHeight, SHEET_SIZE, SHEET_SIZE);
    }
}
