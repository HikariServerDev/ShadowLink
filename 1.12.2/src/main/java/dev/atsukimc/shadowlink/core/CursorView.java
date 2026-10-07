package dev.atsukimc.shadowlink.core;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

/** Read-only single-slot view of a player's cursor stack, so it can be scanned like any holder. */
final class CursorView implements Inventory {
    private final ServerPlayerEntity player;

    CursorView(ServerPlayerEntity player) {
        this.player = player;
    }

    @Override
    public int getInvSize() {
        return 1;
    }

    @Override
    public boolean isEmpty() {
        return this.getInvStack(0).isEmpty();
    }

    @Override
    public ItemStack getInvStack(int slot) {
        return this.player.inventory.getCursorStack();
    }

    @Override
    public ItemStack takeInvStack(int slot, int amount) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ItemStack removeInvStack(int slot) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setInvStack(int slot, ItemStack stack) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getInvMaxStackAmount() {
        return 64;
    }

    @Override
    public void markDirty() {
    }

    @Override
    public boolean canPlayerUseInv(PlayerEntity player) {
        return false;
    }

    @Override
    public void onInvOpen(PlayerEntity player) {
    }

    @Override
    public void onInvClose(PlayerEntity player) {
    }

    @Override
    public boolean isValidInvStack(int slot, ItemStack stack) {
        return false;
    }

    @Override
    public int getProperty(int key) {
        return 0;
    }

    @Override
    public void setProperty(int id, int value) {
    }

    @Override
    public int getProperties() {
        return 0;
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getTranslationKey() {
        return "shadowlink.cursor";
    }

    @Override
    public boolean hasCustomName() {
        return false;
    }

    @Override
    public Text getName() {
        throw new UnsupportedOperationException();
    }
}
