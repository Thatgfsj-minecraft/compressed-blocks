package dev.compressedblocks;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.MaterialSet;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/**
 * 压缩潜影盒渲染（原版 ShulkerBoxRenderer 同款管线，1.21.11 RenderState）：
 * 壳体/盖子模型照抄原版 ShulkerBoxRenderer$ShulkerBoxModel（ModelLayers.SHULKER_BOX），
 * 盖子随开盖进度上移 + 旋转 270°；贴图指向我们自己的 entity 贴图
 * （路径在 entity/shulker/ 下，自动进原版 shulker_boxes 图集，零图集配置）。
 */
public class CompressedShulkerRenderer
        implements BlockEntityRenderer<ScrollingContainerBlockEntity, CompressedShulkerRenderer.ShulkerRenderState> {
    private static final Material MATERIAL = new Material(
        Sheets.SHULKER_SHEET,
        Identifier.fromNamespaceAndPath(CompressedBlocks.MOD_ID, "entity/shulker/compressed_shulker_box"));

    private final MaterialSet materials;
    private final BoxModel model;

    public CompressedShulkerRenderer(BlockEntityRendererProvider.Context ctx) {
        this.materials = ctx.materials();
        this.model = new BoxModel(ctx.entityModelSet().bakeLayer(ModelLayers.SHULKER_BOX));
    }

    @Override
    public ShulkerRenderState createRenderState() {
        return new ShulkerRenderState();
    }

    @Override
    public void extractRenderState(ScrollingContainerBlockEntity be, ShulkerRenderState state, float partialTick,
                                   Vec3 cameraPos, ModelFeatureRenderer.CrumblingOverlay overlay) {
        BlockEntityRenderState.extractBase(be, state, overlay);
        state.progress = be.getLidProgress(partialTick);
    }

    @Override
    public void submit(ShulkerRenderState state, PoseStack pose, SubmitNodeCollector collector,
                       CameraRenderState camera) {
        pose.pushPose();
        // 原版 prepareModel 顺序（无朝向 → 省 mulPose）
        pose.translate(0.5F, 0.5F, 0.5F);
        pose.scale(0.9995F, 0.9995F, 0.9995F);
        pose.scale(1.0F, -1.0F, -1.0F);
        pose.translate(0.0F, -1.0F, 0.0F);
        this.model.setupAnim(state.progress);
        collector.submitModel(this.model, state.progress, pose,
            MATERIAL.renderType(this.model::renderType),
            state.lightCoords, OverlayTexture.NO_OVERLAY, -1, this.materials.get(MATERIAL), 0,
            state.breakProgress);
        pose.popPose();
    }

    /** 渲染状态：仅开盖进度（其余由 extractBase 提供）。 */
    public static class ShulkerRenderState extends BlockEntityRenderState {
        public float progress;
    }

    /** 壳体模型（照抄原版 ShulkerBoxRenderer$ShulkerBoxModel：lid 上移 8px + 旋转 270°）。 */
    static final class BoxModel extends Model<Float> {
        private final ModelPart lid;

        BoxModel(ModelPart root) {
            super(root, id -> RenderTypes.entityCutoutNoCull(id, false));
            this.lid = root.getChild("lid");
        }

        @Override
        public void setupAnim(Float progress) {
            this.lid.setPos(0.0F, 24.0F - progress * 8.0F, 0.0F);
            this.lid.yRot = 270.0F * progress * ((float) Math.PI / 180.0F);
        }
    }
}
