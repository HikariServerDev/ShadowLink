package dev.atsukimc.shadowlink.core;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.PersistentState;

//? if >=1.16 {
final class ShadowState extends PersistentState {
//?} else {
/*public final class ShadowState extends PersistentState {
*///?}
    static final String ID = "shadowlink";

    private final ShadowManager manager;

    //? if >=1.18 {
    ShadowState(ShadowManager manager) {
        this.manager = manager;
    //?} else if >=1.16.5 && <1.18 {
    /*ShadowState(ShadowManager manager) {
        super(ID);
        this.manager = manager;
    }

    @Override
    public void fromTag(NbtCompound nbt) {
        this.manager.readNbtSafely(nbt);
    *///?} else if >=1.16 && <1.16.5 {
    /*ShadowState(ShadowManager manager) {
        super(ID);
        this.manager = manager;
    *///?} else {
    /*public ShadowState(String id) {
        super(id);
        this.manager = ShadowManager.stateOwner;
    *///?}
    }

    @Override
    //? if >=1.16.5 {
    public NbtCompound writeNbt(NbtCompound nbt) {
    //?} else if >=1.16 && <1.16.5 {
    /*public void fromTag(NbtCompound nbt) {
        this.manager.readNbtSafely(nbt);
    }

    @Override
    public NbtCompound toTag(NbtCompound nbt) {
    *///?} else {
    /*public void fromNbt(NbtCompound nbt) {
        this.manager.readNbtSafely(nbt);
    }

    @Override
    public NbtCompound toNbt(NbtCompound nbt) {
    *///?}
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
