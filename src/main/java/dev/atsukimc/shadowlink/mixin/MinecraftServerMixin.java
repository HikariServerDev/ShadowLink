package dev.atsukimc.shadowlink.mixin;

import dev.atsukimc.shadowlink.core.ShadowManager;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//? if >=1.16 {
import java.util.function.BooleanSupplier;

//?}
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    //? if >=1.16 {
    private void shadowlink$endTick(BooleanSupplier shouldKeepTicking, CallbackInfo ci) {
    //?} else {
    /*private void shadowlink$endTick(CallbackInfo ci) {
    *///?}
        ShadowManager.onEndTick((MinecraftServer) (Object) this);
    }

    //? if >=1.16 {
    @Inject(method = "shutdown", at = @At("TAIL"))
    //?} else {
    /*@Inject(method = "stopServer", at = @At("TAIL"))
    *///?}
    private void shadowlink$stopped(CallbackInfo ci) {
        ShadowManager.onServerStopped((MinecraftServer) (Object) this);
    }
}
