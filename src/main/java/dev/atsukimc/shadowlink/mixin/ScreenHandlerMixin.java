package dev.atsukimc.shadowlink.mixin;

import dev.atsukimc.shadowlink.core.ShadowManager;
import net.minecraft.entity.player.PlayerEntity;
//? if >=1.18 {
//?} else if >=1.16 && <1.18 {
/*import net.minecraft.item.ItemStack;
*///?} else {
/*import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
*///?}
import net.minecraft.screen.ScreenHandler;
//? if >=1.16 {
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
//?} else {
/*import net.minecraft.util.ItemAction;
*///?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
//? if >=1.18 {
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//?} else {
/*import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
*///?}

@Mixin(ScreenHandler.class)
public abstract class ScreenHandlerMixin {

    // handler is only queued here and inspected afterwards by ShadowManager.
    //? if >=1.18 {
    @Inject(method = "onSlotClick", at = @At("HEAD"))
    private void shadowlink$clicked(int slotIndex, int button, SlotActionType actionType, PlayerEntity player, CallbackInfo ci) {
        if (player instanceof ServerPlayerEntity serverPlayer) {
            ShadowManager.onSlotClick((ScreenHandler) (Object) this, serverPlayer);
    //?} else if >=1.16 && <1.18 {
    /*@Inject(method = "onSlotClick", at = @At("HEAD"))
    private void shadowlink$clicked(int slotIndex, int button, SlotActionType actionType, PlayerEntity player, CallbackInfoReturnable<ItemStack> cir) {
        if (player instanceof ServerPlayerEntity) {
            ShadowManager.onSlotClick((ScreenHandler) (Object) this, (ServerPlayerEntity) player);
    *///?} else {
    /*@Inject(method = "method_3252", at = @At("HEAD"))
    private void shadowlink$clicked(int slotIndex, int button, ItemAction action, PlayerEntity player, CallbackInfoReturnable<ItemStack> cir) {
        if (player instanceof ServerPlayerEntity) {
            ShadowManager.onSlotClick((ScreenHandler) (Object) this, (ServerPlayerEntity) player);
    *///?}
        }
    }
}
