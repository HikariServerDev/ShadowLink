package dev.atsukimc.shadowlink.mixin;

import net.minecraft.entity.AbstractHorseEntity;
import net.minecraft.inventory.AnimalInventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractHorseEntity.class)
public interface HorseBaseEntityAccessor {
    @Accessor("animalInventory")
    AnimalInventory shadowlink$getItems();
}
