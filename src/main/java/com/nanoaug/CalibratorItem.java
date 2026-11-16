package com.nanoaug;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Núcleo de Calibração Mk I/II/III: cada nível reduz o peso dos augs experimentais no sistema. Usar em ordem. */
public class CalibratorItem extends Item {
    private final int tier;

    public CalibratorItem(Properties props, int tier) {
        super(props);
        this.tier = tier;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            AugData d = AugData.get(sp);
            if (d.calib >= tier) {
                sp.displayClientMessage(Component.translatable("msg.nanoaug.calib_done"), true);
                return InteractionResultHolder.fail(stack);
            }
            if (d.calib < tier - 1) {
                sp.displayClientMessage(Component.translatable("msg.nanoaug.calib_order"), true);
                return InteractionResultHolder.fail(stack);
            }
            d.calib = tier;
            stack.consume(1, player);
            level.playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1f, 0.8f);
            Fx.ring(sp.serverLevel(), player.position(), 1.5);
            sp.displayClientMessage(Component.translatable("msg.nanoaug.calib_ok", tier, SystemLoad.weightFor(tier)), true);
            Net.sync(sp);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
