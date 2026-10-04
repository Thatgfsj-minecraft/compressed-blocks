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
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * 压缩箱子渲染（原版 ChestRenderer 同款管线，26.3 渲染管线）：
 * 单箱模型照抄原版 ChestModel（ModelLayers.CHEST：盖子+锁扣随开盖进度掀开），
 * 朝向取 FACING（toYRot 后按原版取负旋转）；贴图指向我们自己的 entity 贴图
 * （entity/chest/ 下，自动进原版 chest 图集，零图集配置）。
 * 26.3：Material/MaterialSet 移除——贴图改用 SpriteId + SpriteGetter（ctx.sprites()）；
 * PoseStack.mulPose(Quaternionf) 移除——旋转包成 Matrix4f 提交。
 */
public class CompressedChestRenderer
        implements BlockEntityRenderer<ScrollingContainerBlockEntity, CompressedChestRenderer.ChestRenderState> {
    private static final SpriteId SPRITE = new SpriteId(
        Sheets.CHEST_SHEET,
        Identifier.fromNamespaceAndPath(CompressedBlocks.MOD_ID, "entity/chest/compressed_chest"));

    private final SpriteGetter sprites;
    private final ChestModel model;

    public CompressedChestRenderer(BlockEntityRendererProvider.Context ctx) {
        this.sprites = ctx.sprites();
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
        pose.mulPose(new Matrix4f().rotation(Axis.YP.rotationDegrees(-state.angle)));
        pose.translate(-0.5F, -0.5F, -0.5F);
        float open = 1.0F - state.progress;
        open = 1.0F - open * open * open;
        this.model.setupAnim(open);
        // 26.3：SpriteId + SpriteGetter 重载（内部按图集解析贴图与 RenderType），末位为破损强度 0
        collector.submitModel(this.model, open, pose,
            state.lightCoords, OverlayTexture.NO_OVERLAY, -1, SPRITE, this.sprites, 0);
        pose.popPose();
    }

    /** 渲染状态：朝向角 + 开盖进度（其余由 extractBase 提供）。 */
    public static class ChestRenderState extends BlockEntityRenderState {
        public float angle;
        public float progress;
    }
}
