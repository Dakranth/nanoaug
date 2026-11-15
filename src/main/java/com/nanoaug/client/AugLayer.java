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

/** Peças cibernéticas por cima do modelo do jogador: fibra de carbono, detalhes dourados e luzes âmbar. */
public class AugLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    public static final ModelLayerLocation LOC =
            new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(NanoAug.MODID, "overlay"), "main");
    private static final ResourceLocation TEX =
            ResourceLocation.fromNamespaceAndPath(NanoAug.MODID, "textures/entity/aug_overlay.png");

    // {parte do corpo, filhos...} na mesma ordem do array "parents" em pass()
    private static final String[][] TREE = {
            {"head", "eyes", "eyemark", "ears"},
            {"body", "studs", "veins", "back"},
            {"right_arm", "blade", "petal1", "petal2", "petal3", "sleeve", "hand", "stripe", "emitter"},
            {"left_arm", "petal1", "petal2", "petal3", "sleeve", "hand", "stripe"},
            {"right_leg", "shin", "knee", "ankle"},
            {"left_leg", "shin", "knee", "ankle"}
    };
    private static final Set<String> GLOW = Set.of("eyes", "veins", "stripe");

    private final ModelPart root;

    public AugLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent, EntityModelSet models) {
        super(parent);
        this.root = models.bakeLayer(LOC);
    }

    private static void petals(PartDefinition arm, float pivotX, float x0, float dir) {
        float[] rot = {0.12F, 0.32F, 0.52F};
        for (int i = 0; i < 3; i++) {
            arm.addOrReplaceChild("petal" + (i + 1),
                    CubeListBuilder.create().texOffs(0, 0).addBox(x0, -0.8F, -2.4F, 4.6F, 0.8F, 4.8F),
                    PartPose.offsetAndRotation(pivotX, -2.1F - 0.35F * i, 0F, 0F, 0F, rot[i] * dir));
        }
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition r = mesh.getRoot();
        // textura: (0,0) fibra de carbono | (32,0) dourado | (0,32) âmbar brilhante | (32,32) prata

        // CABEÇA: só olhos brilhantes, o traço escuro do Adam e um fone discreto
        PartDefinition head = r.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.ZERO);
        head.addOrReplaceChild("eyes", CubeListBuilder.create().texOffs(0, 32)
                .addBox(-3F, -4.6F, -4.15F, 2F, 1F, 0.2F).texOffs(0, 32).addBox(1F, -4.6F, -4.15F, 2F, 1F, 0.2F), PartPose.ZERO);
        head.addOrReplaceChild("eyemark", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-3.5F, -5.5F, -4.12F, 0.5F, 2.2F, 0.2F).texOffs(0, 0).addBox(3F, -5.5F, -4.12F, 0.5F, 2.2F, 0.2F), PartPose.ZERO);
        head.addOrReplaceChild("ears", CubeListBuilder.create().texOffs(32, 0)
                .addBox(4.05F, -4.2F, -0.5F, 0.3F, 1.2F, 1F).texOffs(32, 0).addBox(-4.35F, -4.2F, -0.5F, 0.3F, 1.2F, 1F), PartPose.ZERO);

        // TORSO: pele à mostra, só os pontinhos metálicos do peito e uma placa fina nas costas
        PartDefinition body = r.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.ZERO);
        body.addOrReplaceChild("studs", CubeListBuilder.create().texOffs(32, 0)
                .addBox(-3.2F, 1.0F, -2.2F, 0.8F, 0.8F, 0.2F).texOffs(32, 0).addBox(2.4F, 1.0F, -2.2F, 0.8F, 0.8F, 0.2F)
                .texOffs(32, 0).addBox(-1.7F, 2.6F, -2.2F, 0.8F, 0.8F, 0.2F).texOffs(32, 0).addBox(0.9F, 2.6F, -2.2F, 0.8F, 0.8F, 0.2F)
                .texOffs(32, 0).addBox(-0.4F, 0.4F, -2.2F, 0.8F, 0.8F, 0.2F).texOffs(32, 0).addBox(-0.4F, 4.6F, -2.2F, 0.8F, 0.8F, 0.2F), PartPose.ZERO);
        body.addOrReplaceChild("veins", CubeListBuilder.create().texOffs(0, 32)
                .addBox(-3.2F, 1.3F, -2.15F, 6.4F, 0.25F, 0.15F).texOffs(0, 32).addBox(-0.12F, 0.8F, -2.15F, 0.25F, 4.2F, 0.15F), PartPose.ZERO);
        body.addOrReplaceChild("back", CubeListBuilder.create().texOffs(0, 0).addBox(-3F, 1.2F, 2.0F, 6F, 4.5F, 0.6F), PartPose.ZERO);

        // BRAÇOS: prótese preta fina colada no braço, mão prateada e pétalas no ombro
        PartDefinition ra = r.addOrReplaceChild("right_arm", CubeListBuilder.create(), PartPose.ZERO);
        ra.addOrReplaceChild("blade", CubeListBuilder.create().texOffs(32, 32).addBox(-1.4F, 8F, -12F, 0.5F, 2F, 10F), PartPose.ZERO);
        petals(ra, -1F, -3.6F, 1F);
        ra.addOrReplaceChild("sleeve", CubeListBuilder.create().texOffs(0, 0).addBox(-3.25F, -2.25F, -2.25F, 4.5F, 9.5F, 4.5F), PartPose.ZERO);
        ra.addOrReplaceChild("hand", CubeListBuilder.create().texOffs(32, 32).addBox(-3.2F, 7.2F, -2.2F, 4.4F, 3F, 4.4F), PartPose.ZERO);
        ra.addOrReplaceChild("stripe", CubeListBuilder.create().texOffs(0, 32).addBox(-3.4F, 0.5F, -0.5F, 0.15F, 6F, 1F), PartPose.ZERO);
        ra.addOrReplaceChild("emitter", CubeListBuilder.create().texOffs(32, 0).addBox(-3.45F, 6.2F, -1.3F, 0.5F, 1.4F, 2.6F), PartPose.ZERO);

        PartDefinition la = r.addOrReplaceChild("left_arm", CubeListBuilder.create(), PartPose.ZERO);
        petals(la, 1F, -1.0F, -1F);
        la.addOrReplaceChild("sleeve", CubeListBuilder.create().texOffs(0, 0).addBox(-1.25F, -2.25F, -2.25F, 4.5F, 9.5F, 4.5F), PartPose.ZERO);
        la.addOrReplaceChild("hand", CubeListBuilder.create().texOffs(32, 32).addBox(-1.2F, 7.2F, -2.2F, 4.4F, 3F, 4.4F), PartPose.ZERO);
        la.addOrReplaceChild("stripe", CubeListBuilder.create().texOffs(0, 32).addBox(3.25F, 0.5F, -0.5F, 0.15F, 6F, 1F), PartPose.ZERO);

        // PERNAS: canela fina, disco dourado no joelho e tornozelo prateado
        for (String leg : new String[]{"right_leg", "left_leg"}) {
            PartDefinition lg = r.addOrReplaceChild(leg, CubeListBuilder.create(), PartPose.ZERO);
            lg.addOrReplaceChild("shin", CubeListBuilder.create().texOffs(32, 32).addBox(-2.25F, 5.6F, -2.25F, 4.5F, 5.0F, 4.5F), PartPose.ZERO);
            lg.addOrReplaceChild("knee", CubeListBuilder.create().texOffs(32, 0).addBox(-1.4F, 4.0F, -2.5F, 2.8F, 2.2F, 0.4F), PartPose.ZERO);
            lg.addOrReplaceChild("ankle", CubeListBuilder.create().texOffs(0, 0).addBox(-2.2F, 10.6F, -2.2F, 4.4F, 1.4F, 4.4F), PartPose.ZERO);
        }
        return LayerDefinition.create(mesh, 64, 64);
    }

    private static boolean visible(String child, AugData d, AbstractClientPlayer p) {
        return switch (child) {
            case "eyes", "eyemark" -> d.any(Aug.Slot.EYES);
            case "ears" -> d.has(Aug.SOCIAL);
            case "studs" -> d.any(Aug.Slot.TORSO);
            case "veins" -> d.active(Aug.CLOAK) || d.active(Aug.TITAN) || d.active(Aug.FOCUS) || d.overclock > 0;
            case "back" -> d.any(Aug.Slot.BACK);
            case "blade" -> d.has(Aug.NANOBLADE) && p.getMainHandItem().isEmpty();
            case "petal1", "petal2", "petal3", "sleeve", "hand", "stripe" -> d.has(Aug.ARM);
            case "emitter" -> d.any(Aug.Slot.ARMS) && !d.has(Aug.ARM);
            case "shin", "knee", "ankle" -> d.has(Aug.LEG);
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
