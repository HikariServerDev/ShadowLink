package dev.atsukimc.shadowlink.mixin;

import net.minecraft.block.entity.FurnaceBlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.collection.DefaultedList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(FurnaceBlockEntity.class)
public interface FurnaceBlockEntityAccessor {
    @Accessor("field_15154")
    DefaultedList<ItemStack> shadowlink$getStacks();
}
