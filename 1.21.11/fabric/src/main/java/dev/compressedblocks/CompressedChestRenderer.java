package dev.compressedblocks;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.object.chest.ChestModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.MaterialSet;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/**
 * 压缩箱子渲染（原版 ChestRenderer 同款管线，1.21.11 RenderState）：
 * 单箱模型照抄原版 ChestModel（ModelLayers.CHEST：盖子+锁扣随开盖进度掀开），
 * 朝向取 FACING（toYRot 后按原版取负旋转）；贴图指向我们自己的 entity 贴图
 * （entity/chest/ 下，自动进原版 chest 图集，零图集配置）。
 */
public class CompressedChestRenderer
        implements BlockEntityRenderer<ScrollingContainerBlockEntity, CompressedChestRenderer.ChestRenderState> {
    private static final Material MATERIAL = new Material(
        Sheets.CHEST_SHEET,
        Identifier.fromNamespaceAndPath(CompressedBlocks.MOD_ID, "entity/chest/compressed_chest"));

    private final MaterialSet materials;
    private final ChestModel model;

    public CompressedChestRenderer(BlockEntityRendererProvider.Context ctx) {
        this.materials = ctx.materials();
        this.model = new ChestModel(ctx.entityModelSet().bakeLayer(ModelLayers.CHEST));
    }

    @Override
    public ChestRenderState createRenderState() {
        return new ChestRenderState();
    }

    @Override
    public void extractRenderState(ScrollingContainerBlockEntity be, ChestRenderState state, float partialTick,
                                   Vec3 cameraPos, ModelFeatureRenderer.CrumblingOverlay overlay) {
        BlockEntityRenderState.extractBase(be, state, overlay);
        state.angle = be.getBlockState().hasProperty(CompressedChestBlock.FACING)
            ? be.getBlockState().getValue(CompressedChestBlock.FACING).toYRot() : 0.0F;
        state.progress = be.getLidProgress(partialTick);
    }

    @Override
    public void submit(ChestRenderState state, PoseStack pose, SubmitNodeCollector collector,
                       CameraRenderState camera) {
        pose.pushPose();
        // 原版 ChestRenderer.submit：居中 → 按朝向取负旋转 → 还原；开盖三次缓动
        pose.translate(0.5F, 0.5F, 0.5F);
        pose.mulPose(Axis.YP.rotationDegrees(-state.angle));
        pose.translate(-0.5F, -0.5F, -0.5F);
        float open = 1.0F - state.progress;
        open = 1.0F - open * open * open;
        this.model.setupAnim(open);
        collector.submitModel(this.model, open, pose,
            MATERIAL.renderType(this.model::renderType),
            state.lightCoords, OverlayTexture.NO_OVERLAY, -1, this.materials.get(MATERIAL), 0,
            state.breakProgress);
        pose.popPose();
    }

    /** 渲染状态：朝向角 + 开盖进度（其余由 extractBase 提供）。 */
    public static class ChestRenderState extends BlockEntityRenderState {
        public float angle;
        public float progress;
    }
}
