package dev.atsukimc.shadowlink.core;

import java.util.Objects;

/** One slot of one holder. */
public final class Endpoint {
    private final HolderKey holder;
    private final int slot;

    public Endpoint(HolderKey holder, int slot) {
        this.holder = holder;
        this.slot = slot;
    }

    public HolderKey holder() {
        return this.holder;
    }

    public int slot() {
        return this.slot;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Endpoint)) {
            return false;
        }
        Endpoint that = (Endpoint) other;
        return this.slot == that.slot && this.holder.equals(that.holder);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.holder, this.slot);
    }

    @Override
    public String toString() {
        return "Endpoint[" + this.holder + "#" + this.slot + "]";
    }
}
