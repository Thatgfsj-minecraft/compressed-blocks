package dev.compressedblocks;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/**
 * 压缩熔炉界面（C1 占位版）：原版顶边 + 灰面板物品区 + 原版背包区两段拼接。
 * C2 再补每路进度条/燃烧池指示的程序化绘制。
 */
public class CompressedFurnaceScreen extends AbstractContainerScreen<CompressedFurnaceMenu> {
    private static final Identifier BACKGROUND = Identifier.withDefaultNamespace("textures/gui/container/generic_54.png");
    private static final int BODY_TOP = 17;
    private static final int PANEL_GRAY = 0xFFC6C6C6;
    private static final int EDGE_LIGHT = 0xFFFFFFFF;
    private static final int EDGE_DARK = 0xFF555555;
    private static final int SLOT_DARK = 0xFF373737;
    private static final int SLOT_FILL = 0xFF8B8B8B;

    public CompressedFurnaceScreen(CompressedFurnaceMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = menu.imageWidth();
        this.imageHeight = menu.imageHeight();
        this.inventoryLabelY = menu.inventoryLabelY();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int bodyHeight = this.menu.bodyHeight();
        // 顶边（标题区）
        graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, this.leftPos, this.topPos,
            0.0F, 0.0F, this.imageWidth, BODY_TOP, 256, 256);
        // 物品区：灰面板 + 左右边线
        int bodyTop = this.topPos + BODY_TOP;
        graphics.fill(this.leftPos, bodyTop, this.leftPos + this.imageWidth, bodyTop + bodyHeight, PANEL_GRAY);
        graphics.fill(this.leftPos, bodyTop, this.leftPos + 1, bodyTop + bodyHeight, EDGE_LIGHT);
        graphics.fill(this.leftPos + this.imageWidth - 1, bodyTop,
            this.leftPos + this.imageWidth, bodyTop + bodyHeight, EDGE_DARK);
        for (int i = 0; i < CompressedFurnaceData.slotCount(this.menu.lanes()); i++) {
            drawSlot(graphics, this.leftPos + this.menu.getSlot(i).x, this.topPos + this.menu.getSlot(i).y);
        }
        // 背包区
        graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND,
            this.leftPos, bodyTop + bodyHeight, 0.0F, 126.0F, this.imageWidth, 96, 256, 256);
    }

    private static void drawSlot(GuiGraphics graphics, int x, int y) {
        graphics.fill(x - 1, y - 1, x + 17, y + 17, SLOT_DARK);
        graphics.fill(x, y, x + 16, y + 16, SLOT_FILL);
        graphics.fill(x - 1, y + 16, x + 17, y + 17, EDGE_LIGHT);
        graphics.fill(x + 16, y - 1, x + 17, y + 17, EDGE_LIGHT);
    }
}
