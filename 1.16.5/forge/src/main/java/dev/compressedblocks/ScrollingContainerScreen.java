package dev.compressedblocks;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/**
 * 滚动容器界面（压缩箱子/压缩潜影盒共用）：每页 6 行 + 右侧自绘滚动条。
 * 背景直接 blit 原版 generic_54（6 行大箱同款两段式）；滚动条用 fill 自绘（灰色轨道+深色滑块）。
 * 滚动纯客户端（菜单槽固定映射，滚动只是挪取景窗）：滚轮逐行、点轨道上下翻页、
 * 按住滑块拖动连续跟随，零网络零闪烁。
 */
public class ScrollingContainerScreen extends AbstractContainerScreen<ScrollingContainerMenu> {
    /** 原版 6 行箱子背景（generic_54：v0..125 顶帽+行带，v126 起为背包区）。 */
    private static final ResourceLocation BACKGROUND =
        new ResourceLocation("textures/gui/container/generic_54.png");
    private static final int PANEL_W = 176;
    /** 原版灰面板色（198,198,198）。 */
    private static final int VANILLA_GRAY = 0xFFC6C6C6;
    private static final int TRACK_X = 178, TRACK_Y = 18, TRACK_W = 12, TRACK_H = 106;
    private static final int THUMB_H = 15;
    /** 拖动滑块时记录抓取偏移（鼠标y-滑块顶）；-1 = 未在拖动。 */
    private double dragOffset = -1;

    public ScrollingContainerScreen(ScrollingContainerMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 194;
        this.imageHeight = 222;
        this.inventoryLabelY = 129;
    }

    @Override
    public void render(com.mojang.blaze3d.vertex.PoseStack poseStack, int mouseX, int mouseY,
                       float partialTick) {
        super.render(poseStack, mouseX, mouseY, partialTick);
        this.renderTooltip(poseStack, mouseX, mouseY);
    }

    @Override
    protected void renderBg(com.mojang.blaze3d.vertex.PoseStack poseStack, float partialTick,
                            int mouseX, int mouseY) {
        RenderSystem.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        this.minecraft.getTextureManager().bind(BACKGROUND);
        // 原版 ContainerScreen 两段式背景（6 行窗口 + 背包区），像素级与原版大箱一致
        this.blit(poseStack, this.leftPos, this.topPos, 0, 0, PANEL_W,
            ScrollingContainerMenu.VISIBLE_ROWS * 18 + 17);
        this.blit(poseStack, this.leftPos, this.topPos + ScrollingContainerMenu.VISIBLE_ROWS * 18 + 17,
            0, 126, PANEL_W, 96);
        // 右侧轨道面板：原版灰延伸 + 黑外边
        int rx = this.leftPos + PANEL_W;
        this.fill(poseStack, rx, this.topPos, rx + 17, this.topPos + 1, 0xFF000000);
        this.fill(poseStack, rx, this.topPos + 1, rx + 17, this.topPos + 221, VANILLA_GRAY);
        this.fill(poseStack, rx + 16, this.topPos, rx + 17, this.topPos + 222, 0xFF000000);
        this.fill(poseStack, rx, this.topPos + 221, rx + 17, this.topPos + 222, 0xFF000000);
        // 滑块（深灰，按客户端取景窗位置绘制）
        this.fill(poseStack, this.leftPos + TRACK_X + 1, this.thumbTop(), this.leftPos + TRACK_X + TRACK_W - 1,
            this.thumbTop() + THUMB_H, 0xFF555555);
        this.fill(poseStack, this.leftPos + TRACK_X + 2, this.thumbTop() + 1,
            this.leftPos + TRACK_X + TRACK_W - 2, this.thumbTop() + THUMB_H - 1, 0xFF8B8B8B);
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
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        // 仅在界面面板区域内滚动翻页（悬停在界面外/热栏时不误翻）
        if (mouseX < this.leftPos || mouseX >= this.leftPos + this.imageWidth
            || mouseY < this.topPos || mouseY >= this.topPos + this.imageHeight) {
            return super.mouseScrolled(mouseX, mouseY, delta);
        }
        this.menu.setScrollRowLocal(this.menu.getScrollRow() + (delta > 0 ? -1 : 1));
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int tx = this.leftPos + TRACK_X, ty = this.topPos + TRACK_Y;
        if (mouseX >= tx && mouseX < tx + TRACK_W && mouseY >= ty && mouseY < ty + TRACK_H) {
            int thumb = this.thumbTop();
            if (mouseY < thumb || mouseY >= thumb + THUMB_H) {
                // 点击轨道空白：整窗翻页（上半=上翻、下半=下翻）
                this.menu.setScrollRowLocal(this.menu.getScrollRow()
                    + (mouseY < thumb ? -ScrollingContainerMenu.VISIBLE_ROWS
                                      : ScrollingContainerMenu.VISIBLE_ROWS));
            }
            // 点击轨道翻页后把抓取偏移夹进滑块内，避免随后一次拖动跳变
            this.dragOffset = Math.max(0, Math.min(THUMB_H, mouseY - this.thumbTop()));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.dragOffset >= 0) {
            this.menu.setScrollRowLocal(this.rowAt(mouseY - this.dragOffset));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        this.dragOffset = -1;
        return super.mouseReleased(mouseX, mouseY, button);
    }
}
