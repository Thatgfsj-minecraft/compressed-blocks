package dev.compressedblocks;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * 压缩熔炉界面：不新增贴图，程序化自绘（仿 {@link WoodenHopperScreen}）。
 *
 * <p>背景三段拼接：原版 {@code generic_54} 顶边（v=0,h=17）+ 灰面板物品区（手绘槽位凹槽）
 * + 原版背包区（v=126,h=96）。面板宽固定 176，高度由菜单决定（行数 R = 1/2/3）。</p>
 *
 * <p>版面：左上输入区（3×R）→ 燃烧进度条 → 左下燃料区（3×R）；右侧产出区（3×R，垂直居中）。
 * 每路烧炼进度画在输入槽下方行距多出的 2px 里；燃烧进度条是整机共享燃烧池的剩余比例。</p>
 */
public class CompressedFurnaceScreen extends AbstractContainerScreen<CompressedFurnaceMenu> {
    /** 原版 6 行箱子背景：顶边取 v=0，背包区取 v=126。 */
    private static final Identifier BACKGROUND = Identifier.withDefaultNamespace("textures/gui/container/generic_54.png");
    private static final int BODY_TOP = 17;
    private static final int PANEL_GRAY = 0xFFC6C6C6;
    private static final int EDGE_LIGHT = 0xFFFFFFFF;
    private static final int EDGE_DARK = 0xFF555555;
    private static final int SLOT_DARK = 0xFF373737;
    private static final int SLOT_FILL = 0xFF8B8B8B;
    private static final int BAR_BG = 0xFF373737;
    private static final int BAR_PROGRESS = 0xFFFFC83C;
    private static final int BAR_FLAME = 0xFFFF8A00;
    private static final int LANE_BAR_WIDTH = 16;
    private static final int LANE_BAR_HEIGHT = 2;
    /** 产出区左侧的右向箭头。 */
    private static final int ARROW_X = 96;
    private static final int ARROW_W = 11;

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

        int lanes = this.menu.lanes();
        // 槽位凹槽（容器槽 0..3N-1，顺序为输入区/燃料区/产出区，但坐标由菜单给出）
        for (int i = 0; i < lanes * 3; i++) {
            Slot slot = this.menu.getSlot(i);
            drawSlot(graphics, this.leftPos + slot.x, this.topPos + slot.y);
        }
        // 每路烧炼进度：输入槽下方 2px
        for (int lane = 0; lane < lanes; lane++) {
            Slot input = this.menu.getSlot(lane);
            drawBar(graphics, this.leftPos + input.x - 1, this.topPos + input.y + 17,
                LANE_BAR_WIDTH, LANE_BAR_HEIGHT, this.menu.cookProgress(lane), BAR_PROGRESS);
        }
        // 整机共享燃烧池：输入区与燃料区之间的长条
        int burnX = this.leftPos + this.menu.burnBarX();
        int burnY = this.topPos + this.menu.burnBarY();
        int burnW = this.menu.burnBarWidth();
        int burnH = this.menu.burnBarHeight();
        graphics.fill(burnX, burnY, burnX + burnW, burnY + burnH, BAR_BG);
        float lit = Math.max(0.0F, Math.min(1.0F, this.menu.litProgress()));
        int litFill = Math.round(lit * (float) burnW);
        if (litFill > 0) {
            graphics.fill(burnX, burnY, burnX + litFill, burnY + burnH, BAR_FLAME);
        }
        // 产出方向箭头（与燃烧条同高，指向右侧产出区）
        int arrowX = this.leftPos + ARROW_X;
        int arrowY = burnY + 1;
        drawArrow(graphics, arrowX, arrowY);
        // 背包区
        graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND,
            this.leftPos, bodyTop + bodyHeight, 0.0F, 126.0F, this.imageWidth, 96, 256, 256);
    }

    /** 原版槽位凹槽：左上深色、内部灰、右下白色高光。 */
    private static void drawSlot(GuiGraphics graphics, int x, int y) {
        graphics.fill(x - 1, y - 1, x + 17, y + 17, SLOT_DARK);
        graphics.fill(x, y, x + 16, y + 16, SLOT_FILL);
        graphics.fill(x - 1, y + 16, x + 17, y + 17, EDGE_LIGHT);
        graphics.fill(x + 16, y - 1, x + 17, y + 17, EDGE_LIGHT);
    }

    /** 2px 状态条：深灰底 + 按 0..1 比例填充。 */
    private static void drawBar(GuiGraphics graphics, int x, int y, int width, int height,
                                float progress, int color) {
        graphics.fill(x, y, x + width, y + height, BAR_BG);
        float clamped = Math.max(0.0F, Math.min(1.0F, progress));
        int filled = Math.round(clamped * (float) width);
        if (filled > 0) {
            graphics.fill(x, y, x + filled, y + height, color);
        }
    }

    /** 右向箭头：杆 + 两级箭头。 */
    private static void drawArrow(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y + 2, x + ARROW_W - 2, y + 3, EDGE_DARK);
        graphics.fill(x + ARROW_W - 2, y, x + ARROW_W - 1, y + 5, EDGE_DARK);
        graphics.fill(x + ARROW_W - 1, y + 1, x + ARROW_W, y + 4, EDGE_DARK);
    }
}
