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
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

import java.util.Set;

/**
 * Prótese "pintada" por cima do corpo: uma segunda pele colada no modelo do jogador (mesmo layout de UV
 * da skin), com textura própria. Uma textura de luz (âmbar) é desenhada brilhando por cima.
 */
public class AugLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    public static final ModelLayerLocation LOC =
            new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(NanoAug.MODID, "overlay"), "main");
    private static final ResourceLocation TEX =
            ResourceLocation.fromNamespaceAndPath(NanoAug.MODID, "textures/entity/aug_overlay.png");
    private static final ResourceLocation GLOW_TEX =
            ResourceLocation.fromNamespaceAndPath(NanoAug.MODID, "textures/entity/aug_glow.png");

    private record P(int parent, String name) {}

    // parent: 0 cabeça, 1 corpo, 2 braço direito, 3 braço esquerdo, 4 perna direita, 5 perna esquerda
    private static final P[] PARTS = {
            new P(0, "head"), new P(0, "ears"),
            new P(1, "body"),
            new P(2, "rarm"), new P(2, "blade"),
            new P(3, "larm"),
            new P(4, "rleg"), new P(5, "lleg")
    };
    private static final Set<String> GLOW = Set.of("head", "body", "rarm", "larm", "rleg", "lleg");

    private final ModelPart root;

    public AugLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent, EntityModelSet models) {
        super(parent);
        this.root = models.bakeLayer(LOC);
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition r = mesh.getRoot();
        CubeDeformation g = new CubeDeformation(0.3F);
        CubeDeformation gh = new CubeDeformation(0.1F);

        // mesmas posições/UV da skin do jogador; só a textura é nossa
        r.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-4F, -8F, -4F, 8F, 8F, 8F, gh), PartPose.ZERO);
        r.addOrReplaceChild("ears", CubeListBuilder.create().texOffs(56, 0)
                .addBox(4.05F, -4.2F, -0.5F, 0.3F, 1.2F, 1F).texOffs(56, 0).addBox(-4.35F, -4.2F, -0.5F, 0.3F, 1.2F, 1F), PartPose.ZERO);
        r.addOrReplaceChild("body", CubeListBuilder.create().texOffs(16, 16).addBox(-4F, 0F, -2F, 8F, 12F, 4F, g), PartPose.ZERO);
        r.addOrReplaceChild("rarm", CubeListBuilder.create().texOffs(40, 16).addBox(-3F, -2F, -2F, 4F, 12F, 4F, g), PartPose.ZERO);
        r.addOrReplaceChild("larm", CubeListBuilder.create().texOffs(32, 48).addBox(-1F, -2F, -2F, 4F, 12F, 4F, g), PartPose.ZERO);
        r.addOrReplaceChild("rleg", CubeListBuilder.create().texOffs(0, 16).addBox(-2F, 0F, -2F, 4F, 12F, 4F, g), PartPose.ZERO);
        r.addOrReplaceChild("lleg", CubeListBuilder.create().texOffs(16, 48).addBox(-2F, 0F, -2F, 4F, 12F, 4F, g), PartPose.ZERO);

        r.addOrReplaceChild("blade", CubeListBuilder.create().texOffs(0, 32).addBox(-1.4F, 8F, -12F, 0.5F, 2F, 10F), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 64);
    }

    private static boolean solidVisible(String n, AugData d, AbstractClientPlayer p) {
        return switch (n) {
            case "head" -> d.any(Aug.Slot.EYES);
            case "ears" -> d.has(Aug.SOCIAL);
            case "body" -> d.any(Aug.Slot.TORSO) || d.any(Aug.Slot.BACK);
            case "rarm", "larm" -> d.has(Aug.ARM);
            case "blade" -> d.has(Aug.NANOBLADE) && p.getMainHandItem().isEmpty();
            case "rleg", "lleg" -> d.has(Aug.LEG);
            default -> false;
        };
    }

    private static boolean glowVisible(String n, AugData d) {
        boolean energized = d.active(Aug.CLOAK) || d.active(Aug.TITAN) || d.overclock > 0;
        return switch (n) {
            case "head" -> d.active(Aug.SMART_VISION);
            case "body" -> energized && (d.any(Aug.Slot.TORSO) || d.any(Aug.Slot.BACK));
            case "rarm", "larm" -> d.has(Aug.ARM);
            case "rleg", "lleg" -> energized && d.has(Aug.LEG);
            default -> false;
        };
    }

    @Override
    public void render(PoseStack ps, MultiBufferSource buf, int light, AbstractClientPlayer pl,
                       float limbSwing, float limbSwingAmount, float partialTick, float age, float netYaw, float pitch) {
        if (pl.isInvisible()) return;
        AugData d = AugData.get(pl);
        if (d.levels.isEmpty()) return;
        PlayerModel<AbstractClientPlayer> pm = getParentModel();
        pass(ps, buf.getBuffer(RenderType.entityCutoutNoCull(TEX)), light, pl, d, pm, false);
        pass(ps, buf.getBuffer(RenderType.eyes(GLOW_TEX)), LightTexture.FULL_BRIGHT, pl, d, pm, true);
    }

    private void pass(PoseStack ps, VertexConsumer vc, int light, AbstractClientPlayer pl, AugData d,
                      PlayerModel<AbstractClientPlayer> pm, boolean glowPass) {
        ModelPart[] parents = {pm.head, pm.body, pm.rightArm, pm.leftArm, pm.rightLeg, pm.leftLeg};
        for (P e : PARTS) {
            boolean vis = glowPass ? (GLOW.contains(e.name()) && glowVisible(e.name(), d)) : solidVisible(e.name(), d, pl);
            if (!vis) continue;
            ps.pushPose();
            parents[e.parent()].translateAndRotate(ps);
            root.getChild(e.name()).render(ps, vc, light, OverlayTexture.NO_OVERLAY);
            ps.popPose();
        }
    }
}
