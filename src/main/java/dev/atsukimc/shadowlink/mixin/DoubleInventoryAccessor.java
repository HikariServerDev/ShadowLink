package dev.atsukimc.shadowlink.mixin;

//? if <1.16 {
/*import net.minecraft.block.entity.LockableScreenHandlerFactory;
*///?}
import net.minecraft.inventory.DoubleInventory;
//? if >=1.16 {
import net.minecraft.inventory.Inventory;
//?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(DoubleInventory.class)
public interface DoubleInventoryAccessor {
    //? if >=1.16 {
    @Accessor("first")
    Inventory shadowlink$getFirst();
    //?} else {
    /*@Accessor("mainInventory")
    LockableScreenHandlerFactory shadowlink$getFirst();
    *///?}

    //? if >=1.16 {
    @Accessor("second")
    Inventory shadowlink$getSecond();
    //?} else {
    /*@Accessor("secondaryInventory")
    LockableScreenHandlerFactory shadowlink$getSecond();
    *///?}
}
