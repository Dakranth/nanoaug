package com.nanoaug.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.nanoaug.Aug;
import com.nanoaug.AugData;
import com.nanoaug.NanoAug;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

import java.util.Set;

/** Desenha as peças cibernéticas por cima do modelo do jogador. */
public class AugLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    public static final ModelLayerLocation LOC =
            new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(NanoAug.MODID, "overlay"), "main");
    private static final ResourceLocation TEX =
            ResourceLocation.fromNamespaceAndPath(NanoAug.MODID, "textures/entity/aug_overlay.png");

    // {parte do corpo, filhos...}  na mesma ordem do array "parents" em pass()
    private static final String[][] TREE = {
            {"head", "eyes", "ears", "crest"},
            {"body", "plate", "core", "back", "veins"},
            {"right_arm", "blade", "armplate"},
            {"left_arm", "armplate"},
            {"right_leg", "legbrace"},
            {"left_leg", "legbrace"}
    };
    private static final Set<String> GLOW = Set.of("eyes", "core", "veins");

    private final ModelPart root;

    public AugLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent, EntityModelSet models) {
        super(parent);
        this.root = models.bakeLayer(LOC);
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition r = mesh.getRoot();
        // texturas: (0,0) metal escuro | (32,0) dourado | (0,32) ciano brilhante | (32,32) prata
        PartDefinition head = r.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.ZERO);
        head.addOrReplaceChild("eyes", CubeListBuilder.create().texOffs(0, 32).addBox(-3.5F, -5.2F, -4.45F, 7F, 1.4F, 0.5F), PartPose.ZERO);
        head.addOrReplaceChild("ears", CubeListBuilder.create().texOffs(32, 0)
                .addBox(-5.0F, -5F, -1.5F, 1F, 3F, 3F).texOffs(32, 0).addBox(4.0F, -5F, -1.5F, 1F, 3F, 3F), PartPose.ZERO);
        head.addOrReplaceChild("crest", CubeListBuilder.create().texOffs(0, 0).addBox(-1F, -8.8F, -3F, 2F, 0.8F, 6F), PartPose.ZERO);

        PartDefinition body = r.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.ZERO);
        body.addOrReplaceChild("plate", CubeListBuilder.create().texOffs(0, 0).addBox(-4.5F, 0.4F, -2.6F, 9F, 6F, 5.2F), PartPose.ZERO);
        body.addOrReplaceChild("core", CubeListBuilder.create().texOffs(0, 32).addBox(-1.5F, 2F, -2.9F, 3F, 3F, 0.4F), PartPose.ZERO);
        body.addOrReplaceChild("back", CubeListBuilder.create().texOffs(0, 0).addBox(-3F, 1F, 2.5F, 6F, 5F, 2F), PartPose.ZERO);
        body.addOrReplaceChild("veins", CubeListBuilder.create().texOffs(0, 32)
                .addBox(-4.1F, 7F, -2.1F, 8.2F, 0.4F, 0.2F).texOffs(0, 32).addBox(-0.2F, 7F, -2.1F, 0.4F, 5F, 0.2F), PartPose.ZERO);

        PartDefinition ra = r.addOrReplaceChild("right_arm", CubeListBuilder.create(), PartPose.ZERO);
        ra.addOrReplaceChild("blade", CubeListBuilder.create().texOffs(32, 32).addBox(-1.4F, 8F, -12F, 0.6F, 2.2F, 10F), PartPose.ZERO);
        ra.addOrReplaceChild("armplate", CubeListBuilder.create().texOffs(32, 0).addBox(-3.6F, 2.5F, -2.5F, 4.8F, 3.5F, 5F), PartPose.ZERO);
        PartDefinition la = r.addOrReplaceChild("left_arm", CubeListBuilder.create(), PartPose.ZERO);
        la.addOrReplaceChild("armplate", CubeListBuilder.create().texOffs(32, 0).addBox(-1.2F, 2.5F, -2.5F, 4.8F, 3.5F, 5F), PartPose.ZERO);

        for (String leg : new String[]{"right_leg", "left_leg"}) {
            r.addOrReplaceChild(leg, CubeListBuilder.create(), PartPose.ZERO)
                    .addOrReplaceChild("legbrace", CubeListBuilder.create().texOffs(32, 0).addBox(-2.4F, 4F, -2.4F, 4.8F, 4F, 4.8F), PartPose.ZERO);
        }
        return LayerDefinition.create(mesh, 64, 64);
    }

    private static boolean visible(String child, AugData d, AbstractClientPlayer p) {
        return switch (child) {
            case "eyes" -> d.has(Aug.SMART_VISION);
            case "ears" -> d.has(Aug.SOCIAL) || d.has(Aug.REMOTE_HACK);
            case "crest" -> d.has(Aug.CLOAK);
            case "plate", "core" -> d.any(Aug.Slot.TORSO);
            case "back" -> d.has(Aug.ICARUS_DASH) || d.has(Aug.ICARUS_LANDING);
            case "veins" -> d.any(Aug.Slot.SKIN);
            case "blade" -> d.has(Aug.NANO_BLADE) && p.getMainHandItem().isEmpty();
            case "armplate" -> d.has(Aug.STRENGTH) || d.has(Aug.REFLEX) || d.has(Aug.TESLA);
            case "legbrace" -> d.any(Aug.Slot.LEGS);
            default -> false;
        };
    }

    @Override
    public void render(PoseStack ps, MultiBufferSource buf, int light, AbstractClientPlayer pl,
                       float limbSwing, float limbSwingAmount, float partialTick, float age, float netYaw, float pitch) {
        if (pl.isInvisible()) return;
        AugData d = AugData.get(pl);
        if (d.installed.isEmpty()) return;
        PlayerModel<AbstractClientPlayer> pm = getParentModel();
        pass(ps, buf.getBuffer(RenderType.entityCutoutNoCull(TEX)), light, pl, d, pm, false);
        pass(ps, buf.getBuffer(RenderType.eyes(TEX)), LightTexture.FULL_BRIGHT, pl, d, pm, true);
    }

    private void pass(PoseStack ps, VertexConsumer vc, int light, AbstractClientPlayer pl, AugData d,
                      PlayerModel<AbstractClientPlayer> pm, boolean glowPass) {
        ModelPart[] parents = {pm.head, pm.body, pm.rightArm, pm.leftArm, pm.rightLeg, pm.leftLeg};
        for (int i = 0; i < TREE.length; i++) {
            String[] t = TREE[i];
            ModelPart rp = root.getChild(t[0]);
            boolean any = false;
            for (int j = 1; j < t.length; j++) {
                ModelPart c = rp.getChild(t[j]);
                c.visible = visible(t[j], d, pl) && GLOW.contains(t[j]) == glowPass;
                any |= c.visible;
            }
            if (!any) continue;
            ps.pushPose();
            parents[i].translateAndRotate(ps);
            rp.render(ps, vc, light, OverlayTexture.NO_OVERLAY);
            ps.popPose();
        }
    }
}
