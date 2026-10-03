package dev.compressedblocks;

import java.util.Collections;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.Vec3;

/**
 * 盆栽渲染（Botany Pots 同款参数，1.21.6+ RenderState 管线）：
 * 土壤 = 整块方块压扁（0.625×0.125×0.625）嵌在口沿内腔（y=0.25..0.375），只在相机高于盆时绘制（防侧面穿模）；
 * 作物 = 基部沉在盆口下（y=0.375），随生长从 0.40 平滑缩放到 1.00，长成后探出盆口。
 * 有 age 属性的方块（作物/红树胚/甜果丛/地狱疣/瓜藤/竹子/可可）按进度推进视觉年龄；
 * 可可特殊：微型丛林木主干 + 四面可可豆（用户定稿，符合原版附木生长）。
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
        // 显式 (1,1,1) 颜色 + 模型直提：绕过 renderSingleBlock 的 null-level 染色解析
        // （该路径会把贴图染成异常红色），贴图保持本色
        if (state.soil != null && state.cameraAbove) {
            // 10px 见方、2px 高，坐在腰身顶（y=4px）、嵌进口沿内腔（3..13），顶面与盆沿齐平不外漏
            pose.pushPose();
            pose.translate(0.5, 0.25, 0.5);
            pose.scale(0.625F, 0.125F, 0.625F);
            pose.translate(-0.5, 0.0, -0.5);
            submitModel(state.soil, pose, collector, light);
            pose.popPose();
        }
        if (state.plant != null) {
            float s = 0.4F + 0.6F * state.growth;
            if (state.plant.getBlock() instanceof CocoaBlock cocoa) {
                // 可可（用户定稿）：微型丛林木主干 + 四面可可豆随生长长大（原版附木生长）
                submitPlant(Blocks.JUNGLE_LOG.defaultBlockState(), s, light, pose, collector);
                int age = Math.round(state.growth * 2);
                for (Direction facing : HORIZONTALS) {
                    submitPlant(cocoa.defaultBlockState()
                        .setValue(CocoaBlock.FACING, facing)
                        .setValue(CocoaBlock.AGE, age), s, light, pose, collector);
                }
            } else {
                submitPlant(advanceVisual(state.plant, state.growth, state.grown),
                    s, light, pose, collector);
            }
        }
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

    /** 作物/土壤模型提交：基部 y=0.375、随生长 0.40→1.00 缩放。 */
    private void submitPlant(BlockState st, float s, int light, PoseStack pose,
                             SubmitNodeCollector collector) {
        pose.pushPose();
        pose.translate(0.5, 0.375, 0.5);
        pose.scale(s, s, s);
        pose.translate(-0.5, 0.0, -0.5);
        submitModel(st, pose, collector, light);
        pose.popPose();
    }

    private void submitModel(BlockState st, PoseStack pose, SubmitNodeCollector collector, int light) {
        // overlay 必须传 NO_OVERLAY：传 0 会采样到 overlay 贴图的受伤红闪行，整个模型混入 30% 纯红
        collector.submitBlockModel(pose,
            net.minecraft.client.renderer.ItemBlockRenderTypes.getRenderType(st),
            this.dispatcher.getBlockModel(st), 1.0F, 1.0F, 1.0F, light,
            net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, 0);
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
