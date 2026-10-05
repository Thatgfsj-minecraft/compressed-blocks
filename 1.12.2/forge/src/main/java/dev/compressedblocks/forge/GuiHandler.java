package dev.compressedblocks.forge;

import dev.compressedblocks.ScrollingContainerMenu;
import dev.compressedblocks.ScrollingContainerScreen;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.IGuiHandler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * 压缩方块 GUI 处理器（1.12.2 IGuiHandler 模式，替代 1.16.5 的 MenuType + NetworkHooks）：
 * 服务端返回 Container（服务端槽位映射恒为全容器顺序）、客户端返回同构 Container 的 Screen。
 * 滚动只是客户端取景窗重摆（同序重建槽位列表），协议零感知。
 */
public class GuiHandler implements IGuiHandler {
    @Override
    public Object getServerGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        boolean keepsContents = id == CompressedBlocksForge.GUI_SHULKER;
        return new ScrollingContainerMenu(player.inventory, world, keepsContents, new BlockPos(x, y, z));
    }

    @Override
    @SideOnly(Side.CLIENT)
    public Object getClientGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        boolean keepsContents = id == CompressedBlocksForge.GUI_SHULKER;
        return new ScrollingContainerScreen(
            new ScrollingContainerMenu(player.inventory, world, keepsContents, new BlockPos(x, y, z)));
    }
}
