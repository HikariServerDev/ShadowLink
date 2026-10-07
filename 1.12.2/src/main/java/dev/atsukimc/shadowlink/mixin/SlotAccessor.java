package dev.atsukimc.shadowlink.mixin;

import net.minecraft.inventory.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Slot.class)
public interface SlotAccessor {
    @Accessor("invSlot")
    int shadowlink$getIndex();
}
