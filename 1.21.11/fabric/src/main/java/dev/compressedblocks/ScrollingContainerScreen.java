package dev.compressedblocks;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

/**
 * 滚动容器界面（压缩箱子/压缩潜影盒共用）：每页 6 行 + 右侧滚动条。
 * 滚动纯客户端（菜单槽固定映射，滚动只是挪取景窗）：滚轮逐行、点轨道上下翻页、
 * 按住滑块拖动连续跟随，零网络零闪烁。
 */
public class ScrollingContainerScreen extends AbstractContainerScreen<ScrollingContainerMenu> {
    private static final Identifier BACKGROUND =
        Identifier.fromNamespaceAndPath(CompressedBlocks.MOD_ID, "textures/gui/compressed_container.png");
    private static final int TRACK_X = 174, TRACK_Y = 17, TRACK_W = 12, TRACK_H = 108;
    private static final int THUMB_H = 24;
    /** 拖动滑块时记录抓取偏移（鼠标y-滑块顶）；-1 = 未在拖动。 */
    private double dragOffset = -1;

    public ScrollingContainerScreen(ScrollingContainerMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 194;
        this.imageHeight = 222;
        this.inventoryLabelY = 128;
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        super.render(gfx, mouseX, mouseY, partialTick);
        this.renderTooltip(gfx, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics gfx, float partialTick, int mouseX, int mouseY) {
        gfx.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND,
            this.leftPos, this.topPos, 0.0F, 0.0F, this.imageWidth, this.imageHeight, 256, 256);
        // 滚动条：轨道 + 滑块（按客户端取景窗位置绘制）
        int tx = this.leftPos + TRACK_X, ty = this.topPos + TRACK_Y;
        gfx.fill(tx, ty, tx + TRACK_W, ty + TRACK_H, 0xFF8B8B8B);
        int thumbY = this.thumbTop();
        gfx.fill(tx + 1, thumbY, tx + TRACK_W - 1, thumbY + THUMB_H, 0xFFF0F0F0);
    }

    private int thumbTop() {
        return this.topPos + TRACK_Y
            + this.menu.getScrollRow() * (TRACK_H - THUMB_H) / ScrollingContainerMenu.MAX_ROW;
    }

    /** 鼠标纵坐标 → 取景窗首行。 */
    private int rowAt(double mouseY) {
        return Math.round((float) (mouseY - (this.topPos + TRACK_Y) - THUMB_H / 2.0)
            / (TRACK_H - THUMB_H) * ScrollingContainerMenu.MAX_ROW);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        this.menu.setScrollRowLocal(this.menu.getScrollRow() + (scrollY > 0 ? -1 : 1));
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        double mouseX = event.x(), mouseY = event.y();
        int tx = this.leftPos + TRACK_X, ty = this.topPos + TRACK_Y;
        if (mouseX >= tx && mouseX < tx + TRACK_W && mouseY >= ty && mouseY < ty + TRACK_H) {
            int thumb = this.thumbTop();
            if (mouseY < thumb || mouseY >= thumb + THUMB_H) {
                // 点击轨道空白：整窗翻页（上半=上翻、下半=下翻）
                this.menu.setScrollRowLocal(this.menu.getScrollRow()
                    + (mouseY < thumb ? -ScrollingContainerMenu.VISIBLE_ROWS
                                      : ScrollingContainerMenu.VISIBLE_ROWS));
            }
            this.dragOffset = mouseY - this.thumbTop();
            return true;
        }
        return super.mouseClicked(event, doubled);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (this.dragOffset >= 0) {
            this.menu.setScrollRowLocal(this.rowAt(event.y() - this.dragOffset));
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        this.dragOffset = -1;
        return super.mouseReleased(event);
    }
}
