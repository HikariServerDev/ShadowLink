package dev.atsukimc.shadowlink.mixin;

import net.minecraft.entity.AbstractHorseEntity;
import net.minecraft.screen.HorseScreenHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(HorseScreenHandler.class)
public interface HorseScreenHandlerAccessor {
    @Accessor("field_15101")
    AbstractHorseEntity shadowlink$getEntity();
}
