package com.nanoaug;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

@Mod(NanoAug.MODID)
public class NanoAug {
    public static final String MODID = "nanoaug";

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, MODID);

    public static final DeferredItem<Item> PRAXIS_KIT = ITEMS.register("praxis_kit",
            () -> new PraxisKitItem(new Item.Properties().stacksTo(16).rarity(Rarity.EPIC)));

    public static final Supplier<AttachmentType<AugData>> AUG_DATA = ATTACHMENTS.register("aug_data",
            () -> AttachmentType.builder(AugData::new).serialize(AugData.CODEC).copyOnDeath().build());

    public static final Supplier<CreativeModeTab> TAB = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.nanoaug"))
                    .icon(() -> new ItemStack(PRAXIS_KIT.get()))
                    .displayItems((params, out) -> out.accept(PRAXIS_KIT.get()))
                    .build());

    public NanoAug(IEventBus bus) {
        ITEMS.register(bus);
        TABS.register(bus);
        ATTACHMENTS.register(bus);
        bus.addListener(Net::register);
    }
}
