package com.example.soundattract.gametest.mixin;

import com.example.soundattract.gametest.BlockBreakProgressPacketProbe;
import com.example.soundattract.gametest.FromTheFogMarkerPacketProbe;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerCommonPacketListenerImpl.class)
abstract class ServerGamePacketListenerImplMixin {
    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"))
    private void soundattract$observeBlockBreakProgress(Packet<?> packet, CallbackInfo callback) {
        BlockBreakProgressPacketProbe.capture(packet);
        FromTheFogMarkerPacketProbe.capture(packet);
    }
}
