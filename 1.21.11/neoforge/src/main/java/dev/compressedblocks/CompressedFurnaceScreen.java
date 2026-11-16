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
 * + 原版背包区（v=126,h=96）。面板宽固定 176；高度由菜单的 bodyHeight() 决定
 * （1 重 154、2 重 154、3/4 重 174）。</p>
 *
 * <p>每路状态条画在槽位下方行距多出的 2px 区域里：产出槽下方是烧炼进度，
 * 燃料槽下方是整机共享燃烧池的剩余比例（橙红）。</p>
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
    private static final int BAR_WIDTH = 16;
    private static final int BAR_HEIGHT = 2;

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
        // 槽位凹槽 + 每路状态条（行距 20 多出的 2px）
        for (int i = 0; i < lanes * 3; i++) {
            Slot slot = this.menu.getSlot(i);
            drawSlot(graphics, this.leftPos + slot.x, this.topPos + slot.y);
        }
        for (int lane = 0; lane < lanes; lane++) {
            Slot output = this.menu.getSlot(2 * lanes + lane);
            drawBar(graphics, this.leftPos + output.x - 1, this.topPos + output.y + 17,
                this.menu.cookProgress(lane), BAR_PROGRESS);
            if (this.menu.isLit()) {
                Slot fuel = this.menu.getSlot(lanes + lane);
                drawBar(graphics, this.leftPos + fuel.x - 1, this.topPos + fuel.y + 17,
                    this.menu.litProgress(), BAR_FLAME);
            }
        }
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
    private static void drawBar(GuiGraphics graphics, int x, int y, float progress, int color) {
        graphics.fill(x, y, x + BAR_WIDTH, y + BAR_HEIGHT, BAR_BG);
        float clamped = Math.max(0.0F, Math.min(1.0F, progress));
        int filled = Math.round(clamped * (float) BAR_WIDTH);
        if (filled > 0) {
            graphics.fill(x, y, x + filled, y + BAR_HEIGHT, color);
        }
    }
}
