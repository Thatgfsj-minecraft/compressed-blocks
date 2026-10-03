package dev.compressedblockspot;

import java.util.Collections;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.Vec3;

/**
 * 压缩盆栽渲染：土壤 10×10×2 嵌口沿内腔（仅相机高于盆时绘制），作物 0.40→1.00 平滑缩放；
 * 有 age 属性的方块按进度推进视觉年龄；带环境染色的模型（作物/甘蔗等）按原版 BlockColors
 * 取生物群系色（无染色的方块取回白色，保持本色）。
 */
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
        // 原版环境染色（BlockColors）；无染色方块的取值为 -1（白）
        state.tint = -1;
        if (state.plant != null && be.getLevel() != null) {
            state.tint = Minecraft.getInstance().getBlockColors()
                .getColor(state.plant, be.getLevel(), be.getBlockPos(), 0);
        }
    }

    @Override
    public void submit(PotRenderState state, PoseStack pose, SubmitNodeCollector collector,
                       CameraRenderState camera) {
        int light = state.lightCoords;
        float tr = (state.tint >> 16 & 255) / 255.0F;
        float tg = (state.tint >> 8 & 255) / 255.0F;
        float tb = (state.tint & 255) / 255.0F;
        if (state.soil != null && state.cameraAbove) {
            // 10px 见方、2px 高，坐在腰身顶（y=4px）、嵌进口沿内腔（3..13），顶面与盆沿齐平不外漏
            pose.pushPose();
            pose.translate(0.5, 0.25, 0.5);
            pose.scale(0.625F, 0.125F, 0.625F);
            pose.translate(-0.5, 0.0, -0.5);
            submitModel(state.soil, pose, collector, light, 1.0F, 1.0F, 1.0F);
            pose.popPose();
        }
        if (state.plant != null) {
            float s = 0.4F + 0.6F * state.growth;
            submitPlant(advanceVisual(state.plant, state.growth, state.grown),
                s, light, tr, tg, tb, pose, collector);
        }
    }

    /** 视觉生长：有 age 属性的方块按进度推进，树苗成熟切 stage=1。 */
    private static BlockState advanceVisual(BlockState st, float growth, boolean grown) {
        if (st.getBlock().getStateDefinition().getProperty("age")
            instanceof IntegerProperty age && st.hasProperty(age)) {
            int max = Collections.max(age.getPossibleValues());
            st = st.setValue(age, Math.round(growth * max));
        } else if (st.getBlock() instanceof SaplingBlock && grown) {
            st = st.setValue(SaplingBlock.STAGE, 1);
        }
        return st;
    }

    private void submitPlant(BlockState st, float s, int light, float tr, float tg, float tb,
                             PoseStack pose, SubmitNodeCollector collector) {
        pose.pushPose();
        pose.translate(0.5, 0.375, 0.5);
        pose.scale(s, s, s);
        pose.translate(-0.5, 0.0, -0.5);
        submitModel(st, pose, collector, light, tr, tg, tb);
        pose.popPose();
    }

    private void submitModel(BlockState st, PoseStack pose, SubmitNodeCollector collector, int light,
                             float tr, float tg, float tb) {
        // overlay 必须传 NO_OVERLAY，否则整模型混入受伤红
        collector.submitBlockModel(pose,
            net.minecraft.client.renderer.ItemBlockRenderTypes.getRenderType(st),
            this.dispatcher.getBlockModel(st), tr, tg, tb, light,
            net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, 0);
    }

    /** 盆栽渲染状态。 */
    public static class PotRenderState extends BlockEntityRenderState {
        public BlockState soil;
        public BlockState plant;
        public float growth;
        public boolean grown;
        public boolean cameraAbove;
        /** 原版环境染色（BlockColors），-1 = 无染色保持本色。 */
        public int tint = -1;
    }
}
