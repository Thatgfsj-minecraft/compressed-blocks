package dev.compressedblocks;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

/**
 * 滚动容器界面（压缩箱子/压缩潜影盒共用）：每页 6 行 + 右侧滚动条。
 * 视觉与原版 6 行大箱完全一致：背景直接 blit 原版 generic_54（原版 ContainerScreen
 * 同款两段式），滚动条用原版创造栏/列表同款精灵图；右侧轨道为原版灰面板延伸。
 * 滚动纯客户端（菜单槽固定映射，滚动只是挪取景窗）：滚轮逐行、点轨道上下翻页、
 * 按住滑块拖动连续跟随，零网络零闪烁。
 * 26.3：GuiGraphics/render/renderBg 移除——屏幕改为 extract 管线，背景在
 * extractRenderState 里直接向 GuiGraphicsExtractor 提交绘制指令。
 */
public class ScrollingContainerScreen extends AbstractContainerScreen<ScrollingContainerMenu> {
    /** 原版 6 行箱子背景（generic_54：v0..rows*18+17=顶帽+行带，v126 起为背包区）。 */
    private static final Identifier BACKGROUND = Identifier.withDefaultNamespace("textures/gui/container/generic_54.png");
    /** 原版创造栏滚动条（12×15）与列表轨道底（同款精灵）。 */
    private static final Identifier SCROLLER = Identifier.withDefaultNamespace("container/creative_inventory/scroller");
    private static final Identifier SCROLLER_BG = Identifier.withDefaultNamespace("widget/scroller_background");
    private static final int PANEL_W = 176;
    /** 原版灰面板色（198,198,198）。 */
    private static final int VANILLA_GRAY = 0xFFC6C6C6;
    private static final int TRACK_X = 178, TRACK_Y = 18, TRACK_W = 12, TRACK_H = 106;
    private static final int THUMB_H = 15;
    /** 拖动滑块时记录抓取偏移（鼠标y-滑块顶）；-1 = 未在拖动。 */
    private double dragOffset = -1;

    public ScrollingContainerScreen(ScrollingContainerMenu menu, Inventory inv, Component title) {
        // 26.3：imageWidth/imageHeight 变 final，经构造器传入
        super(menu, inv, title, 194, 222);
        this.inventoryLabelY = 128;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float partialTick) {
        // 先铺背景（与旧 renderBg 等价），再让基类提取槽位/标签/悬浮提示
        // 原版 ContainerScreen 两段式背景（6 行窗口 + 背包区），像素级与原版大箱一致
        gfx.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND,
            this.leftPos, this.topPos, 0.0F, 0.0F, PANEL_W,
            ScrollingContainerMenu.VISIBLE_ROWS * 18 + 17, 256, 256);
        gfx.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND,
            this.leftPos, this.topPos + ScrollingContainerMenu.VISIBLE_ROWS * 18 + 17,
            0.0F, 126.0F, PANEL_W, 96, 256, 256);
        // 右侧轨道面板：原版灰延伸 + 黑外边（顶/右/底）
        int rx = this.leftPos + PANEL_W;
        gfx.fill(rx, this.topPos, rx + 17, this.topPos + 1, 0xFF000000);
        gfx.fill(rx, this.topPos + 1, rx + 17, this.topPos + 221, VANILLA_GRAY);
        gfx.fill(rx + 16, this.topPos, rx + 17, this.topPos + 222, 0xFF000000);
        gfx.fill(rx, this.topPos + 221, rx + 17, this.topPos + 222, 0xFF000000);
        // 滚动条：原版精灵轨道 + 滑块（按客户端取景窗位置绘制）
        gfx.blitSprite(RenderPipelines.GUI_TEXTURED, SCROLLER_BG,
            this.leftPos + TRACK_X, this.topPos + TRACK_Y, TRACK_W, TRACK_H);
        gfx.blitSprite(RenderPipelines.GUI_TEXTURED, SCROLLER,
            this.leftPos + TRACK_X, this.thumbTop(), TRACK_W, THUMB_H);
        super.extractRenderState(gfx, mouseX, mouseY, partialTick);
    }

    @Override
    protected void init() {
        super.init();
        // 与 Item Scroller 的"悬停槽位+滚轮=搬移物品"冲突：本界面滚轮专职翻页，
        // 自动把本屏类名加入其 GUI 黑名单（幂等，未装该 mod 时静默跳过）。
        // 想恢复其滚轮搬移：从 itemscroller 配置的 guiBlacklist 移除本类名即可。
        try {
            Class<?> configs = Class.forName("fi.dy.masa.itemscroller.config.Configs");
            @SuppressWarnings("unchecked")
            java.util.Set<String> blacklist = (java.util.Set<String>)
                configs.getField("GUI_BLACKLIST").get(null);
            blacklist.add("dev.compressedblocks.ScrollingContainerScreen");
        } catch (ClassNotFoundException | NoSuchFieldException
                 | IllegalAccessException | ClassCastException ignored) {
            // 未安装 Item Scroller：无事发生
        }
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
        // 仅在界面面板区域内滚动翻页（悬停在界面外/热栏时不误翻）
        if (mouseX < this.leftPos || mouseX >= this.leftPos + this.imageWidth
            || mouseY < this.topPos || mouseY >= this.topPos + this.imageHeight) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
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
            // 点击轨道翻页后把抓取偏移夹进滑块内，避免随后一次拖动跳变
            this.dragOffset = Math.max(0, Math.min(THUMB_H, mouseY - this.thumbTop()));
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
