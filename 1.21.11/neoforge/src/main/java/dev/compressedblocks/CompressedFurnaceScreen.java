package dev.compressedblocks;

import java.util.Locale;
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
 * <p>版面：左上输入区（3×R）→ 燃烧剩余时间（如 {@code 132.5s}）→ 左下燃料区（3×R）；
 * 右侧产出区（3×R，垂直居中）。每路烧炼进度画在对应输入槽内部底边（同原版耐久条位置，
 * 覆盖在物品之上），不再单独占用行距。</p>
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
    private static final int BAR_BG = 0xFF000000;
    private static final int BAR_PROGRESS = 0xFFFFC83C;
    private static final int TEXT_DARK = 0xFF3F3F3F;
    /** 每路进度条：与原版耐久条同位置（槽内底部 2px）。 */
    private static final int LANE_BAR_X_OFFSET = 2;
    private static final int LANE_BAR_Y_OFFSET = 13;
    private static final int LANE_BAR_WIDTH = 13;
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
        // 每路烧炼进度：画在物品之上（同原版耐久条位置），避免被物品图标遮住。
        // 只有该路真的在烧（进度 > 0）时才画，空闲的路不留多余横线。
        for (int lane = 0; lane < this.menu.lanes(); lane++) {
            float progress = this.menu.cookProgress(lane);
            if (progress <= 0.0F) {
                continue;
            }
            Slot input = this.menu.getSlot(lane);
            drawBar(graphics,
                this.leftPos + input.x + LANE_BAR_X_OFFSET,
                this.topPos + input.y + LANE_BAR_Y_OFFSET,
                LANE_BAR_WIDTH, LANE_BAR_HEIGHT, progress, BAR_PROGRESS);
        }
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
        // 槽位凹槽（容器槽 0..3N-1，坐标由菜单给出）
        for (int i = 0; i < lanes * 3; i++) {
            Slot slot = this.menu.getSlot(i);
            drawSlot(graphics, this.leftPos + slot.x, this.topPos + slot.y);
        }
        // 整机共享燃烧池剩余时间（原中间燃烧条位置，空出来只放文字；水平居中于 A/C 区块）
        String burnText = String.format(Locale.ROOT, "%.1fs", this.menu.litSeconds());
        int burnTextX = this.leftPos + this.menu.burnInfoCenterX() - this.font.width(burnText) / 2;
        graphics.drawString(this.font, burnText, burnTextX,
            this.topPos + this.menu.burnInfoY(), TEXT_DARK, false);
        // 产出方向箭头：水平居中于 A/C 与 D 之间，垂直居中于 A/C/D 整体
        int arrowX = this.leftPos + this.menu.arrowX();
        int arrowY = this.topPos + this.menu.arrowCenterY() - 2;
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

    /** 2px 状态条：黑底 + 按 0..1 比例填充（同原版耐久条）。 */
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
