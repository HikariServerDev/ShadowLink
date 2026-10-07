package dev.atsukimc.shadowlink.mixin;

import dev.atsukimc.shadowlink.core.ShadowManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.util.ItemAction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ScreenHandler.class)
public abstract class ScreenHandlerMixin {
    // HEAD rather than RETURN: a click that creates a shadow may end in an exception, so the
    // handler is only queued here and inspected afterwards by ShadowManager.
    @Inject(method = "method_3252", at = @At("HEAD"))
    private void shadowlink$clicked(int slotIndex, int button, ItemAction action, PlayerEntity player, CallbackInfoReturnable<ItemStack> cir) {
        if (player instanceof ServerPlayerEntity) {
            ShadowManager.onSlotClick((ScreenHandler) (Object) this, (ServerPlayerEntity) player);
        }
    }
}
