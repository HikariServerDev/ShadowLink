package dev.atsukimc.shadowlink.mixin;

import net.minecraft.block.entity.LockableScreenHandlerFactory;
import net.minecraft.inventory.DoubleInventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(DoubleInventory.class)
public interface DoubleInventoryAccessor {
    @Accessor("mainInventory")
    LockableScreenHandlerFactory shadowlink$getFirst();

    @Accessor("secondaryInventory")
    LockableScreenHandlerFactory shadowlink$getSecond();
}
