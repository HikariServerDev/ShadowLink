package dev.atsukimc.shadowlink.mixin;

import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.collection.DefaultedList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LootableContainerBlockEntity.class)
public interface LootableContainerBlockEntityInvoker {
    /** Reads the backing list without triggering loot table generation. */
    @Invoker("getInvStackList")
    DefaultedList<ItemStack> shadowlink$getInvStackList();
}
