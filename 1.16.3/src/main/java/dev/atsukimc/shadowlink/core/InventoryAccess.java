package dev.atsukimc.shadowlink.core;

import dev.atsukimc.shadowlink.mixin.AbstractFurnaceBlockEntityAccessor;
import dev.atsukimc.shadowlink.mixin.HorseBaseEntityAccessor;
import dev.atsukimc.shadowlink.mixin.LootableContainerBlockEntityInvoker;
import dev.atsukimc.shadowlink.mixin.SimpleInventoryAccessor;
import dev.atsukimc.shadowlink.mixin.StorageMinecartEntityAccessor;
import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.HorseBaseEntity;
import net.minecraft.entity.vehicle.StorageMinecartEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.collection.DefaultedList;

/**
 * Side-effect free slot access.
 * <p>
 * {@code Inventory.getStack/setStack} are not neutral on several vanilla inventories: lootable
 * containers and chest minecarts generate their loot table, furnaces reset their cook timer,
 * SimpleInventory notifies listeners and most of them clamp the stack count and fire
 * comparator updates. Observing or re-linking a shadow must cause none of that, so the
 * backing list is used directly wherever vanilla has one.
 */
final class InventoryAccess {
    private static final CompoundTag EMPTY_SNAPSHOT = new CompoundTag();

    private InventoryAccess() {
    }

    private static DefaultedList<ItemStack> backingList(Inventory inventory) {
        if (inventory instanceof LootableContainerBlockEntity) {
            return ((LootableContainerBlockEntityInvoker) inventory).shadowlink$getInvStackList();
        }
        if (inventory instanceof AbstractFurnaceBlockEntity) {
            return ((AbstractFurnaceBlockEntityAccessor) inventory).shadowlink$getInventory();
        }
        if (inventory instanceof StorageMinecartEntity) {
            return ((StorageMinecartEntityAccessor) inventory).shadowlink$getInventory();
        }
        if (inventory instanceof SimpleInventory) {
            return ((SimpleInventoryAccessor) inventory).shadowlink$getStacks();
        }
        // PlayerInventory, BrewingStandBlockEntity: plain getStack/setStack without side effects.
        return null;
    }

    static int size(Inventory inventory) {
        DefaultedList<ItemStack> list = backingList(inventory);
        return list != null ? list.size() : inventory.size();
    }

    static ItemStack get(Inventory inventory, int slot) {
        DefaultedList<ItemStack> list = backingList(inventory);
        if (list != null) {
            return slot >= 0 && slot < list.size() ? list.get(slot) : ItemStack.EMPTY;
        }
        return slot >= 0 && slot < inventory.size() ? inventory.getStack(slot) : ItemStack.EMPTY;
    }

    static void set(Inventory inventory, int slot, ItemStack stack) {
        DefaultedList<ItemStack> list = backingList(inventory);
        if (list != null) {
            list.set(slot, stack);
        } else {
            inventory.setStack(slot, stack);
        }
    }

    /** The slot container of an entity, or null if this entity type is not tracked. */
    static Inventory inventoryOf(Entity entity) {
        if (entity instanceof Inventory) {
            return (Inventory) entity;
        }
        if (entity instanceof HorseBaseEntity) {
            return ((HorseBaseEntityAccessor) entity).shadowlink$getItems();
        }
        return null;
    }

    /**
     * NBT form of a stack as it looks after one save/load cycle. Loading is not an exact
     * inverse of saving (e.g. damageable items gain {@code Damage:0}), so both sides of every
     * comparison go through the same round trip. Empty stacks are an empty compound.
     */
    static CompoundTag snapshotOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return EMPTY_SNAPSHOT;
        }
        ItemStack reloaded = ItemStack.fromTag(stack.toTag(new CompoundTag()));
        if (reloaded.isEmpty()) {
            return EMPTY_SNAPSHOT;
        }
        return reloaded.toTag(new CompoundTag());
    }

    static ItemStack fromSnapshot(CompoundTag snapshot) {
        // fromNbt may post-process the compound in place, hence the copy.
        return snapshot.isEmpty() ? ItemStack.EMPTY : ItemStack.fromTag(snapshot.copy());
    }
}
