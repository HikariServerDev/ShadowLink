package dev.atsukimc.shadowlink.mixin;

//? if >=1.16 {
import net.minecraft.entity.passive.HorseBaseEntity;
import net.minecraft.inventory.Inventory;
//?} else {
/*import net.minecraft.entity.AbstractHorseEntity;
*///?}
import net.minecraft.screen.HorseScreenHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(HorseScreenHandler.class)
public interface HorseScreenHandlerAccessor {
    //? if >=1.16 {
    @Accessor("inventory")
    Inventory shadowlink$getInventory();

    @Accessor("entity")
    HorseBaseEntity shadowlink$getEntity();
    //?} else {
    /*@Accessor("field_15101")
    AbstractHorseEntity shadowlink$getEntity();
    *///?}
}
