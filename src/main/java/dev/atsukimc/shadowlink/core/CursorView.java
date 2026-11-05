package dev.atsukimc.shadowlink.core;

import net.minecraft.entity.player.PlayerEntity;
//? if <1.16 {
/*import net.minecraft.entity.player.ServerPlayerEntity;
*///?}
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
//? if >=1.16 {
import net.minecraft.server.network.ServerPlayerEntity;
//?} else {
/*import net.minecraft.text.Text;
*///?}

/** Read-only single-slot view of a player's cursor stack, so it can be scanned like any holder. */
final class CursorView implements Inventory {
    private final ServerPlayerEntity player;

    CursorView(ServerPlayerEntity player) {
        this.player = player;
    }

    @Override
    //? if >=1.16 {
    public int size() {
    //?} else {
    /*public int getInvSize() {
    *///?}
        return 1;
    }

    @Override
    public boolean isEmpty() {
        //? if >=1.16 {
        return this.getStack(0).isEmpty();
        //?} else {
        /*return this.getInvStack(0).isEmpty();
        *///?}
    }

    @Override
    //? if >=1.18 {
    public ItemStack getStack(int slot) {
        return this.player.currentScreenHandler == null ? ItemStack.EMPTY : this.player.currentScreenHandler.getCursorStack();
    //?} else if >=1.16 && <1.18 {
    /*public ItemStack getStack(int slot) {
        return this.player.inventory.getCursorStack();
    *///?} else {
    /*public ItemStack getInvStack(int slot) {
        return this.player.inventory.getCursorStack();
    *///?}
    }

    @Override
    //? if >=1.16 {
    public ItemStack removeStack(int slot, int amount) {
    //?} else {
    /*public ItemStack takeInvStack(int slot, int amount) {
    *///?}
        throw new UnsupportedOperationException();
    }

    @Override
    //? if >=1.16 {
    public ItemStack removeStack(int slot) {
    //?} else {
    /*public ItemStack removeInvStack(int slot) {
    *///?}
        throw new UnsupportedOperationException();
    }

    @Override
    //? if >=1.16 {
    public void setStack(int slot, ItemStack stack) {
    //?} else {
    /*public void setInvStack(int slot, ItemStack stack) {
    *///?}
        throw new UnsupportedOperationException();
    //? if <1.16 {
    /*}

    @Override
    public int getInvMaxStackAmount() {
        return 64;
    *///?}
    }

    @Override
    public void markDirty() {
    }

    @Override
    //? if >=1.16 {
    public boolean canPlayerUse(PlayerEntity player) {
    //?} else {
    /*public boolean canPlayerUseInv(PlayerEntity player) {
    *///?}
        return false;
    //? if <1.16 {
    /*}

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
    *///?}
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException();
    }
    //? if <1.16 {
    /*
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
    *///?}
}
