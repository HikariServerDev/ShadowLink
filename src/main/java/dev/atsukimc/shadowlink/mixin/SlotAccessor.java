//? if >=1.18 {
//?} else if >=1.16 && <1.18 {
/*package dev.atsukimc.shadowlink.mixin;

import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Slot.class)
public interface SlotAccessor {
    @Accessor("index")
    int shadowlink$getIndex();
}
*///?} else {
/*package dev.atsukimc.shadowlink.mixin;

import net.minecraft.inventory.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Slot.class)
public interface SlotAccessor {
    @Accessor("invSlot")
    int shadowlink$getIndex();
}
*///?}
