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

/** Neuropozyne: evita a rejeição e as falhas das augs experimentais por 10 minutos. */
public class NeuropozyneItem extends Item {
    public NeuropozyneItem(Properties props) { super(props); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            AugData d = AugData.get(sp);
            d.stable = 12000;
            stack.consume(1, player);
            level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_DRINK, SoundSource.PLAYERS, 0.8f, 1.2f);
            sp.displayClientMessage(Component.translatable("msg.nanoaug.neuro_ok"), true);
            Net.sync(sp);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
