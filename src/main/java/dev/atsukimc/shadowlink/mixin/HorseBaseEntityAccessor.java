package dev.atsukimc.shadowlink.mixin;

//? if >=1.16 {
import net.minecraft.entity.passive.HorseBaseEntity;
import net.minecraft.inventory.SimpleInventory;
//?} else {
/*import net.minecraft.entity.AbstractHorseEntity;
import net.minecraft.inventory.AnimalInventory;
*///?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

//? if >=1.16 {
@Mixin(HorseBaseEntity.class)
//?} else {
/*@Mixin(AbstractHorseEntity.class)
*///?}
public interface HorseBaseEntityAccessor {
    //? if >=1.16 {
    @Accessor("items")
    SimpleInventory shadowlink$getItems();
    //?} else {
    /*@Accessor("animalInventory")
    AnimalInventory shadowlink$getItems();
    *///?}
}
