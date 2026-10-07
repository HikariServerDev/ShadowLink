package dev.atsukimc.shadowlink.mixin;

import net.minecraft.block.entity.class_2737;
import net.minecraft.item.ItemStack;
import net.minecraft.util.collection.DefaultedList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(class_2737.class)
public interface LootableContainerBlockEntityInvoker {
    /** Backing list of chests, hoppers, dispensers and shulker boxes; no loot table generation. */
    @Invoker("method_13730")
    DefaultedList<ItemStack> shadowlink$getInvStackList();
}
