package dev.compressedblocks;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/**
 * 木质漏斗界面：原版容器风格但物品槽只有 1 或 3 格，没有现成贴图可整张借用。
 * 做法是「原版顶边 + 自绘中段 + 原版背包区」三段拼接：
 * 顶边与背包区直接 blit 原版 {@code generic_54}，中段用原版面板灰（#C6C6C6）铺底并手绘
 * 槽位凹槽（左上 #373737、内部 #8B8B8B、右下白），视觉与原版容器一致且无需新增贴图。
 */
public class WoodenHopperScreen extends AbstractContainerScreen<WoodenHopperMenu> {
    /** 原版 6 行箱子背景：顶边取 v=0，背包区取 v=126。 */
    private static final Identifier BACKGROUND = Identifier.withDefaultNamespace("textures/gui/container/generic_54.png");
    private static final int PANEL_W = 176;
    /** 原版容器顶边高度。 */
    private static final int TOP_H = 17;
    /** 物品槽行高（1 格与 3 格都只占一行）。 */
    private static final int BODY_H = 18;
    /** 原版背包区贴图高度。 */
    private static final int INV_H = 96;
    /** 原版灰面板色。 */
    private static final int PANEL_GRAY = 0xFFC6C6C6;
    private static final int EDGE_LIGHT = 0xFFFFFFFF;
    private static final int EDGE_DARK = 0xFF555555;
    private static final int SLOT_DARK = 0xFF373737;
    private static final int SLOT_FILL = 0xFF8B8B8B;

    public WoodenHopperScreen(WoodenHopperMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = PANEL_W;
        this.imageHeight = TOP_H + BODY_H + INV_H + 1;
        this.inventoryLabelY = TOP_H + BODY_H + 1;
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        super.render(gfx, mouseX, mouseY, partialTick);
        this.renderTooltip(gfx, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics gfx, float partialTick, int mouseX, int mouseY) {
        // 顶边：原版容器顶盖（含标题区）
        gfx.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, this.leftPos, this.topPos,
            0.0F, 0.0F, PANEL_W, TOP_H, 256, 256);

        // 中段：物品槽所在行，灰面板 + 左右边线
        int bodyTop = this.topPos + TOP_H;
        gfx.fill(this.leftPos, bodyTop, this.leftPos + PANEL_W, bodyTop + BODY_H, PANEL_GRAY);
        gfx.fill(this.leftPos, bodyTop, this.leftPos + 1, bodyTop + BODY_H, EDGE_LIGHT);
        gfx.fill(this.leftPos + PANEL_W - 1, bodyTop, this.leftPos + PANEL_W, bodyTop + BODY_H, EDGE_DARK);

        // 槽位凹槽
        for (int i = 0; i < this.menu.getContainerSlots(); i++) {
            drawSlot(gfx, this.leftPos + this.menu.getSlot(i).x, this.topPos + this.menu.getSlot(i).y);
        }

        // 背包区：原版 generic_54 的 v=126 段
        gfx.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, this.leftPos, bodyTop + BODY_H,
            0.0F, 126.0F, PANEL_W, INV_H, 256, 256);
    }

    /** 原版槽位凹槽：左上深色、内部灰、右下白色高光。 */
    private static void drawSlot(GuiGraphics gfx, int x, int y) {
        gfx.fill(x - 1, y - 1, x + 17, y + 17, SLOT_DARK);
        gfx.fill(x, y, x + 16, y + 16, SLOT_FILL);
        gfx.fill(x - 1, y + 16, x + 17, y + 17, EDGE_LIGHT);
        gfx.fill(x + 16, y - 1, x + 17, y + 17, EDGE_LIGHT);
    }
}
