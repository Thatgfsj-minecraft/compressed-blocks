package dev.compressedblockspot;

import java.util.Collections;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.Vec3;

/**
 * 压缩盆栽渲染：土壤 10×10×2 嵌口沿内腔（仅相机高于盆时绘制），作物 0.40→1.00 平滑缩放；
 * 有 age 属性的方块按进度推进视觉年龄；环境染色（作物/甘蔗等）由 moving-block 管线
 * 按盆所在生物群系自动施加。
 * 26.3：BlockRenderDispatcher/submitBlockModel 移除——土壤/作物统一走 MovingBlockRenderState
 * （原版下落方块同款管线，替代旧的手动 BlockColors tint）。
 */
public class SpotPotRenderer implements BlockEntityRenderer<SpotPotBlockEntity, SpotPotRenderer.PotRenderState> {

    public SpotPotRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    @Override
    public PotRenderState createRenderState() {
        return new PotRenderState();
    }

    /** 把一个 BlockState 填进 moving-block 渲染状态（位置光照 + 生物群系染色随盆所在处）。 */
    private static MovingBlockRenderState newMoving(BlockState st, SpotPotBlockEntity be) {
        MovingBlockRenderState mbrs = new MovingBlockRenderState();
        mbrs.randomSeedPos = be.getBlockPos();
        mbrs.blockPos = be.getBlockPos();
        mbrs.blockState = st;
        if (be.getLevel() instanceof ClientLevel level) {
            mbrs.biome = level.getBiome(be.getBlockPos());
            mbrs.cardinalLighting = level.cardinalLighting();
            mbrs.lightEngine = level.getLightEngine();
        }
        return mbrs;
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
        state.soilRender = state.soil == null ? null : newMoving(state.soil, be);
        state.plantRender = state.plant == null ? null
            : newMoving(advanceVisual(state.plant, state.growth, state.grown), be);
    }

    @Override
    public void submit(PotRenderState state, PoseStack pose, SubmitNodeCollector collector,
                       CameraRenderState camera) {
        if (state.soilRender != null && state.cameraAbove) {
            // 10px 见方、2px 高，坐在腰身顶（y=4px）、嵌进口沿内腔（3..13），顶面与盆沿齐平不外漏
            pose.pushPose();
            pose.translate(0.5, 0.25, 0.5);
            pose.scale(0.625F, 0.125F, 0.625F);
            pose.translate(-0.5, 0.0, -0.5);
            collector.submitMovingBlock(pose, state.soilRender, 0);
            pose.popPose();
        }
        if (state.plantRender != null) {
            float s = 0.4F + 0.6F * state.growth;
            pose.pushPose();
            pose.translate(0.5, 0.375, 0.5);
            pose.scale(s, s, s);
            pose.translate(-0.5, 0.0, -0.5);
            collector.submitMovingBlock(pose, state.plantRender, 0);
            pose.popPose();
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

    /** 盆栽渲染状态。 */
    public static class PotRenderState extends BlockEntityRenderState {
        public BlockState soil;
        public BlockState plant;
        public float growth;
        public boolean grown;
        public boolean cameraAbove;
        /** 26.3：moving-block 渲染状态（土壤/作物各一）。 */
        public MovingBlockRenderState soilRender;
        public MovingBlockRenderState plantRender;
    }
}
