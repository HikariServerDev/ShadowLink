package dev.atsukimc.shadowlink.mixin;

import dev.atsukimc.shadowlink.core.ShadowManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.HorseBaseEntity;
import net.minecraft.inventory.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Load / unload of inventory-carrying entities (storage minecarts, horses, donkeys, llamas). */
@Mixin(targets = "net.minecraft.server.world.ServerWorld$ServerEntityHandler")
public abstract class ServerEntityHandlerMixin {
    @Inject(method = "startTracking(Lnet/minecraft/entity/Entity;)V", at = @At("TAIL"))
    private void shadowlink$loaded(Entity entity, CallbackInfo ci) {
        if (entity instanceof Inventory || entity instanceof HorseBaseEntity) {
            ShadowManager.onEntityLoaded(entity);
        }
    }

    @Inject(method = "stopTracking(Lnet/minecraft/entity/Entity;)V", at = @At("HEAD"))
    private void shadowlink$unloading(Entity entity, CallbackInfo ci) {
        if (entity instanceof Inventory || entity instanceof HorseBaseEntity) {
            ShadowManager.onEntityUnloading(entity);
        }
    }
}
