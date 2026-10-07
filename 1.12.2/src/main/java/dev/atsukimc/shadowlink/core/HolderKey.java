package dev.atsukimc.shadowlink.core;

import net.minecraft.util.math.BlockPos;

import java.util.Objects;
import java.util.UUID;

/**
 * Stable, serializable identity of something that owns item slots. It never holds a Java
 * reference to the owner, so it stays valid while the owner is unloaded.
 */
public final class HolderKey {
    private final Kind kind;
    private final UUID uuid;
    private final Integer dimension;
    private final BlockPos pos;

    public HolderKey(Kind kind, UUID uuid, Integer dimension, BlockPos pos) {
        this.kind = kind;
        this.uuid = uuid;
        this.dimension = dimension;
        this.pos = pos;
    }

    public Kind kind() {
        return this.kind;
    }

    public UUID uuid() {
        return this.uuid;
    }

    public Integer dimension() {
        return this.dimension;
    }

    public BlockPos pos() {
        return this.pos;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof HolderKey)) {
            return false;
        }
        HolderKey that = (HolderKey) other;
        return this.kind == that.kind && Objects.equals(this.uuid, that.uuid)
                && Objects.equals(this.dimension, that.dimension) && Objects.equals(this.pos, that.pos);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.kind, this.uuid, this.dimension, this.pos);
    }

    @Override
    public String toString() {
        return this.kind + "[" + (this.uuid != null ? this.uuid : "") + (this.dimension != null ? this.dimension : "")
                + (this.pos != null ? " " + this.pos.getX() + "," + this.pos.getY() + "," + this.pos.getZ() : "") + "]";
    }

    public enum Kind {
        /** PlayerInventory: 36 main + 4 armor + 1 offhand slots. Covers Carpet fake players. */
        PLAYER("player"),
        ENDER_CHEST("ender_chest"),
        /** The stack carried on the mouse cursor. Runtime only: it is never unloaded or saved. */
        CURSOR("cursor"),
        /** Any BlockEntity implementing Inventory. Double chests are split into their halves. */
        BLOCK("block"),
        /** Storage minecarts and the horse family. */
        ENTITY("entity");

        public final String id;

        Kind(String id) {
            this.id = id;
        }

        public static Kind byId(String id) {
            for (Kind kind : values()) {
                if (kind.id.equals(id)) {
                    return kind;
                }
            }
            return null;
        }
    }

    public static HolderKey player(UUID uuid) {
        return new HolderKey(Kind.PLAYER, uuid, null, null);
    }

    public static HolderKey enderChest(UUID uuid) {
        return new HolderKey(Kind.ENDER_CHEST, uuid, null, null);
    }

    public static HolderKey cursor(UUID uuid) {
        return new HolderKey(Kind.CURSOR, uuid, null, null);
    }

    public static HolderKey block(Integer dimension, BlockPos pos) {
        return new HolderKey(Kind.BLOCK, null, dimension, pos.toImmutable());
    }

    public static HolderKey entity(UUID uuid, Integer dimension) {
        return new HolderKey(Kind.ENTITY, uuid, dimension, null);
    }
}
