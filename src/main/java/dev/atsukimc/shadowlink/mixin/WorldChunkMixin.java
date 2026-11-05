//? if >=1.18 {
package dev.atsukimc.shadowlink.mixin;

import dev.atsukimc.shadowlink.core.ShadowManager;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldChunk.class)
public abstract class WorldChunkMixin {
    @Shadow @Final World world;
    @Shadow private boolean loadedToWorld;

    @Inject(method = "setLoadedToWorld", at = @At("TAIL"))
    private void shadowlink$loadState(boolean loaded, CallbackInfo ci) {
        if (this.world instanceof ServerWorld serverWorld) {
            if (loaded) {
                ShadowManager.onChunkLoaded(serverWorld, (WorldChunk) (Object) this);
            } else {
                ShadowManager.onChunkUnloading(serverWorld, (WorldChunk) (Object) this);
            }
        }
    }

    // A block entity put into an already loaded chunk (placed, or moved by a piston with
    // Carpet's movable block entities) may carry a tracked stack to a new position.
    @Inject(method = "setBlockEntity", at = @At("TAIL"))
    private void shadowlink$blockEntitySet(BlockEntity blockEntity, CallbackInfo ci) {
        if (this.loadedToWorld && blockEntity instanceof Inventory && this.world instanceof ServerWorld serverWorld) {
            ShadowManager.onBlockEntityPlaced(serverWorld, blockEntity);
        }
    }
}
//?} else if >=1.16 && <1.18 {
/*package dev.atsukimc.shadowlink.mixin;

import dev.atsukimc.shadowlink.core.ShadowManager;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldChunk.class)
public abstract class WorldChunkMixin {
    @Shadow @Final private World world;
    @Shadow private boolean loadedToWorld;

    @Inject(method = "loadToWorld", at = @At("TAIL"))
    private void shadowlink$loaded(CallbackInfo ci) {
        if (!this.loadedToWorld && this.world instanceof ServerWorld) {
            ShadowManager.onChunkLoaded((ServerWorld) this.world, (WorldChunk) (Object) this);
        }
    }

    @Inject(method = "setLoadedToWorld", at = @At("TAIL"))
    private void shadowlink$unloading(boolean loaded, CallbackInfo ci) {
        if (!loaded && this.world instanceof ServerWorld) {
            ShadowManager.onChunkUnloading((ServerWorld) this.world, (WorldChunk) (Object) this);
        }
    }

    // A block entity put into an already loaded chunk (placed, or moved by a piston with
    // Carpet's movable block entities) may carry a tracked stack to a new position.
    @Inject(method = "setBlockEntity", at = @At("TAIL"))
    private void shadowlink$blockEntitySet(BlockPos pos, BlockEntity blockEntity, CallbackInfo ci) {
        if (this.loadedToWorld && blockEntity instanceof Inventory && this.world instanceof ServerWorld) {
            ShadowManager.onBlockEntityPlaced((ServerWorld) this.world, blockEntity);
        }
    }
}
*///?} else {
/**///?}
