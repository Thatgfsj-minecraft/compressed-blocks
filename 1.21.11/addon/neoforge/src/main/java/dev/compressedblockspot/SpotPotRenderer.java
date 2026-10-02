package dev.compressedblockspot;

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

/** 压缩盆栽渲染：土壤 10×10×5 填内腔（仅相机高于盆时绘制），作物 0.40→1.00 平滑缩放。 */
public class SpotPotRenderer implements BlockEntityRenderer<SpotPotBlockEntity, SpotPotRenderer.PotRenderState> {
    private final BlockRenderDispatcher dispatcher;

    public SpotPotRenderer(BlockEntityRendererProvider.Context ctx) {
        this.dispatcher = ctx.blockRenderDispatcher();
    }

    @Override
    public PotRenderState createRenderState() {
        return new PotRenderState();
    }

    @Override
    public void extractRenderState(SpotPotBlockEntity be, PotRenderState state, float partialTick,
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
            pose.translate(0.5, 0.0, 0.5);
            pose.scale(0.625F, 0.3125F, 0.625F);
            pose.translate(-0.5, 0.0, -0.5);
            submitModel(state.soil, pose, collector, light);
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
            submitModel(st, pose, collector, light);
            pose.popPose();
        }
    }

    private void submitModel(BlockState st, PoseStack pose, SubmitNodeCollector collector, int light) {
        // overlay 必须传 NO_OVERLAY，否则整模型混入受伤红
        collector.submitBlockModel(pose,
            net.minecraft.client.renderer.ItemBlockRenderTypes.getRenderType(st),
            this.dispatcher.getBlockModel(st), 1.0F, 1.0F, 1.0F, light,
            net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, 0);
    }

    /** 盆栽渲染状态。 */
    public static class PotRenderState extends BlockEntityRenderState {
        public BlockState soil;
        public BlockState plant;
        public float growth;
        public boolean grown;
        public boolean cameraAbove;
    }
}
