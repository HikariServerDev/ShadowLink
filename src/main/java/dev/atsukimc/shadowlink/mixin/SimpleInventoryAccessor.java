package dev.atsukimc.shadowlink.mixin;

import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.collection.DefaultedList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SimpleInventory.class)
public interface SimpleInventoryAccessor {
    //? if >=1.16 {
    @Accessor("stacks")
    //?} else {
    /*@Accessor("content")
    *///?}
    DefaultedList<ItemStack> shadowlink$getStacks();
}
