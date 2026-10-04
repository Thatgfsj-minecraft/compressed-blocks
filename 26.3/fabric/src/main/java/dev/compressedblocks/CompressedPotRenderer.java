package dev.compressedblocks;

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
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.Vec3;

/**
 * 盆栽渲染（Botany Pots 同款参数，26.3 渲染管线）：
 * 土壤 = 整块方块压扁（0.625×0.125×0.625）嵌在口沿内腔（y=0.25..0.375），只在相机高于盆时绘制（防侧面穿模）；
 * 作物 = 基部沉在盆口下（y=0.375），随生长从 0.40 平滑缩放到 1.00，长成后探出盆口。
 * 有 age 属性的方块（作物/红树胚/甜果丛/地狱疣/瓜藤/竹子/可可）按进度推进视觉年龄；
 * 可可特殊：微型丛林木主干 + 四面可可豆（用户定稿，符合原版附木生长）。
 * 进度由客户端本地 tick 推进，每帧读取，无级跳变。
 * 26.3：BlockRenderDispatcher/submitBlockModel 移除——土壤/作物统一走 MovingBlockRenderState
 * （原版下落方块同款管线，自带生物群系染色与位置光照，替代旧的手动 BlockColors tint）。
 */
public class CompressedPotRenderer implements BlockEntityRenderer<CompressedPotBlockEntity, CompressedPotRenderer.PotRenderState> {

    public CompressedPotRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    @Override
    public PotRenderState createRenderState() {
        return new PotRenderState();
    }

    /** 把一个 BlockState 填进 moving-block 渲染状态（位置光照 + 生物群系染色随盆所在处）。 */
    private static void fillMoving(MovingBlockRenderState mbrs, BlockState st, BlockPos pos, ClientLevel level) {
        mbrs.randomSeedPos = pos;
        mbrs.blockPos = pos;
        mbrs.blockState = st;
        mbrs.biome = level.getBiome(pos);
        mbrs.cardinalLighting = level.cardinalLighting();
        mbrs.lightEngine = level.getLightEngine();
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
        if (be.getLevel() instanceof ClientLevel level) {
            state.level = level;
        }
        state.soilRender = state.soil == null ? null : newMoving(state.soil, be);
        state.plantRender = state.plant == null ? null : newMoving(advanceVisual(state.plant, state.growth, state.grown), be);
        // 可可（用户定稿）：微型丛林木主干 + 四面可可豆随生长长大（原版附木生长）
        state.cocoaRenders = null;
        if (state.plant != null && state.plant.getBlock() instanceof CocoaBlock cocoa) {
            int age = Math.round(state.growth * 2);
            state.cocoaRenders = new MovingBlockRenderState[1 + HORIZONTALS.length];
            state.cocoaRenders[0] = newMoving(Blocks.JUNGLE_LOG.defaultBlockState(), be);
            for (int i = 0; i < HORIZONTALS.length; i++) {
                state.cocoaRenders[1 + i] = newMoving(cocoa.defaultBlockState()
                    .setValue(CocoaBlock.FACING, HORIZONTALS[i])
                    .setValue(CocoaBlock.AGE, age), be);
            }
        }
    }

    private static MovingBlockRenderState newMoving(BlockState st, CompressedPotBlockEntity be) {
        MovingBlockRenderState mbrs = new MovingBlockRenderState();
        if (be.getLevel() instanceof ClientLevel level) {
            fillMoving(mbrs, st, be.getBlockPos(), level);
        } else {
            mbrs.blockState = st;
        }
        return mbrs;
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
        if (state.cocoaRenders != null) {
            float s = 0.4F + 0.6F * state.growth;
            pose.pushPose();
            submitPlantPose(pose, s);
            for (MovingBlockRenderState m : state.cocoaRenders) {
                collector.submitMovingBlock(pose, m, 0);
            }
            pose.popPose();
        } else if (state.plantRender != null) {
            float s = 0.4F + 0.6F * state.growth;
            pose.pushPose();
            submitPlantPose(pose, s);
            collector.submitMovingBlock(pose, state.plantRender, 0);
            pose.popPose();
        }
    }

    /** 作物基部 y=0.375、随生长 0.40→1.00 缩放。 */
    private static void submitPlantPose(PoseStack pose, float s) {
        pose.translate(0.5, 0.375, 0.5);
        pose.scale(s, s, s);
        pose.translate(-0.5, 0.0, -0.5);
    }

    /** 四个水平朝向（可可豆挂四面）。 */
    private static final Direction[] HORIZONTALS =
        {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};

    /** 视觉生长：有 age 属性的方块按进度推进（作物/红树胚/甜果丛/地狱疣/瓜藤/竹子/可可），
     *  树苗成熟切 stage=1。 */
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

    /** 盆栽渲染状态：盆内土壤、作物、生长进度与相机相对高度。 */
    public static class PotRenderState extends BlockEntityRenderState {
        public BlockState soil;
        public BlockState plant;
        public float growth;
        public boolean grown;
        public boolean cameraAbove;
        /** 26.3：moving-block 渲染状态（土壤/作物各一，可可为主干+四面豆）。 */
        public MovingBlockRenderState soilRender;
        public MovingBlockRenderState plantRender;
        public MovingBlockRenderState[] cocoaRenders;
        /** 渲染端世界（extract 填充光照/生物群系用）。 */
        public ClientLevel level;
    }
}
