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
import java.util.HashMap;
import java.util.List;

public class Net {
    private static final StreamCodec<ByteBuf, List<String>> STRS = ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list());
    private static final StreamCodec<ByteBuf, HashMap<String, Integer>> LEVELS =
            ByteBufCodecs.<ByteBuf, String, Integer, HashMap<String, Integer>>map(HashMap::new,
                    ByteBufCodecs.STRING_UTF8, ByteBufCodecs.VAR_INT);

    /** servidor -> cliente */
    public record Sync(int praxis, HashMap<String, Integer> levels, List<String> active, float energy,
                       int overclock, int overheat, List<String> slots) implements CustomPacketPayload {
        public static final Type<Sync> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(NanoAug.MODID, "sync"));
        public static final StreamCodec<ByteBuf, Sync> CODEC = StreamCodec.of(Sync::write, Sync::read);

        private static void write(ByteBuf b, Sync s) {
            ByteBufCodecs.VAR_INT.encode(b, s.praxis());
            LEVELS.encode(b, s.levels());
            STRS.encode(b, s.active());
            ByteBufCodecs.FLOAT.encode(b, s.energy());
            ByteBufCodecs.VAR_INT.encode(b, s.overclock());
            ByteBufCodecs.VAR_INT.encode(b, s.overheat());
            STRS.encode(b, s.slots());
        }

        private static Sync read(ByteBuf b) {
            int praxis = ByteBufCodecs.VAR_INT.decode(b);
            HashMap<String, Integer> lv = LEVELS.decode(b);
            List<String> act = STRS.decode(b);
            float en = ByteBufCodecs.FLOAT.decode(b);
            int oc = ByteBufCodecs.VAR_INT.decode(b);
            int oh = ByteBufCodecs.VAR_INT.decode(b);
            List<String> sl = STRS.decode(b);
            return new Sync(praxis, lv, act, en, oc, oh, sl);
        }

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** cliente -> servidor. op: 0 melhorar, 1 usar/alternar, 2 overclock, 3 equipar (slot:aug), 5 dash mirado */
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
        PacketDistributor.sendToPlayer(p, new Sync(d.praxis, new HashMap<>(d.levels),
                new ArrayList<>(d.active), d.energy, d.overclock, d.overheat,
                new ArrayList<>(java.util.Arrays.asList(d.slots))));
    }
}
