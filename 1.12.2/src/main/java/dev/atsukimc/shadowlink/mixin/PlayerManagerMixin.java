package dev.atsukimc.shadowlink.mixin;

import dev.atsukimc.shadowlink.core.ShadowManager;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.ClientConnection;
import net.minecraft.server.PlayerManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Every ServerPlayerEntity (including fake players spawned by other mods) enters and leaves through these two. */
@Mixin(PlayerManager.class)
public abstract class PlayerManagerMixin {
    // initializeConnectionToPlayer
    @Inject(method = "method_12827", at = @At("TAIL"))
    private void shadowlink$joined(ClientConnection connection, ServerPlayerEntity player, CallbackInfo ci) {
        ShadowManager.onPlayerJoined(player);
    }

    // playerLoggedOut; HEAD: the inventory is still intact and is about to be written to the player file.
    @Inject(method = "method_12830", at = @At("HEAD"))
    private void shadowlink$leaving(ServerPlayerEntity player, CallbackInfo ci) {
        ShadowManager.onPlayerLeaving(player);
    }
}
