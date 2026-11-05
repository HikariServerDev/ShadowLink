package dev.atsukimc.shadowlink.core;

//? if >=1.16 {
import dev.atsukimc.shadowlink.mixin.AbstractFurnaceBlockEntityAccessor;
//?} else {
/*import dev.atsukimc.shadowlink.mixin.FurnaceBlockEntityAccessor;
*///?}
import dev.atsukimc.shadowlink.mixin.HorseBaseEntityAccessor;
import dev.atsukimc.shadowlink.mixin.LootableContainerBlockEntityInvoker;
import dev.atsukimc.shadowlink.mixin.SimpleInventoryAccessor;
import dev.atsukimc.shadowlink.mixin.StorageMinecartEntityAccessor;
//? if >=1.16 {
import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.block.entity.LootableContainerBlockEntity;
//?} else {
/*import net.minecraft.block.entity.class_2737;
import net.minecraft.block.entity.FurnaceBlockEntity;
import net.minecraft.entity.AbstractHorseEntity;
*///?}
import net.minecraft.entity.Entity;
//? if >=1.16 {
import net.minecraft.entity.passive.HorseBaseEntity;
//?}
import net.minecraft.entity.vehicle.StorageMinecartEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.collection.DefaultedList;

final class InventoryAccess {
    private static final NbtCompound EMPTY_SNAPSHOT = new NbtCompound();

    private InventoryAccess() {
    }

    private static DefaultedList<ItemStack> backingList(Inventory inventory) {
        //? if >=1.16 {
        if (inventory instanceof LootableContainerBlockEntity) {
        //?} else {
        /*if (inventory instanceof class_2737) {

        *///?}
            return ((LootableContainerBlockEntityInvoker) inventory).shadowlink$getInvStackList();
        }
        //? if >=1.16 {
        if (inventory instanceof AbstractFurnaceBlockEntity) {
            return ((AbstractFurnaceBlockEntityAccessor) inventory).shadowlink$getInventory();
        //?} else {
        /*if (inventory instanceof FurnaceBlockEntity) {
            return ((FurnaceBlockEntityAccessor) inventory).shadowlink$getStacks();
        *///?}
        }
        if (inventory instanceof StorageMinecartEntity) {
            return ((StorageMinecartEntityAccessor) inventory).shadowlink$getInventory();
        }
        if (inventory instanceof SimpleInventory) {
            return ((SimpleInventoryAccessor) inventory).shadowlink$getStacks();
        }

        return null;
    }

    static int size(Inventory inventory) {
        DefaultedList<ItemStack> list = backingList(inventory);
        //? if >=1.16 {
        return list != null ? list.size() : inventory.size();
        //?} else {
        /*return list != null ? list.size() : inventory.getInvSize();
        *///?}
    }

    static ItemStack get(Inventory inventory, int slot) {
        DefaultedList<ItemStack> list = backingList(inventory);
        if (list != null) {
            return slot >= 0 && slot < list.size() ? list.get(slot) : ItemStack.EMPTY;
        }
        //? if >=1.16 {
        return slot >= 0 && slot < inventory.size() ? inventory.getStack(slot) : ItemStack.EMPTY;
        //?} else {
        /*return slot >= 0 && slot < inventory.getInvSize() ? inventory.getInvStack(slot) : ItemStack.EMPTY;
        *///?}
    }

    static void set(Inventory inventory, int slot, ItemStack stack) {
        DefaultedList<ItemStack> list = backingList(inventory);
        if (list != null) {
            list.set(slot, stack);
        } else {
            //? if >=1.16 {
            inventory.setStack(slot, stack);
            //?} else {
            /*inventory.setInvStack(slot, stack);
            *///?}
        }
    }

    /** The slot container of an entity, or null if this entity type is not tracked. */
    static Inventory inventoryOf(Entity entity) {
        //? if >=1.18 {
        if (entity instanceof Inventory inventory) {
            return inventory;
        //?} else {
        /*if (entity instanceof Inventory) {
            return (Inventory) entity;
        *///?}
        }
        //? if >=1.16 {
        if (entity instanceof HorseBaseEntity) {
        //?} else {
        /*if (entity instanceof AbstractHorseEntity) {
        *///?}
            return ((HorseBaseEntityAccessor) entity).shadowlink$getItems();
        }
        return null;
    }

    /**
     * NBT form of a stack as it looks after one save/load cycle. Loading is not an exact
     * inverse of saving (e.g. damageable items gain {@code Damage:0}), so both sides of every
     * comparison go through the same round trip. Empty stacks are an empty compound.
     */
    static NbtCompound snapshotOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return EMPTY_SNAPSHOT;
        }
        //? if >=1.16.5 {
        ItemStack reloaded = ItemStack.fromNbt(stack.writeNbt(new NbtCompound()));
        //?} else if >=1.16 && <1.16.5 {
        /*ItemStack reloaded = ItemStack.fromTag(stack.toTag(new NbtCompound()));
        *///?} else {
        /*ItemStack reloaded = fromNbt(stack.toNbt(new NbtCompound()));
        *///?}
        if (reloaded.isEmpty()) {
            return EMPTY_SNAPSHOT;
        }
        //? if >=1.16.5 {
        return reloaded.writeNbt(new NbtCompound());
        //?} else if >=1.16 && <1.16.5 {
        /*return reloaded.toTag(new NbtCompound());
        *///?} else {
        /*return reloaded.toNbt(new NbtCompound());
        *///?}
    }

    static ItemStack fromSnapshot(NbtCompound snapshot) {

    //? if >=1.16.5 {
        return snapshot.isEmpty() ? ItemStack.EMPTY : ItemStack.fromNbt(snapshot.copy());
    //?} else if >=1.16 && <1.16.5 {
    /*    return snapshot.isEmpty() ? ItemStack.EMPTY : ItemStack.fromTag(snapshot.copy());
    *///?} else {
    /*    return snapshot.isEmpty() ? ItemStack.EMPTY : fromNbt(snapshot.copy());
    }

    private static ItemStack fromNbt(NbtCompound nbt) {
        try {
            ItemStack stack = new ItemStack(nbt);
            return stack.isEmpty() ? ItemStack.EMPTY : stack;
        } catch (RuntimeException e) {
            return ItemStack.EMPTY;
        }
    *///?}
    }
}
