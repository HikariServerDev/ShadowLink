package dev.atsukimc.shadowlink.mixin;

import dev.atsukimc.shadowlink.core.ShadowManager;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Chunk.class)
public abstract class ChunkMixin {
    @Shadow @Final private World world;
    @Shadow private boolean loaded;

    /**
     * The chunk's block entities were just read from disk but are not yet handed to the world,
     * so re-linking never registers anything twice.
     */
    @Inject(method = "loadToWorld", at = @At("HEAD"))
    private void shadowlink$loading(CallbackInfo ci) {
        if (!this.loaded && this.world instanceof ServerWorld) {
            ShadowManager.onChunkLoaded((ServerWorld) this.world, (Chunk) (Object) this);
        }
    }

    /** The chunk is serialized right after this call, with its block entities still intact. */
    @Inject(method = "unloadFromWorld", at = @At("HEAD"))
    private void shadowlink$unloading(CallbackInfo ci) {
        if (this.world instanceof ServerWorld) {
            ShadowManager.onChunkUnloading((ServerWorld) this.world, (Chunk) (Object) this);
        }
    }

    // A block entity put into an already loaded chunk may carry a tracked stack to a new position.
    @Inject(method = "addBlockEntity", at = @At("TAIL"))
    private void shadowlink$blockEntityAdded(BlockEntity blockEntity, CallbackInfo ci) {
        if (this.loaded && blockEntity instanceof Inventory && this.world instanceof ServerWorld) {
            ShadowManager.onBlockEntityPlaced((ServerWorld) this.world, blockEntity);
        }
    }
}
