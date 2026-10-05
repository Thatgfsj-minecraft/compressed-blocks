package dev.compressedblocks;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.inventory.Container;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * 滚动容器界面（压缩箱子/压缩潜影盒共用）：每页 6 行 + 右侧自绘滚动条。
 * 背景直接 blit 原版 generic_54（6 行大箱同款两段式）；滚动条用.drawRect 自绘（灰轨道+深滑块）。
 * 滚动纯客户端（菜单槽固定映射，滚动只是挪取景窗）：
 * 1.12.2 事件钩子：handleMouseInput 滚轮（org.lwjgl.input.Mouse）、
 * mouseClicked 轨道翻页、mouseClickMove 滑块拖动、mouseReleased 结束，零网络零闪烁。
 */
@SideOnly(Side.CLIENT)
public class ScrollingContainerScreen extends GuiContainer {
    /** 原版 6 行箱子背景（generic_54：v0..125 顶帽+行带，v126 起为背包区）。 */
    private static final ResourceLocation BACKGROUND =
        new ResourceLocation("minecraft", "textures/gui/container/generic_54.png");
    private static final int PANEL_W = 176;
    /** 原版灰面板色（198,198,198）。 */
    private static final int VANILLA_GRAY = 0xFFC6C6C6;
    private static final int TRACK_X = 178, TRACK_Y = 18, TRACK_W = 12, TRACK_H = 106;
    private static final int THUMB_H = 15;
    /** 拖动滑块时记录抓取偏移（鼠标y-滑块顶）；-1 = 未在拖动。 */
    private double dragOffset = -1;

    public ScrollingContainerScreen(Container container) {
        super(container);
        this.xSize = 194;
        this.ySize = 222;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTick) {
        super.drawScreen(mouseX, mouseY, partialTick);
        this.renderHoveredToolTip(mouseX, mouseY);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTick, int mouseX, int mouseY) {
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        this.mc.getTextureManager().bindTexture(BACKGROUND);
        // 原版两段式背景（6 行窗口 + 背包区），像素级与原版大箱一致
        this.drawTexturedModalRect(this.guiLeft, this.guiTop, 0, 0, PANEL_W,
            ScrollingContainerMenu.VISIBLE_ROWS * 18 + 17);
        this.drawTexturedModalRect(this.guiLeft, this.guiTop + ScrollingContainerMenu.VISIBLE_ROWS * 18 + 17,
            0, 126, PANEL_W, 96);
        // 右侧轨道面板：原版灰延伸 + 黑外边（1.12.2 用 drawRect）
        int rx = this.guiLeft + PANEL_W;
        drawRect(rx, this.guiTop, rx + 17, this.guiTop + 1, 0xFF000000);
        drawRect(rx, this.guiTop + 1, rx + 17, this.guiTop + 221, VANILLA_GRAY);
        drawRect(rx + 16, this.guiTop, rx + 17, this.guiTop + 222, 0xFF000000);
        drawRect(rx, this.guiTop + 221, rx + 17, this.guiTop + 222, 0xFF000000);
        // 滑块（深灰，按客户端取景窗位置绘制）
        int thumb = this.thumbTop();
        drawRect(this.guiLeft + TRACK_X + 1, thumb, this.guiLeft + TRACK_X + TRACK_W - 1,
            thumb + THUMB_H, 0xFF555555);
        drawRect(this.guiLeft + TRACK_X + 2, thumb + 1, this.guiLeft + TRACK_X + TRACK_W - 2,
            thumb + THUMB_H - 1, 0xFF8B8B8B);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private int thumbTop() {
        return this.guiTop + TRACK_Y
            + menu().getScrollRow() * (TRACK_H - THUMB_H) / ScrollingContainerMenu.MAX_ROW;
    }

    /** 鼠标纵坐标 → 取景窗首行。 */
    private int rowAt(double mouseY) {
        return Math.round((float) (mouseY - (this.guiTop + TRACK_Y) - THUMB_H / 2.0)
            / (TRACK_H - THUMB_H) * ScrollingContainerMenu.MAX_ROW);
    }

    private ScrollingContainerMenu menu() {
        return (ScrollingContainerMenu) this.inventorySlots;
    }

    /** 1.12.2 滚轮入口：GuiScreen.handleMouseInput 中读 Mouse.getEventDWheel()；
     *  1.12.2 无 mouseX/mouseY 字段，鼠标位置现场换算（与 handleInput 同公式）。 */
    @Override
    public void handleMouseInput() throws java.io.IOException {
        super.handleMouseInput();
        int wheel = org.lwjgl.input.Mouse.getEventDWheel();
        if (wheel != 0) {
            int mx = org.lwjgl.input.Mouse.getEventX() * this.width / this.mc.displayWidth;
            int my = this.height - org.lwjgl.input.Mouse.getEventY() * this.height / this.mc.displayHeight - 1;
            // 仅在界面面板区域内滚动翻页（悬停在界面外/热栏时不误翻）
            if (mx >= this.guiLeft && mx < this.guiLeft + this.xSize
                && my >= this.guiTop && my < this.guiTop + this.ySize) {
                menu().setScrollRowLocal(menu().getScrollRow() + (wheel > 0 ? -1 : 1));
            }
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws java.io.IOException {
        int tx = this.guiLeft + TRACK_X, ty = this.guiTop + TRACK_Y;
        if (mouseX >= tx && mouseX < tx + TRACK_W && mouseY >= ty && mouseY < ty + TRACK_H) {
            int thumb = this.thumbTop();
            if (mouseY < thumb || mouseY >= thumb + THUMB_H) {
                // 点击轨道空白：整窗翻页（上半=上翻、下半=下翻）
                menu().setScrollRowLocal(menu().getScrollRow()
                    + (mouseY < thumb ? -ScrollingContainerMenu.VISIBLE_ROWS
                                      : ScrollingContainerMenu.VISIBLE_ROWS));
            }
            // 点击轨道翻页后把抓取偏移夹进滑块内，避免随后一次拖动跳变
            this.dragOffset = Math.max(0, Math.min(THUMB_H, mouseY - this.thumbTop()));
            return;
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (this.dragOffset >= 0) {
            menu().setScrollRowLocal(this.rowAt(mouseY - this.dragOffset));
            return;
        }
        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        this.dragOffset = -1;
        super.mouseReleased(mouseX, mouseY, state);
    }
}
