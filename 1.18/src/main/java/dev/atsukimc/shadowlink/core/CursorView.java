package dev.atsukimc.shadowlink.core;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

/** Read-only single-slot view of a player's cursor stack, so it can be scanned like any holder. */
final class CursorView implements Inventory {
    private final ServerPlayerEntity player;

    CursorView(ServerPlayerEntity player) {
        this.player = player;
    }

    @Override
    public int size() {
        return 1;
    }

    @Override
    public boolean isEmpty() {
        return this.getStack(0).isEmpty();
    }

    @Override
    public ItemStack getStack(int slot) {
        return this.player.currentScreenHandler == null ? ItemStack.EMPTY : this.player.currentScreenHandler.getCursorStack();
    }

    @Override
    public ItemStack removeStack(int slot, int amount) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ItemStack removeStack(int slot) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setStack(int slot, ItemStack stack) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void markDirty() {
    }

    @Override
    public boolean canPlayerUse(PlayerEntity player) {
        return false;
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException();
    }
}
