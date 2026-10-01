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
 * 盆栽渲染（Botany Pots 同款思路，1.21.6+ RenderState 管线）：盆内画土壤（0.5 缩放沉入盆口内腔），
 * 作物画在盆沿上、按生长进度从 0.2 缩放到 0.5（服务端分 8 档同步，视觉阶梯增长）。
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
    }

    @Override
    public void submit(PotRenderState state, PoseStack pose, SubmitNodeCollector collector,
                       CameraRenderState camera) {
        int light = state.lightCoords;
        if (state.soil != null) {
            pose.pushPose();
            pose.translate(0.5, 0.125, 0.5);
            pose.scale(0.5F, 0.5F, 0.5F);
            pose.translate(-0.5, 0.0, -0.5);
            collector.submitBlock(pose, state.soil, light, 0, 0);
            pose.popPose();
        }
        if (state.plant != null) {
            float f = state.growth;
            float s = 0.2F + 0.3F * f;
            BlockState st = state.plant;
            if (st.getBlock() instanceof CropBlock crop) {
                st = crop.getStateForAge(Math.round(f * crop.getMaxAge()));
            } else if (st.getBlock() instanceof SaplingBlock && state.grown) {
                st = st.setValue(SaplingBlock.STAGE, 1);
            }
            pose.pushPose();
            pose.translate(0.5, 0.75, 0.5);
            pose.scale(s, s, s);
            pose.translate(-0.5, 0.0, -0.5);
            collector.submitBlock(pose, st, light, 0, 0);
            pose.popPose();
        }
    }

    /** 盆栽渲染状态：盆内土壤、作物与其生长进度（主类为 CompressedPotBlockEntity）。 */
    public static class PotRenderState extends BlockEntityRenderState {
        public BlockState soil;
        public BlockState plant;
        public float growth;
        public boolean grown;
    }
}
