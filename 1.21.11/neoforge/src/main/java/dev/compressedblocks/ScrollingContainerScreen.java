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
 * 滚轮逐行滚；点滚动条轨道跳行。客户端先本地预测滚动再发包，服务端确认后内容整包同步。
 */
public class ScrollingContainerScreen extends AbstractContainerScreen<ScrollingContainerMenu> {
    private static final Identifier BACKGROUND =
        Identifier.fromNamespaceAndPath(CompressedBlocks.MOD_ID, "textures/gui/compressed_container.png");
    private static final int TRACK_X = 174, TRACK_Y = 17, TRACK_W = 12, TRACK_H = 108;
    private static final int THUMB_H = 24;

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
        // 滚动条：轨道 + 滑块（按 scrollRow 绘制）
        int tx = this.leftPos + TRACK_X, ty = this.topPos + TRACK_Y;
        gfx.fill(tx, ty, tx + TRACK_W, ty + TRACK_H, 0xFF8B8B8B);
        int thumbY = ty + this.menu.scrollRow() * (TRACK_H - THUMB_H) / ScrollingContainerMenu.MAX_ROW;
        gfx.fill(tx + 1, thumbY, tx + TRACK_W - 1, thumbY + THUMB_H, 0xFFF0F0F0);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int button = scrollY > 0 ? 0 : 1;
        this.menu.clientScroll(button);
        if (this.minecraft != null && this.minecraft.gameMode != null) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, button);
        }
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        double mouseX = event.x(), mouseY = event.y();
        int tx = this.leftPos + TRACK_X, ty = this.topPos + TRACK_Y;
        if (mouseX >= tx && mouseX < tx + TRACK_W && mouseY >= ty && mouseY < ty + TRACK_H) {
            int row = Math.round((float) (mouseY - ty - THUMB_H / 2) / (TRACK_H - THUMB_H)
                * ScrollingContainerMenu.MAX_ROW);
            this.menu.clientScroll(100 + row);
            if (this.minecraft != null && this.minecraft.gameMode != null) {
                this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, 100 + row);
            }
            return true;
        }
        return super.mouseClicked(event, doubled);
    }
}
