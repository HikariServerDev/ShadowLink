package dev.atsukimc.shadowlink.mixin;

//? if >=1.16 {
import net.minecraft.block.entity.LootableContainerBlockEntity;
//?} else {
/*import net.minecraft.block.entity.class_2737;
*///?}
import net.minecraft.item.ItemStack;
import net.minecraft.util.collection.DefaultedList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

//? if >=1.16 {
@Mixin(LootableContainerBlockEntity.class)
//?} else {
/*@Mixin(class_2737.class)
*///?}
public interface LootableContainerBlockEntityInvoker {

    //? if >=1.16 {
    @Invoker("getInvStackList")
    //?} else {
    /*@Invoker("method_13730")
    *///?}
    DefaultedList<ItemStack> shadowlink$getInvStackList();
}
