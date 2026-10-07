package dev.atsukimc.shadowlink.core;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.PersistentState;

/** Vanilla save-data carrier: {@code <world>/data/shadowlink.dat}, written with every world save. */
final class ShadowState extends PersistentState {
    static final String ID = "shadowlink";

    private final ShadowManager manager;

    ShadowState(ShadowManager manager) {
        super(ID);
        this.manager = manager;
    }

    @Override
    public void fromTag(NbtCompound nbt) {
        this.manager.readNbtSafely(nbt);
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        return this.manager.writeNbt(nbt);
    }

    /**
     * The saved snapshot has to describe the canonical stacks as they are at save time, and
     * those change without notifying anyone, so the state is rewritten on every save while
     * at least one group exists.
     */
    @Override
    public boolean isDirty() {
        return super.isDirty() || this.manager.hasGroups();
    }
}
