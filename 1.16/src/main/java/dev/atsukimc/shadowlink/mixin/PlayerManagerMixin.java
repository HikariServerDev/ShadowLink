package dev.atsukimc.shadowlink.mixin;

import dev.atsukimc.shadowlink.core.ShadowManager;
import net.minecraft.network.ClientConnection;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Real players and Carpet fake players both enter and leave through these two methods, so
 * hooking them covers every ServerPlayerEntity without referencing Carpet.
 */
@Mixin(PlayerManager.class)
public abstract class PlayerManagerMixin {
    @Inject(method = "onPlayerConnect", at = @At("TAIL"))
    private void shadowlink$joined(ClientConnection connection, ServerPlayerEntity player, CallbackInfo ci) {
        ShadowManager.onPlayerJoined(player);
    }

    // HEAD: the inventory is still intact and is about to be written to the player file.
    @Inject(method = "remove", at = @At("HEAD"))
    private void shadowlink$leaving(ServerPlayerEntity player, CallbackInfo ci) {
        ShadowManager.onPlayerLeaving(player);
    }
}
