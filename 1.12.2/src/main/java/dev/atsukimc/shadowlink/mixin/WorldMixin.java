package dev.atsukimc.shadowlink.mixin;

import dev.atsukimc.shadowlink.core.ShadowManager;
import net.minecraft.entity.AbstractHorseEntity;
import net.minecraft.entity.Entity;
import net.minecraft.inventory.Inventory;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Load / unload of inventory-carrying entities (storage minecarts, horses, donkeys, llamas). */
@Mixin(World.class)
public abstract class WorldMixin {
    @Inject(method = "onEntitySpawned", at = @At("TAIL"))
    private void shadowlink$spawned(Entity entity, CallbackInfo ci) {
        if (entity instanceof Inventory || entity instanceof AbstractHorseEntity) {
            ShadowManager.onEntityLoaded(entity);
        }
    }

    // Reached both for a chunk unload and for removal (death, portal); the manager tells them
    // apart by Entity#removed.
    @Inject(method = "onEntityRemoved", at = @At("HEAD"))
    private void shadowlink$removed(Entity entity, CallbackInfo ci) {
        if (entity instanceof Inventory || entity instanceof AbstractHorseEntity) {
            ShadowManager.onEntityUnloading(entity);
        }
    }
}
