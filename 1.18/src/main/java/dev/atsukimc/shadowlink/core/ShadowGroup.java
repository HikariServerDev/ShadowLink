package dev.atsukimc.shadowlink.core;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** A set of slots that share (or must share again once loaded) one ItemStack instance. */
final class ShadowGroup {
    /** Per-endpoint runtime state. */
    static final class Link {
        /**
         * {@code null}: ACTIVE, the slot was last confirmed to hold the canonical instance.
         * <p>
         * non-null: UNAVAILABLE, the holder was unloaded while it held the canonical instance.
         * The value is the stack exactly as it was serialized at that moment; a reloaded slot
         * is only overwritten if it still matches it.
         */
        NbtCompound awaiting;
        /** Tick at which a reloaded slot first failed the content check, or -1. */
        int mismatchSince = -1;

        Link(NbtCompound awaiting) {
            this.awaiting = awaiting;
        }

        boolean active() {
            return this.awaiting == null;
        }
    }

    final UUID id;
    /**
     * The one live instance every active endpoint references. {@code null} only between a
     * server start and the first endpoint of this group becoming available again.
     */
    ItemStack canonical;
    /** State of the canonical stack at the last save; the authority while {@link #canonical} is null. */
    NbtCompound snapshot;
    final Map<Endpoint, Link> links = new LinkedHashMap<>();
    /** Consecutive ticks with every endpoint loaded but fewer than two holding the instance. */
    int lonelyTicks;
    /** An endpoint joined since the last validation pass, i.e. the instance was seen moving. */
    boolean gained;
    /** A restore inconsistency was already reported (and the group no longer guesses). */
    boolean inconsistent;

    ShadowGroup(UUID id, ItemStack canonical, NbtCompound snapshot) {
        this.id = id;
        this.canonical = canonical;
        this.snapshot = snapshot;
    }
}
