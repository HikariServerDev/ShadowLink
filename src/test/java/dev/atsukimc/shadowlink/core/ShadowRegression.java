package dev.atsukimc.shadowlink.core;

import net.minecraft.Bootstrap;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.UUID;

/** Offline core checks using real Minecraft ItemStack/NBT, without a server or mixins. */
public final class ShadowRegression {
    private static int checks;

    public static void main(String[] args) throws Exception {
        try {
            Class.forName("net.minecraft.SharedConstants").getMethod("createGameVersion").invoke(null);
        } catch (ClassNotFoundException | NoSuchMethodException ignored) {
            // Older Minecraft versions do not require a separately initialized version.
        }
        Bootstrap.initialize();
        identityDetection();
        sameContentIsNotIdentity();
        replacementIsNotReload();
        reloadUsesCanonical();
        consumedOfflineStack();
        mismatchIsLeftAlone();
        invalidSlotIsLeftAlone();
        duplicateIds();
        malformedData();
        System.out.println("ShadowLink regression checks passed: " + checks);
    }

    private static ShadowManager manager() throws Exception {
        Constructor<?> constructor = ShadowManager.class.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        return (ShadowManager) constructor.newInstance(new Object[] {null});
    }

    private static Object call(ShadowManager manager, String name, Object... args) throws Exception {
        for (Method method : ShadowManager.class.getDeclaredMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == args.length) {
                method.setAccessible(true);
                try {
                    return method.invoke(manager, args);
                } catch (InvocationTargetException error) {
                    throw new AssertionError(name, error.getCause());
                }
            }
        }
        throw new NoSuchMethodException(name);
    }

    @SuppressWarnings("unchecked")
    private static <K, V> Map<K, V> map(ShadowManager manager, String name) throws Exception {
        Field field = ShadowManager.class.getDeclaredField(name);
        field.setAccessible(true);
        return (Map<K, V>) field.get(manager);
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static ItemStack stack(int count) {
        NbtCompound nbt = new NbtCompound();
        nbt.putString("id", "minecraft:stone");
        nbt.putByte("Count", (byte) count);
        return InventoryAccess.fromSnapshot(nbt);
    }

    private static Endpoint endpoint(int slot) {
        return new Endpoint(HolderKey.player(UUID.randomUUID()), slot);
    }

    private static Inventory inventory(ItemStack[] slots) {
        return (Inventory) Proxy.newProxyInstance(Inventory.class.getClassLoader(), new Class<?>[] {Inventory.class}, (proxy, method, args) -> {
            switch (method.getName()) {
                case "size": case "getInvSize": return slots.length;
                case "getStack": case "getInvStack": return slots[(Integer) args[0]];
                case "setStack": case "setInvStack": slots[(Integer) args[0]] = (ItemStack) args[1]; return null;
                case "markDirty": return null;
                default: throw new AssertionError("Unexpected inventory side effect: " + method.getName());
            }
        });
    }

    private static ShadowGroup group(ShadowManager manager, ItemStack canonical, Endpoint endpoint, NbtCompound awaiting) throws Exception {
        ShadowGroup group = new ShadowGroup(UUID.randomUUID(), canonical, InventoryAccess.snapshotOf(canonical));
        ShadowRegression.<UUID, ShadowGroup>map(manager, "groups").put(group.id, group);
        if (canonical != null) ShadowRegression.<ItemStack, ShadowGroup>map(manager, "byCanonical").put(canonical, group);
        call(manager, "link", group, endpoint, awaiting);
        return group;
    }

    private static void identityDetection() throws Exception {
        ShadowManager manager = manager();
        ItemStack stack = stack(12);
        call(manager, "observe", endpoint(0), stack);
        call(manager, "observe", endpoint(1), stack);
        check(map(manager, "groups").size() == 1 && map(manager, "byEndpoint").size() == 2, "Shared references must be detected");
    }

    private static void sameContentIsNotIdentity() throws Exception {
        ShadowManager manager = manager();
        call(manager, "observe", endpoint(0), stack(12));
        call(manager, "observe", endpoint(1), stack(12));
        call(manager, "observe", endpoint(2), ItemStack.EMPTY);
        call(manager, "observe", endpoint(3), ItemStack.EMPTY);
        check(map(manager, "groups").isEmpty(), "Equal-content copies and empty slots must not create shadows");
    }

    private static void replacementIsNotReload() throws Exception {
        for (int replacementCount : new int[] {12, 24}) {
            ShadowManager manager = manager();
            Endpoint endpoint = endpoint(0);
            ShadowGroup group = group(manager, stack(12), endpoint, null);
            ItemStack replacement = stack(replacementCount);
            ItemStack[] slots = {replacement};
            call(manager, "validateLoadedEndpoint", group, endpoint, inventory(slots));
            check(slots[0] == replacement && !map(manager, "byEndpoint").containsKey(endpoint), "Loaded replacement must stay detached, even with equal or old reload content");
        }
        ShadowManager manager = manager();
        Endpoint endpoint = endpoint(0);
        ShadowGroup group = group(manager, stack(12), endpoint, null);
        call(manager, "validateLoadedEndpoint", group, endpoint, inventory(new ItemStack[] {group.canonical}));
        check(map(manager, "byEndpoint").containsKey(endpoint), "Unchanged reference must remain linked");
    }

    private static void reloadUsesCanonical() throws Exception {
        for (int savedCount : new int[] {12, 24}) {
            ShadowManager manager = manager();
            Endpoint endpoint = endpoint(0);
            ItemStack saved = stack(savedCount);
            ShadowGroup group = group(manager, stack(12), endpoint, InventoryAccess.snapshotOf(saved));
            ItemStack[] slots = {saved};
            call(manager, "restore", group, endpoint, group.links.get(endpoint), inventory(slots));
            check(slots[0] == group.canonical && group.links.get(endpoint).active(), "Recorded reload must restore the latest canonical reference");
        }
        ShadowManager manager = manager();
        Endpoint endpoint = endpoint(0);
        ShadowGroup group = group(manager, null, endpoint, InventoryAccess.snapshotOf(stack(24)));
        group.snapshot = InventoryAccess.snapshotOf(stack(12));
        ItemStack[] slots = {stack(24)};
        call(manager, "restore", group, endpoint, group.links.get(endpoint), inventory(slots));
        check(slots[0] == group.canonical && group.canonical.getCount() == 12, "Restart must use saved canonical authority instead of stale endpoint data");
    }

    private static void consumedOfflineStack() throws Exception {
        ShadowManager manager = manager();
        Endpoint endpoint = endpoint(0);
        ShadowGroup group = group(manager, ItemStack.EMPTY, endpoint, InventoryAccess.snapshotOf(stack(24)));
        ItemStack[] slots = {stack(24)};
        call(manager, "restore", group, endpoint, group.links.get(endpoint), inventory(slots));
        check(slots[0].isEmpty() && !map(manager, "byEndpoint").containsKey(endpoint), "Consumed canonical must empty the recorded offline copy");
    }

    private static void mismatchIsLeftAlone() throws Exception {
        ShadowManager manager = manager();
        Endpoint endpoint = endpoint(0);
        ShadowGroup group = group(manager, stack(12), endpoint, InventoryAccess.snapshotOf(stack(24)));
        ItemStack unexpected = stack(48);
        ItemStack[] slots = {unexpected};
        ShadowGroup.Link link = group.links.get(endpoint);
        call(manager, "restore", group, endpoint, link, inventory(slots));
        check(slots[0] == unexpected && !link.active(), "Mismatched reload must stay untouched during settling");
        Field ticks = ShadowManager.class.getDeclaredField("ticks");
        ticks.setAccessible(true);
        ticks.setInt(manager, 100);
        call(manager, "restore", group, endpoint, link, inventory(slots));
        check(slots[0] == unexpected && !map(manager, "byEndpoint").containsKey(endpoint), "Persistent mismatch must be unlinked without overwriting items");
    }

    private static void invalidSlotIsLeftAlone() throws Exception {
        ShadowManager manager = manager();
        Endpoint endpoint = endpoint(10);
        ShadowGroup group = group(manager, stack(12), endpoint, InventoryAccess.snapshotOf(stack(24)));
        ItemStack original = stack(24);
        ItemStack[] slots = {original};
        call(manager, "restore", group, endpoint, group.links.get(endpoint), inventory(slots));
        check(slots[0] == original && !map(manager, "byEndpoint").containsKey(endpoint), "Out-of-range reload must not write inventory");
    }

    private static NbtCompound savedGroup(UUID id) {
        NbtCompound group = new NbtCompound();
        group.putUuid("id", id);
        group.put("stack", InventoryAccess.snapshotOf(stack(12)));
        NbtCompound endpoint = new NbtCompound();
        endpoint.putString("kind", "player");
        endpoint.putUuid("uuid", UUID.randomUUID());
        endpoint.putInt("slot", 0);
        NbtList endpoints = new NbtList();
        endpoints.add(endpoint);
        group.put("endpoints", endpoints);
        return group;
    }

    private static ShadowManager load(NbtCompound... groups) throws Exception {
        ShadowManager manager = manager();
        NbtCompound root = new NbtCompound();
        root.putInt("version", 1);
        NbtList list = new NbtList();
        for (NbtCompound group : groups) list.add(group);
        root.put("groups", list);
        call(manager, "readNbt", root);
        return manager;
    }

    private static void duplicateIds() throws Exception {
        UUID id = UUID.randomUUID();
        ShadowManager manager = load(savedGroup(id), savedGroup(id));
        Map<UUID, ShadowGroup> groups = map(manager, "groups");
        Map<Endpoint, ShadowGroup> endpoints = map(manager, "byEndpoint");
        check(groups.size() == 1 && endpoints.size() == 1, "Duplicate group IDs must not create orphan endpoint indexes");
        for (ShadowGroup group : endpoints.values()) check(groups.get(group.id) == group, "Every indexed endpoint must belong to its registered group object");
    }

    private static void malformedData() throws Exception {
        NbtCompound group = savedGroup(UUID.randomUUID());
        group.remove("stack");
        check(map(load(group), "groups").isEmpty(), "Missing canonical snapshot must not become an empty authority");
        group = savedGroup(UUID.randomUUID());
        group.getCompound("stack").putString("id", "shadowlink:nonexistent_item");
        check(map(load(group), "groups").isEmpty(), "Unknown item must not become an empty authority");
        group = savedGroup(UUID.randomUUID());
        group.put("stack", new NbtCompound());
        check(map(load(group), "groups").size() == 1, "Explicit empty snapshot is valid consumed-item authority");
        for (int invalid : new int[] {-1, -2}) {
            group = savedGroup(UUID.randomUUID());
            NbtCompound endpoint = group.getList("endpoints", 10).getCompound(0);
            if (invalid == -1) endpoint.remove("slot"); else endpoint.putInt("slot", -2);
            check(map(load(group), "byEndpoint").isEmpty(), "Missing or negative slot must not default to slot zero");
        }
        group = savedGroup(UUID.randomUUID());
        group.getList("endpoints", 10).getCompound(0).putString("stack", "invalid");
        check(map(load(group), "byEndpoint").isEmpty(), "Wrong snapshot type must not silently use canonical data");
    }
}
