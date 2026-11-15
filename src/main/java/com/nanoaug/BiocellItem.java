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

/** Biocélula: recarrega 50 de energia na hora (e adianta o fim do sobreaquecimento). */
public class BiocellItem extends Item {
    public BiocellItem(Properties props) { super(props); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            AugData d = AugData.get(sp);
            if (d.energy >= d.maxEnergy() - 0.5f && d.overheat == 0) {
                sp.displayClientMessage(Component.translatable("msg.nanoaug.energy_full"), true);
                return InteractionResultHolder.fail(stack);
            }
            d.energy = Math.min(d.maxEnergy(), d.energy + 50f);
            if (d.overheat > 0) d.overheat = Math.max(0, d.overheat - 100);
            stack.consume(1, player);
            player.getCooldowns().addCooldown(this, 20);
            level.playSound(null, player.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.7f, 1.6f);
            Net.sync(sp);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
