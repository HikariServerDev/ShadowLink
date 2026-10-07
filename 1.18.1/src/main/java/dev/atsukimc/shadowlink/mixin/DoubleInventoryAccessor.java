package dev.atsukimc.shadowlink.mixin;

import net.minecraft.inventory.DoubleInventory;
import net.minecraft.inventory.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(DoubleInventory.class)
public interface DoubleInventoryAccessor {
    @Accessor("first")
    Inventory shadowlink$getFirst();

    @Accessor("second")
    Inventory shadowlink$getSecond();
}
