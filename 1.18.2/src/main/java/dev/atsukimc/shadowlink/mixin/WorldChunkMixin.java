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

    /**
     * ThreadedAnvilChunkStorage calls this with {@code true} once the block entities read from
     * disk are in place but before they start ticking, and with {@code false} immediately
     * before the chunk is serialized for unloading. Both are exactly the moments we need.
     */
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
