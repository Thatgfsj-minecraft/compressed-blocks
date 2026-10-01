package dev.compressedblocks;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * 盆栽渲染（Botany Pots 同款参数，1.21.6+ RenderState 管线）：
 * 土壤 = 整块方块压扁（0.75×0.375×0.75）填进盆口内腔，只在相机高于盆时绘制（防侧面穿模）；
 * 作物 = 基部沉在盆口下（y=0.375），随生长从 0.40 平滑缩放到 1.00，长成后探出盆口。
 * 进度由客户端本地 tick 推进，每帧读取，无级跳变。
 */
public class CompressedPotRenderer implements BlockEntityRenderer<CompressedPotBlockEntity, CompressedPotRenderer.PotRenderState> {
    private final BlockRenderDispatcher dispatcher;

    public CompressedPotRenderer(BlockEntityRendererProvider.Context ctx) {
        this.dispatcher = ctx.blockRenderDispatcher();
    }

    @Override
    public PotRenderState createRenderState() {
        return new PotRenderState();
    }

    @Override
    public void extractRenderState(CompressedPotBlockEntity be, PotRenderState state, float partialTick,
                                   Vec3 cameraPos, ModelFeatureRenderer.CrumblingOverlay overlay) {
        BlockEntityRenderState.extractBase(be, state, overlay);
        state.soil = be.soil();
        state.plant = be.plant();
        state.growth = be.growthFraction();
        state.grown = be.grown();
        state.cameraAbove = cameraPos != null && cameraPos.y >= be.getBlockPos().getY();
    }

    @Override
    public void submit(PotRenderState state, PoseStack pose, SubmitNodeCollector collector,
                       CameraRenderState camera) {
        int light = state.lightCoords;
        if (state.soil != null && state.cameraAbove) {
            pose.pushPose();
            pose.translate(0.5, 0.125, 0.5);
            pose.scale(0.75F, 0.375F, 0.75F);
            pose.translate(-0.5, 0.0, -0.5);
            collector.submitBlock(pose, state.soil, light, 0, 0);
            pose.popPose();
        }
        if (state.plant != null) {
            float s = 0.4F + 0.6F * state.growth;
            BlockState st = state.plant;
            if (st.getBlock() instanceof CropBlock crop) {
                st = crop.getStateForAge(Math.round(state.growth * crop.getMaxAge()));
            } else if (st.getBlock() instanceof SaplingBlock && state.grown) {
                st = st.setValue(SaplingBlock.STAGE, 1);
            }
            pose.pushPose();
            pose.translate(0.5, 0.375, 0.5);
            pose.scale(s, s, s);
            pose.translate(-0.5, 0.0, -0.5);
            collector.submitBlock(pose, st, light, 0, 0);
            pose.popPose();
        }
    }

    /** 盆栽渲染状态：盆内土壤、作物、生长进度与相机相对高度。 */
    public static class PotRenderState extends BlockEntityRenderState {
        public BlockState soil;
        public BlockState plant;
        public float growth;
        public boolean grown;
        public boolean cameraAbove;
    }
}
