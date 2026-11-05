package dev.atsukimc.shadowlink.mixin;

import dev.atsukimc.shadowlink.core.ShadowManager;
//? if <1.16 {
/*import net.minecraft.entity.player.ServerPlayerEntity;
*///?}
import net.minecraft.network.ClientConnection;
import net.minecraft.server.PlayerManager;
//? if >=1.16 {
import net.minecraft.server.network.ServerPlayerEntity;
//?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerManager.class)
public abstract class PlayerManagerMixin {
    //? if >=1.16 {
    @Inject(method = "onPlayerConnect", at = @At("TAIL"))
    //?} else {
    /*
    @Inject(method = "method_12827", at = @At("TAIL"))
    *///?}
    private void shadowlink$joined(ClientConnection connection, ServerPlayerEntity player, CallbackInfo ci) {
        ShadowManager.onPlayerJoined(player);
    }

    //? if >=1.16 {
    @Inject(method = "remove", at = @At("HEAD"))
    //?} else {
    /*@Inject(method = "method_12830", at = @At("HEAD"))
    *///?}
    private void shadowlink$leaving(ServerPlayerEntity player, CallbackInfo ci) {
        ShadowManager.onPlayerLeaving(player);
    }
}
