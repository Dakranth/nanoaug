package com.nanoaug;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.ArrayList;
import java.util.List;

public class Net {
    private static final StreamCodec<ByteBuf, List<String>> STRS = ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list());

    /** servidor -> cliente */
    public record Sync(int praxis, List<String> installed, List<String> active, float energy, int overclock, int overheat)
            implements CustomPacketPayload {
        public static final Type<Sync> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(NanoAug.MODID, "sync"));
        public static final StreamCodec<ByteBuf, Sync> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Sync::praxis,
                STRS, Sync::installed,
                STRS, Sync::active,
                ByteBufCodecs.FLOAT, Sync::energy,
                ByteBufCodecs.VAR_INT, Sync::overclock,
                ByteBufCodecs.VAR_INT, Sync::overheat,
                Sync::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** cliente -> servidor. op: 0 = instalar, 1 = usar/alternar, 2 = overclock */
    public record Request(String id, byte op) implements CustomPacketPayload {
        public static final Type<Request> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(NanoAug.MODID, "request"));
        public static final StreamCodec<ByteBuf, Request> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Request::id,
                ByteBufCodecs.BYTE, Request::op,
                Request::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static void register(RegisterPayloadHandlersEvent e) {
        PayloadRegistrar r = e.registrar("1");
        r.playToClient(Sync.TYPE, Sync.CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> com.nanoaug.client.ClientHooks.sync(p)));
        r.playToServer(Request.TYPE, Request.CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> {
                    if (ctx.player() instanceof ServerPlayer sp) Logic.handle(sp, p);
                }));
    }

    public static void sync(ServerPlayer p) {
        AugData d = AugData.get(p);
        PacketDistributor.sendToPlayer(p, new Sync(d.praxis, new ArrayList<>(d.installed),
                new ArrayList<>(d.active), d.energy, d.overclock, d.overheat));
    }
}
