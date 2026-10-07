package dev.atsukimc.shadowlink.mixin;

import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Slot#getIndex only exists from 1.17 on. */
@Mixin(Slot.class)
public interface SlotAccessor {
    @Accessor("index")
    int shadowlink$getIndex();
}
