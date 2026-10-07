package dev.atsukimc.shadowlink.mixin;

import net.minecraft.entity.passive.HorseBaseEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.screen.HorseScreenHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(HorseScreenHandler.class)
public interface HorseScreenHandlerAccessor {
    @Accessor("inventory")
    Inventory shadowlink$getInventory();

    @Accessor("entity")
    HorseBaseEntity shadowlink$getEntity();
}
