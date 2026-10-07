package dev.atsukimc.shadowlink.core;

import dev.atsukimc.shadowlink.mixin.FurnaceBlockEntityAccessor;
import dev.atsukimc.shadowlink.mixin.HorseBaseEntityAccessor;
import dev.atsukimc.shadowlink.mixin.LootableContainerBlockEntityInvoker;
import dev.atsukimc.shadowlink.mixin.SimpleInventoryAccessor;
import dev.atsukimc.shadowlink.mixin.StorageMinecartEntityAccessor;
import net.minecraft.block.entity.class_2737;
import net.minecraft.block.entity.FurnaceBlockEntity;
import net.minecraft.entity.AbstractHorseEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.vehicle.StorageMinecartEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.collection.DefaultedList;

/**
 * Side-effect free slot access.
 * <p>
 * {@code Inventory.getInvStack/setInvStack} are not neutral on several vanilla inventories:
 * lootable containers generate their loot table, furnaces reset their cook timer, most of them
 * clamp the stack count and fire comparator updates through markDirty. Observing or re-linking
 * a shadow must cause none of that, so the backing list is used directly wherever vanilla has one.
 */
final class InventoryAccess {
    private static final NbtCompound EMPTY_SNAPSHOT = new NbtCompound();

    private InventoryAccess() {
    }

    private static DefaultedList<ItemStack> backingList(Inventory inventory) {
        if (inventory instanceof class_2737) {
            // Chests, hoppers, dispensers/droppers and shulker boxes.
            return ((LootableContainerBlockEntityInvoker) inventory).shadowlink$getInvStackList();
        }
        if (inventory instanceof FurnaceBlockEntity) {
            return ((FurnaceBlockEntityAccessor) inventory).shadowlink$getStacks();
        }
        if (inventory instanceof StorageMinecartEntity) {
            return ((StorageMinecartEntityAccessor) inventory).shadowlink$getInventory();
        }
        if (inventory instanceof SimpleInventory) {
            return ((SimpleInventoryAccessor) inventory).shadowlink$getStacks();
        }
        // PlayerInventory, BrewingStandBlockEntity: plain getInvStack/setInvStack without side effects.
        return null;
    }

    static int size(Inventory inventory) {
        DefaultedList<ItemStack> list = backingList(inventory);
        return list != null ? list.size() : inventory.getInvSize();
    }

    static ItemStack get(Inventory inventory, int slot) {
        DefaultedList<ItemStack> list = backingList(inventory);
        if (list != null) {
            return slot >= 0 && slot < list.size() ? list.get(slot) : ItemStack.EMPTY;
        }
        return slot >= 0 && slot < inventory.getInvSize() ? inventory.getInvStack(slot) : ItemStack.EMPTY;
    }

    static void set(Inventory inventory, int slot, ItemStack stack) {
        DefaultedList<ItemStack> list = backingList(inventory);
        if (list != null) {
            list.set(slot, stack);
        } else {
            inventory.setInvStack(slot, stack);
        }
    }

    /** The slot container of an entity, or null if this entity type is not tracked. */
    static Inventory inventoryOf(Entity entity) {
        if (entity instanceof Inventory) {
            return (Inventory) entity;
        }
        if (entity instanceof AbstractHorseEntity) {
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
        ItemStack reloaded = fromNbt(stack.toNbt(new NbtCompound()));
        if (reloaded.isEmpty()) {
            return EMPTY_SNAPSHOT;
        }
        return reloaded.toNbt(new NbtCompound());
    }

    static ItemStack fromSnapshot(NbtCompound snapshot) {
        // The constructor may post-process the compound in place, hence the copy.
        return snapshot.isEmpty() ? ItemStack.EMPTY : fromNbt(snapshot.copy());
    }

    private static ItemStack fromNbt(NbtCompound nbt) {
        try {
            ItemStack stack = new ItemStack(nbt);
            return stack.isEmpty() ? ItemStack.EMPTY : stack;
        } catch (RuntimeException e) {
            return ItemStack.EMPTY;
        }
    }
}
