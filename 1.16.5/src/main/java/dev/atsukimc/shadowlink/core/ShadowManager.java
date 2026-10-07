package dev.atsukimc.shadowlink.core;

import dev.atsukimc.shadowlink.ShadowLink;
import dev.atsukimc.shadowlink.mixin.DoubleInventoryAccessor;
import dev.atsukimc.shadowlink.mixin.HorseScreenHandlerAccessor;
import dev.atsukimc.shadowlink.mixin.SlotAccessor;
import dev.atsukimc.shadowlink.mixin.WorldChunkAccessor;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.DoubleInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.screen.HorseScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Tracks item shadows (one ItemStack instance referenced from several slots) for one server.
 *
 * <h2>Model</h2>
 * A {@link ShadowGroup} is one canonical ItemStack instance plus the slots (endpoints) that
 * hold it. Membership is decided by reference identity ({@code ==}) only, never by content.
 * Nothing is ever written into the ItemStack itself.
 *
 * <h2>Endpoint lifecycle</h2>
 * <ul>
 * <li><b>ACTIVE</b>: holder loaded, slot confirmed to reference the canonical instance.</li>
 * <li><b>UNAVAILABLE</b>: holder was unloaded (logout, chunk unload, shutdown) while ACTIVE.
 * The link is kept, together with the stack as it was serialized.</li>
 * <li>When the holder comes back, the freshly deserialized copy in the slot is replaced by
 * the canonical instance itself (never a copy of it) and the endpoint is ACTIVE again.</li>
 * </ul>
 * An endpoint whose holder is <em>loaded</em> and whose slot no longer references the
 * canonical instance is simply removed: that is a real in-game unshadowing, not a
 * serialization artefact, and it must not come back. A group with no unavailable endpoint
 * and fewer than two active ones is dissolved.
 *
 * <h2>Authority</h2>
 * The live canonical instance is always right, including every change made to it while
 * other endpoints were unloaded. After a restart there is no live instance, so the saved
 * snapshot of the canonical stack takes that role. A reloaded slot is only ever overwritten
 * if its content is exactly what was serialized when it became unavailable; otherwise the
 * endpoint is unlinked and left untouched (fail closed).
 *
 * All methods run on the server thread; hooks arriving from any other thread are ignored
 * and covered by the periodic polling in {@link #endTick()}.
 */
public final class ShadowManager {
    private static final int FORMAT_VERSION = 1;
    /**
     * How long a group may sit with a single holder before it is dissolved. Covers the few
     * ticks a container is in transit with Carpet's movable block entities.
     */
    private static final int LONELY_GRACE_TICKS = 10;
    private static final int PLAYER_SCAN_INTERVAL = 20;
    private static final int PENDING_POLL_INTERVAL = 10;
    /**
     * How long a reloaded slot may disagree with its saved state before the endpoint is given
     * up. Other mods (e.g. checkpoint restore of fake players) rewrite an inventory shortly
     * after the holder was loaded; the final content is what has to be judged.
     */
    private static final int RELOAD_SETTLE_TICKS = 100;
    /**
     * How long after a reload a slot may be overwritten once more with its saved data and
     * still be re-linked. Player data kept in an external database is applied on top of the
     * player file some time after the login, depending on how fast that database answers.
     */
    private static final int REWRITE_WINDOW_TICKS = 600;
    private static final int MAX_ERROR_LOGS = 10;

    private static volatile ShadowManager instance;
    private static int errorLogs;

    private static final class ChunkRef {
        private final RegistryKey<World> dimension;
        private final long pos;

        ChunkRef(RegistryKey<World> dimension, long pos) {
            this.dimension = dimension;
            this.pos = pos;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof ChunkRef)) {
                return false;
            }
            ChunkRef that = (ChunkRef) other;
            return this.pos == that.pos && this.dimension.equals(that.dimension);
        }

        @Override
        public int hashCode() {
            return this.dimension.hashCode() * 31 + Long.hashCode(this.pos);
        }
    }

    // Raw NBT type ids (NbtElement constants only exist from 1.17 on).
    private static final int TAG_COMPOUND = 10;
    private static final int TAG_STRING = 8;
    private static final int TAG_LONG = 4;

    private final MinecraftServer server;
    private ShadowState state;

    private final Map<UUID, ShadowGroup> groups = new LinkedHashMap<>();
    private final IdentityHashMap<ItemStack, ShadowGroup> byCanonical = new IdentityHashMap<>();
    private final Map<Endpoint, ShadowGroup> byEndpoint = new HashMap<>();
    private final Map<HolderKey, Set<Endpoint>> byHolder = new HashMap<>();
    private final Map<ChunkRef, Set<HolderKey>> blockHoldersByChunk = new HashMap<>();

    /** Scratch map of one scan pass: first slot each stack instance was seen in. */
    private final IdentityHashMap<ItemStack, Endpoint> seen = new IdentityHashMap<>();
    /** Screen handlers clicked since the last flush, with the clicking player. */
    private final IdentityHashMap<ScreenHandler, ServerPlayerEntity> clicked = new IdentityHashMap<>();
    private final Set<BlockEntity> placed = Collections.newSetFromMap(new IdentityHashMap<>());

    private int ticks;
    /** Set by {@link #locate}: the holder's surroundings are loaded but the holder is not there. */
    private boolean holderGone;
    /** Outputs of {@link #resolveSlot}. */
    private HolderKey slotHolder;
    private Inventory slotInventory;
    private int slotIndex;

    private ShadowManager(MinecraftServer server) {
        this.server = server;
    }

    // ------------------------------------------------------------------------------------
    // Entry points called from mixins
    // ------------------------------------------------------------------------------------

    private static void run(MinecraftServer server, Consumer<ShadowManager> action) {
        if (server == null || !server.isOnThread()) {
            return;
        }
        try {
            ShadowManager manager = instance;
            if (manager == null || manager.server != server) {
                instance = manager = new ShadowManager(server);
            }
            if (manager.ensureLoaded()) {
                action.accept(manager);
            }
        } catch (Exception e) {
            if (errorLogs++ < MAX_ERROR_LOGS) {
                ShadowLink.LOGGER.error("Unexpected error in shadow bookkeeping", e);
            }
        }
    }

    public static void onEndTick(MinecraftServer server) {
        run(server, ShadowManager::endTick);
    }

    public static void onServerStopped(MinecraftServer server) {
        ShadowManager manager = instance;
        if (manager != null && manager.server == server) {
            instance = null;
        }
    }

    public static void onSlotClick(ScreenHandler handler, ServerPlayerEntity player) {
        run(player.server, manager -> {
            // Inspect the result of earlier clicks before this one changes things again.
            manager.flush();
            manager.clicked.put(handler, player);
        });
    }

    public static void onPlayerJoined(ServerPlayerEntity player) {
        run(player.server, manager -> {
            UUID uuid = player.getUuid();
            manager.resolvePending(HolderKey.player(uuid), player.inventory);
            manager.resolvePending(HolderKey.enderChest(uuid), player.getEnderChestInventory());
        });
    }

    public static void onPlayerLeaving(ServerPlayerEntity player) {
        run(player.server, manager -> manager.playerLeaving(player));
    }

    public static void onChunkLoaded(ServerWorld world, WorldChunk chunk) {
        run(world.getServer(), manager -> manager.chunkLoaded(world, chunk));
    }

    public static void onChunkUnloading(ServerWorld world, WorldChunk chunk) {
        run(world.getServer(), manager -> manager.chunkUnloading(world, chunk));
    }

    public static void onBlockEntityPlaced(ServerWorld world, BlockEntity blockEntity) {
        ShadowManager manager = instance;
        if (manager != null && manager.server == world.getServer() && !manager.groups.isEmpty() && manager.server.isOnThread()) {
            manager.placed.add(blockEntity);
        }
    }

    public static void onEntityLoaded(Entity entity) {
        ShadowManager current = instance;
        if (current == null || current.groups.isEmpty() || entity.world.isClient) {
            return;
        }
        run(entity.getServer(), manager -> {
            HolderKey key = HolderKey.entity(entity.getUuid(), entity.world.getRegistryKey());
            Inventory inventory = InventoryAccess.inventoryOf(entity);
            if (inventory != null && manager.byHolder.containsKey(key)) {
                manager.resolvePending(key, inventory);
            }
        });
    }

    public static void onEntityUnloading(Entity entity) {
        ShadowManager current = instance;
        if (current == null || (current.groups.isEmpty() && current.clicked.isEmpty()) || entity.world.isClient) {
            return;
        }
        run(entity.getServer(), manager -> {
            manager.flush();
            HolderKey key = HolderKey.entity(entity.getUuid(), entity.world.getRegistryKey());
            if (!manager.byHolder.containsKey(key)) {
                return;
            }
            Inventory inventory = InventoryAccess.inventoryOf(entity);
            // Killed, discarded or sent through a portal: the entity object is gone for good
            // and flagged as removed. Only a plain chunk unload is a temporary absence.
            if (inventory != null && !entity.removed) {
                manager.holderUnloading(key, inventory, true);
            } else {
                manager.dropHolder(key, false);
            }
        });
    }

    // ------------------------------------------------------------------------------------
    // Index maintenance
    // ------------------------------------------------------------------------------------

    boolean hasGroups() {
        return !this.groups.isEmpty();
    }

    private void markDirty() {
        if (this.state != null) {
            this.state.markDirty();
        }
    }

    private static ChunkRef chunkRef(HolderKey blockHolder) {
        BlockPos pos = blockHolder.pos();
        return new ChunkRef(blockHolder.dimension(), ChunkPos.toLong(pos.getX() >> 4, pos.getZ() >> 4));
    }

    /** Adds the endpoint to the group (moving it out of any other group) and sets its state. */
    private void link(ShadowGroup group, Endpoint endpoint, NbtCompound awaiting) {
        ShadowGroup previous = this.byEndpoint.get(endpoint);
        if (previous == group) {
            group.links.get(endpoint).awaiting = awaiting;
            return;
        }
        if (previous != null) {
            this.unlink(previous, endpoint);
        }
        group.links.put(endpoint, new ShadowGroup.Link(awaiting));
        group.gained = true;
        this.byEndpoint.put(endpoint, group);
        HolderKey holder = endpoint.holder();
        this.byHolder.computeIfAbsent(holder, key -> new LinkedHashSet<>()).add(endpoint);
        if (holder.kind() == HolderKey.Kind.BLOCK) {
            this.blockHoldersByChunk.computeIfAbsent(chunkRef(holder), key -> new HashSet<>()).add(holder);
        }
        this.markDirty();
    }

    private void unlink(ShadowGroup group, Endpoint endpoint) {
        if (group.links.remove(endpoint) == null) {
            return;
        }
        this.byEndpoint.remove(endpoint, group);
        HolderKey holder = endpoint.holder();
        Set<Endpoint> ofHolder = this.byHolder.get(holder);
        if (ofHolder != null && ofHolder.remove(endpoint) && ofHolder.isEmpty()) {
            this.byHolder.remove(holder);
            if (holder.kind() == HolderKey.Kind.BLOCK) {
                ChunkRef ref = chunkRef(holder);
                Set<HolderKey> inChunk = this.blockHoldersByChunk.get(ref);
                if (inChunk != null && inChunk.remove(holder) && inChunk.isEmpty()) {
                    this.blockHoldersByChunk.remove(ref);
                }
            }
        }
        this.markDirty();
    }

    private void dissolve(ShadowGroup group) {
        for (Endpoint endpoint : new ArrayList<>(group.links.keySet())) {
            this.unlink(group, endpoint);
        }
        this.groups.remove(group.id);
        if (group.canonical != null) {
            this.byCanonical.remove(group.canonical, group);
        }
        this.markDirty();
        ShadowLink.LOGGER.debug("Shadow group {} dissolved", group.id);
    }

    /** Removes the endpoints of a holder that no longer exists. */
    private void dropHolder(HolderKey holder, boolean onlyUnavailable) {
        Set<Endpoint> endpoints = this.byHolder.get(holder);
        if (endpoints == null) {
            return;
        }
        for (Endpoint endpoint : new ArrayList<>(endpoints)) {
            ShadowGroup group = this.byEndpoint.get(endpoint);
            if (!onlyUnavailable || !group.links.get(endpoint).active()) {
                this.unlink(group, endpoint);
            }
        }
    }

    // ------------------------------------------------------------------------------------
    // Locating holders
    // ------------------------------------------------------------------------------------

    private static Inventory inventoryAt(WorldChunk chunk, BlockPos pos) {
        BlockEntity blockEntity = chunk.getBlockEntity(pos);
        return blockEntity instanceof Inventory && !blockEntity.isRemoved() ? (Inventory) blockEntity : null;
    }

    /**
     * Returns the holder's slots if it is loaded right now. Never loads chunks and never
     * creates block entities. On {@code null}, {@link #holderGone} tells "does not exist
     * any more" apart from "currently unloaded".
     */
    private Inventory locate(HolderKey key) {
        this.holderGone = false;
        switch (key.kind()) {
            case PLAYER:
            case ENDER_CHEST:
            case CURSOR: {
                PlayerManager players = this.server.getPlayerManager();
                ServerPlayerEntity player = players == null ? null : players.getPlayer(key.uuid());
                if (player == null) {
                    this.holderGone = key.kind() == HolderKey.Kind.CURSOR;
                    return null;
                }
                if (key.kind() == HolderKey.Kind.PLAYER) {
                    return player.inventory;
                }
                return key.kind() == HolderKey.Kind.ENDER_CHEST ? player.getEnderChestInventory() : new CursorView(player);
            }
            case BLOCK: {
                ServerWorld world = this.server.getWorld(key.dimension());
                if (world == null) {
                    return null;
                }
                BlockPos pos = key.pos();
                WorldChunk chunk = world.getChunkManager().getWorldChunk(pos.getX() >> 4, pos.getZ() >> 4);
                if (chunk == null || !((WorldChunkAccessor) chunk).shadowlink$isLoadedToWorld()) {
                    return null;
                }
                Inventory inventory = inventoryAt(chunk, pos);
                this.holderGone = inventory == null;
                return inventory;
            }
            case ENTITY: {
                ServerWorld world = this.server.getWorld(key.dimension());
                Entity entity = world == null ? null : world.getEntity(key.uuid());
                if (entity == null || entity.removed) {
                    return null;
                }
                Inventory inventory = InventoryAccess.inventoryOf(entity);
                this.holderGone = inventory == null;
                return inventory;
            }
            default:
                return null;
        }
    }

    /**
     * Maps a screen handler slot to the endpoint that really owns it. Slots of transient
     * inventories (crafting grid, anvil, merchant, ...) are not endpoints.
     */
    private boolean resolveSlot(Inventory inventory, int slot, ScreenHandler handler, ServerPlayerEntity player) {
        if (inventory instanceof DoubleInventory) {
            DoubleInventoryAccessor halves = (DoubleInventoryAccessor) inventory;
            Inventory first = halves.shadowlink$getFirst();
            if (slot < first.size()) {
                inventory = first;
            } else {
                slot -= first.size();
                inventory = halves.shadowlink$getSecond();
            }
        }
        HolderKey key;
        if (inventory instanceof PlayerInventory) {
            PlayerInventory playerInventory = (PlayerInventory) inventory;
            if (!(playerInventory.player instanceof ServerPlayerEntity)) {
                return false;
            }
            key = HolderKey.player(playerInventory.player.getUuid());
        } else if (inventory instanceof BlockEntity) {
            BlockEntity blockEntity = (BlockEntity) inventory;
            if (!(blockEntity.getWorld() instanceof ServerWorld) || blockEntity.isRemoved()) {
                return false;
            }
            key = HolderKey.block(blockEntity.getWorld().getRegistryKey(), blockEntity.getPos());
        } else if (inventory instanceof Entity) {
            Entity entity = (Entity) inventory;
            if (entity.world.isClient || entity.removed) {
                return false;
            }
            key = HolderKey.entity(entity.getUuid(), entity.world.getRegistryKey());
        } else if (inventory == player.getEnderChestInventory()) {
            key = HolderKey.enderChest(player.getUuid());
        } else if (handler instanceof HorseScreenHandler && ((HorseScreenHandlerAccessor) handler).shadowlink$getInventory() == inventory) {
            Entity horse = ((HorseScreenHandlerAccessor) handler).shadowlink$getEntity();
            if (horse == null || horse.world.isClient || horse.removed) {
                return false;
            }
            key = HolderKey.entity(horse.getUuid(), horse.world.getRegistryKey());
        } else {
            return false;
        }
        this.slotHolder = key;
        this.slotInventory = inventory;
        this.slotIndex = slot;
        return true;
    }

    // ------------------------------------------------------------------------------------
    // Detection
    // ------------------------------------------------------------------------------------

    /**
     * Records that {@code stack} currently sits in {@code endpoint}. A stack that is a known
     * canonical instance (re)joins its group; an instance met in two different slots during
     * one scan pass is a newly formed shadow.
     */
    private void observe(Endpoint endpoint, ItemStack stack) {
        // Also filters the shared ItemStack.EMPTY instance that fills every empty slot.
        if (stack == null || stack.isEmpty()) {
            return;
        }
        ShadowGroup group = this.byCanonical.get(stack);
        if (group != null) {
            this.link(group, endpoint, null);
            return;
        }
        Endpoint first = this.seen.putIfAbsent(stack, endpoint);
        if (first == null || first.equals(endpoint)) {
            return;
        }
        group = new ShadowGroup(UUID.randomUUID(), stack, null);
        this.groups.put(group.id, group);
        this.byCanonical.put(stack, group);
        this.link(group, first, null);
        this.link(group, endpoint, null);
        this.seen.remove(stack);
        ShadowLink.LOGGER.debug("Shadow group {} detected: {} <-> {}", group.id, first, endpoint);
    }

    private void scanInventory(HolderKey holder, Inventory inventory) {
        int size = InventoryAccess.size(inventory);
        for (int slot = 0; slot < size; slot++) {
            ItemStack stack = InventoryAccess.get(inventory, slot);
            if (!stack.isEmpty()) {
                this.observe(new Endpoint(holder, slot), stack);
            }
        }
    }

    private void scanHandler(ScreenHandler handler, ServerPlayerEntity player) {
        PlayerInventory own = player.inventory;
        for (Slot slot : handler.slots) {
            if (slot.inventory == own || !this.resolveSlot(slot.inventory, ((SlotAccessor) slot).shadowlink$getIndex(), handler, player)) {
                continue;
            }
            ItemStack stack = InventoryAccess.get(this.slotInventory, this.slotIndex);
            if (!stack.isEmpty()) {
                this.observe(new Endpoint(this.slotHolder, this.slotIndex), stack);
            }
        }
    }

    /** Everything a player can move stack references between with inventory clicks. */
    private void scanPlayer(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        this.scanInventory(HolderKey.player(uuid), player.inventory);
        this.scanInventory(HolderKey.enderChest(uuid), player.getEnderChestInventory());
        ScreenHandler handler = player.currentScreenHandler;
        if (handler != null) {
            this.observe(new Endpoint(HolderKey.cursor(uuid), 0), player.inventory.getCursorStack());
            if (handler != player.playerScreenHandler) {
                this.scanHandler(handler, player);
            }
        }
    }

    /**
     * Inspects everything queued by hooks since the last call. Vanilla only ever moves or
     * duplicates a stack <em>reference</em> between inventories through screen handler
     * clicks (all other transfers split or copy), so looking at the clicked handlers and
     * their players is enough to catch both new shadows and canonical stacks changing slot.
     */
    private void flush() {
        if (this.clicked.isEmpty() && this.placed.isEmpty()) {
            return;
        }
        this.seen.clear();
        if (!this.clicked.isEmpty()) {
            List<Map.Entry<ScreenHandler, ServerPlayerEntity>> clicks = new ArrayList<>(this.clicked.entrySet());
            this.clicked.clear();
            for (Map.Entry<ScreenHandler, ServerPlayerEntity> click : clicks) {
                ServerPlayerEntity player = click.getValue();
                this.scanPlayer(player);
                if (click.getKey() != player.currentScreenHandler && click.getKey() != player.playerScreenHandler) {
                    this.scanHandler(click.getKey(), player);
                }
            }
        }
        if (!this.placed.isEmpty()) {
            List<BlockEntity> blockEntities = new ArrayList<>(this.placed);
            this.placed.clear();
            if (!this.groups.isEmpty()) {
                for (BlockEntity blockEntity : blockEntities) {
                    if (!blockEntity.isRemoved() && blockEntity.getWorld() instanceof ServerWorld && blockEntity instanceof Inventory) {
                        this.scanInventory(HolderKey.block(blockEntity.getWorld().getRegistryKey(), blockEntity.getPos()), (Inventory) blockEntity);
                    }
                }
            }
        }
        this.seen.clear();
    }

    // ------------------------------------------------------------------------------------
    // Tick
    // ------------------------------------------------------------------------------------

    private void endTick() {
        this.ticks++;
        this.flush();
        if (this.ticks % PLAYER_SCAN_INTERVAL == 0) {
            // Safety net independent of the click hook: one pass over all online players.
            List<ServerPlayerEntity> players = this.server.getPlayerManager().getPlayerList();
            if (!players.isEmpty()) {
                this.seen.clear();
                for (ServerPlayerEntity player : players) {
                    this.scanPlayer(player);
                }
                this.seen.clear();
            }
        }
        if (this.groups.isEmpty()) {
            return;
        }
        if (this.ticks % PENDING_POLL_INTERVAL == 0) {
            this.pollPending();
        }
        this.validate(true);
    }

    /** Fallback for holders that became available without one of the load hooks firing. */
    private void pollPending() {
        List<HolderKey> waiting = new ArrayList<>();
        for (Map.Entry<HolderKey, Set<Endpoint>> entry : this.byHolder.entrySet()) {
            for (Endpoint endpoint : entry.getValue()) {
                if (!this.byEndpoint.get(endpoint).links.get(endpoint).active()) {
                    waiting.add(entry.getKey());
                    break;
                }
            }
        }
        for (HolderKey holder : waiting) {
            Inventory inventory = this.locate(holder);
            if (inventory != null) {
                this.resolvePending(holder, inventory);
            } else if (this.holderGone) {
                this.dropHolder(holder, true);
            }
        }
    }

    /**
     * Re-checks every ACTIVE endpoint by identity and applies the dissolution rules. The cost
     * is proportional to the number of shadowed slots, not to the size of the world.
     */
    private void validate(boolean countGrace) {
        List<Endpoint> suspects = null;
        List<Endpoint> gone = null;
        for (ShadowGroup group : this.groups.values()) {
            for (Map.Entry<Endpoint, ShadowGroup.Link> entry : group.links.entrySet()) {
                ShadowGroup.Link link = entry.getValue();
                if (!link.active()) {
                    continue;
                }
                Endpoint endpoint = entry.getKey();
                Inventory inventory = this.locate(endpoint.holder());
                if (inventory == null) {
                    if (this.holderGone || group.canonical.isEmpty()) {
                        (gone == null ? gone = new ArrayList<>() : gone).add(endpoint);
                    } else {
                        // Unloaded without an unload hook. Keep the link; the reload check
                        // decides whether what comes back is still the same stack.
                        link.awaiting = InventoryAccess.snapshotOf(group.canonical);
                        this.markDirty();
                    }
                } else if (InventoryAccess.get(inventory, endpoint.slot()) != group.canonical) {
                    (suspects == null ? suspects = new ArrayList<>() : suspects).add(endpoint);
                }
            }
        }
        if (gone != null) {
            for (Endpoint endpoint : gone) {
                this.unlink(this.byEndpoint.get(endpoint), endpoint);
            }
        }
        if (suspects != null) {
            // The instance may only have changed slot inside the same holder (offhand swap,
            // pick block, respawn...). Look for it there before giving the endpoint up.
            this.seen.clear();
            Set<HolderKey> rescanned = new HashSet<>();
            for (Endpoint endpoint : suspects) {
                if (rescanned.add(endpoint.holder())) {
                    Inventory inventory = this.locate(endpoint.holder());
                    if (inventory != null) {
                        this.scanInventory(endpoint.holder(), inventory);
                    }
                }
            }
            this.seen.clear();
            for (Endpoint endpoint : suspects) {
                ShadowGroup group = this.byEndpoint.get(endpoint);
                if (group == null || !group.links.get(endpoint).active()) {
                    continue;
                }
                Inventory inventory = this.locate(endpoint.holder());
                if (inventory == null) {
                    this.unlink(group, endpoint);
                    continue;
                }
                ItemStack current = InventoryAccess.get(inventory, endpoint.slot());
                if (current == group.canonical) {
                    continue;
                }
                if (this.isRewrittenCopy(group, current) || this.isReloadedAgain(group, group.links.get(endpoint), current)) {
                    // The loaded holder was deserialized again in place (another mod restored
                    // its inventory from NBT). Same lifecycle artefact as a reload: put the
                    // canonical instance back. The discarded stack is an identical copy.
                    InventoryAccess.set(inventory, endpoint.slot(), group.canonical);
                    ShadowLink.LOGGER.debug("Shadow group {}: re-linked rewritten {}", group.id, endpoint);
                } else {
                    // Holder loaded, reference really gone: a genuine unshadowing.
                    this.unlink(group, endpoint);
                }
            }
        }

        for (ShadowGroup group : new ArrayList<>(this.groups.values())) {
            group.gained = false;
            int active = 0;
            int unavailable = 0;
            for (ShadowGroup.Link link : group.links.values()) {
                if (link.active()) {
                    active++;
                } else {
                    unavailable++;
                }
            }
            if (group.canonical != null && group.canonical.isEmpty()) {
                // The shared stack was used up. Loaded slots already show that; unloaded
                // ones still have to be emptied when they return, so the group lives on
                // for them only.
                for (Endpoint endpoint : new ArrayList<>(group.links.keySet())) {
                    if (group.links.get(endpoint).active()) {
                        this.unlink(group, endpoint);
                    }
                }
                if (unavailable == 0) {
                    this.dissolve(group);
                }
                continue;
            }
            if (unavailable > 0 || active >= 2) {
                // With an unavailable endpoint the group is kept even if no loaded slot
                // holds the instance right now (e.g. it is on a cursor): the returning
                // endpoint is still entitled to it.
                group.lonelyTicks = 0;
            } else if (countGrace && ++group.lonelyTicks >= LONELY_GRACE_TICKS) {
                this.dissolve(group);
            }
        }
    }

    /**
     * Tells an in-place re-deserialization of a slot apart from an in-game unshadowing.
     * <p>
     * Vanilla never replaces a stack by an equal copy while leaving the original instance
     * untouched: it either moves the instance (then it turns up in another slot or on a
     * cursor, and the group gained an endpoint) or splits it (then the original is drained).
     * So a slot that now holds a different instance with exactly the canonical content, while
     * the canonical instance was not seen moving anywhere, was rewritten from NBT.
     */
    private boolean isRewrittenCopy(ShadowGroup group, ItemStack current) {
        if (group.gained || current.isEmpty() || group.canonical.isEmpty() || this.byCanonical.containsKey(current)) {
            return false;
        }
        return InventoryAccess.snapshotOf(current).equals(InventoryAccess.snapshotOf(group.canonical));
    }

    /**
     * A holder that was just reloaded got its slot overwritten again with the data it had
     * been saved with (not with the current state of the shared stack, which may have changed
     * through the other holders in the meantime). That is the same stale copy the reload
     * already replaced once, written by another mod loading the same save a second time.
     */
    private boolean isReloadedAgain(ShadowGroup group, ShadowGroup.Link link, ItemStack current) {
        if (link.reloadedFrom == null) {
            return false;
        }
        if (this.ticks - link.reloadedAt > REWRITE_WINDOW_TICKS) {
            link.reloadedFrom = null;
            return false;
        }
        if (group.gained || current.isEmpty() || group.canonical.isEmpty() || this.byCanonical.containsKey(current)) {
            return false;
        }
        return InventoryAccess.snapshotOf(current).equals(link.reloadedFrom);
    }

    // ------------------------------------------------------------------------------------
    // Holder unload / reload
    // ------------------------------------------------------------------------------------

    /**
     * The holder is about to be serialized and discarded. Endpoints that hold the canonical
     * instance at this very moment become UNAVAILABLE; endpoints that do not were unshadowed
     * in game and are dropped, so they cannot be resurrected by the reload.
     *
     * @return whether any endpoint was kept as UNAVAILABLE
     */
    private boolean holderUnloading(HolderKey holder, Inventory inventory, boolean rescan) {
        if (this.groups.isEmpty()) {
            return false;
        }
        if (rescan) {
            this.seen.clear();
            this.scanInventory(holder, inventory);
            this.seen.clear();
        }
        Set<Endpoint> endpoints = this.byHolder.get(holder);
        if (endpoints == null) {
            return false;
        }
        boolean kept = false;
        for (Endpoint endpoint : new ArrayList<>(endpoints)) {
            ShadowGroup group = this.byEndpoint.get(endpoint);
            ShadowGroup.Link link = group.links.get(endpoint);
            if (!link.active()) {
                link.mismatchSince = -1;
                if (group.canonical != null && !group.canonical.isEmpty() && InventoryAccess.get(inventory, endpoint.slot()) == group.canonical) {
                    // Marked unavailable earlier while the holder was merely out of reach.
                    // It is serialized only now, with the stack as it is at this moment.
                    link.awaiting = InventoryAccess.snapshotOf(group.canonical);
                    this.markDirty();
                }
                kept = true;
                continue;
            }
            if (!group.canonical.isEmpty() && InventoryAccess.get(inventory, endpoint.slot()) == group.canonical) {
                link.awaiting = InventoryAccess.snapshotOf(group.canonical);
                this.markDirty();
                kept = true;
            } else {
                this.unlink(group, endpoint);
            }
        }
        return kept;
    }

    private void playerLeaving(ServerPlayerEntity player) {
        this.flush();
        // Last chance to notice a shadow formed moments before the logout.
        this.seen.clear();
        this.scanPlayer(player);
        this.seen.clear();
        UUID uuid = player.getUuid();
        this.holderUnloading(HolderKey.player(uuid), player.inventory, false);
        this.holderUnloading(HolderKey.enderChest(uuid), player.getEnderChestInventory(), false);
        this.dropHolder(HolderKey.cursor(uuid), false);
    }

    private void chunkUnloading(ServerWorld world, WorldChunk chunk) {
        this.flush();
        Set<HolderKey> holders = this.blockHoldersByChunk.get(new ChunkRef(world.getRegistryKey(), chunk.getPos().toLong()));
        if (holders == null) {
            return;
        }
        boolean kept = false;
        for (HolderKey holder : new ArrayList<>(holders)) {
            Inventory inventory = inventoryAt(chunk, holder.pos());
            if (inventory != null) {
                kept |= this.holderUnloading(holder, inventory, true);
            } else {
                this.dropHolder(holder, false);
            }
        }
        if (kept) {
            // A shadowed stack changes through its other holders without this chunk being
            // marked dirty. Force the save that follows so that the data on disk is the
            // stack as it is now, which is what the reload check compares against.
            chunk.setShouldSave(true);
        }
    }

    private void chunkLoaded(ServerWorld world, WorldChunk chunk) {
        Set<HolderKey> holders = this.blockHoldersByChunk.get(new ChunkRef(world.getRegistryKey(), chunk.getPos().toLong()));
        if (holders == null) {
            return;
        }
        for (HolderKey holder : new ArrayList<>(holders)) {
            Inventory inventory = inventoryAt(chunk, holder.pos());
            if (inventory != null) {
                this.resolvePending(holder, inventory);
            } else {
                this.dropHolder(holder, true);
            }
        }
    }

    private void resolvePending(HolderKey holder, Inventory inventory) {
        Set<Endpoint> endpoints = this.byHolder.get(holder);
        if (endpoints == null) {
            return;
        }
        for (Endpoint endpoint : new ArrayList<>(endpoints)) {
            ShadowGroup group = this.byEndpoint.get(endpoint);
            ShadowGroup.Link link = group.links.get(endpoint);
            if (!link.active()) {
                this.restore(group, endpoint, link, inventory);
            }
        }
    }

    /**
     * Re-links one endpoint whose holder has just been deserialized. The slot now contains a
     * fresh copy made from NBT; it is replaced by the canonical instance itself.
     */
    private void restore(ShadowGroup group, Endpoint endpoint, ShadowGroup.Link link, Inventory inventory) {
        int slot = endpoint.slot();
        if (slot < 0 || slot >= InventoryAccess.size(inventory)) {
            this.failClosed(group, endpoint);
            return;
        }
        ItemStack current = InventoryAccess.get(inventory, slot);
        if (group.canonical != null && current == group.canonical) {
            link.awaiting = null;
            this.markDirty();
            return;
        }
        // The slot must contain exactly what was serialized when this endpoint went away.
        // Anything else means the data changed behind our back (edited or rolled back
        // player data, a crash between saves, another mod): overwriting it could destroy
        // or duplicate items, so the endpoint is released instead.
        if (!InventoryAccess.snapshotOf(current).equals(link.awaiting)) {
            // Not final yet: the holder may still be in the middle of being restored by
            // another mod. pollPending() retries until the settle time is over.
            if (link.mismatchSince < 0) {
                link.mismatchSince = this.ticks;
            }
            if (this.ticks - link.mismatchSince >= RELOAD_SETTLE_TICKS) {
                this.failClosed(group, endpoint);
            }
            return;
        }
        link.mismatchSince = -1;
        if (group.canonical == null) {
            // First endpoint back after a restart: no live instance exists yet.
            if (link.awaiting.equals(group.snapshot) && !current.isEmpty()) {
                // This copy is identical to the last known canonical state; adopt the
                // instance as it is, nothing needs to be written.
                group.canonical = current;
            } else if (group.inconsistent) {
                this.failClosed(group, endpoint);
                return;
            } else {
                // This endpoint was unloaded earlier than the others and its copy is
                // outdated. Rebuild the canonical stack from the state saved last.
                group.canonical = InventoryAccess.fromSnapshot(group.snapshot);
            }
            if (!group.canonical.isEmpty()) {
                this.byCanonical.put(group.canonical, group);
            }
        }
        if (group.canonical.isEmpty()) {
            // The shared stack was used up while this endpoint was away.
            if (!current.isEmpty()) {
                InventoryAccess.set(inventory, slot, ItemStack.EMPTY);
            }
            this.unlink(group, endpoint);
            return;
        }
        // The same instance, never a copy. Clients are updated by the regular per-tick
        // ScreenHandler content sync, which compares stack contents.
        if (current != group.canonical) {
            InventoryAccess.set(inventory, slot, group.canonical);
        }
        link.reloadedFrom = link.awaiting;
        link.reloadedAt = this.ticks;
        link.awaiting = null;
        this.markDirty();
        ShadowLink.LOGGER.debug("Shadow group {}: restored {}", group.id, endpoint);
    }

    private void failClosed(ShadowGroup group, Endpoint endpoint) {
        if (!group.inconsistent) {
            group.inconsistent = true;
            ShadowLink.LOGGER.warn("Shadow group {}: {} no longer matches its saved state. The slot is left untouched and unlinked.", group.id, endpoint);
        }
        this.unlink(group, endpoint);
    }

    // ------------------------------------------------------------------------------------
    // Persistence
    // ------------------------------------------------------------------------------------

    private boolean ensureLoaded() {
        if (this.state != null) {
            return true;
        }
        ServerWorld overworld = this.server.getOverworld();
        if (overworld == null) {
            return false;
        }
        this.state = overworld.getPersistentStateManager().getOrCreate(() -> new ShadowState(this), ShadowState.ID);
        return true;
    }

    void readNbtSafely(NbtCompound nbt) {
        try {
            this.readNbt(nbt);
        } catch (RuntimeException e) {
            ShadowLink.LOGGER.error("Could not read saved shadow links; starting without them", e);
        }
    }

    NbtCompound writeNbt(NbtCompound nbt) {
        try {
            // Make the saved links truthful as of this very moment.
            this.flush();
            this.validate(false);
        } catch (RuntimeException e) {
            ShadowLink.LOGGER.error("Could not validate shadow links before saving", e);
        }
        nbt.putInt("version", FORMAT_VERSION);
        NbtList groupList = new NbtList();
        for (ShadowGroup group : this.groups.values()) {
            NbtCompound snapshot = group.canonical != null ? InventoryAccess.snapshotOf(group.canonical) : group.snapshot;
            NbtList endpointList = new NbtList();
            boolean anyUnavailable = false;
            for (Map.Entry<Endpoint, ShadowGroup.Link> entry : group.links.entrySet()) {
                HolderKey holder = entry.getKey().holder();
                if (holder.kind() == HolderKey.Kind.CURSOR) {
                    continue;
                }
                NbtCompound endpointNbt = new NbtCompound();
                endpointNbt.putString("kind", holder.kind().id);
                if (holder.uuid() != null) {
                    endpointNbt.putUuid("uuid", holder.uuid());
                }
                if (holder.dimension() != null) {
                    endpointNbt.putString("dimension", holder.dimension().getValue().toString());
                }
                if (holder.pos() != null) {
                    endpointNbt.putLong("pos", holder.pos().asLong());
                }
                endpointNbt.putInt("slot", entry.getKey().slot());
                ShadowGroup.Link link = entry.getValue();
                if (link.active()) {
                    // Saved together with the canonical stack, so the group snapshot applies.
                    if (holder.kind() == HolderKey.Kind.BLOCK) {
                        this.markChunkForSaving(holder);
                    }
                } else {
                    anyUnavailable = true;
                    if (!link.awaiting.equals(snapshot)) {
                        endpointNbt.put("stack", link.awaiting);
                    }
                }
                endpointList.add(endpointNbt);
            }
            if (endpointList.size() < 2 && !anyUnavailable) {
                continue;
            }
            NbtCompound groupNbt = new NbtCompound();
            groupNbt.putUuid("id", group.id);
            groupNbt.put("stack", snapshot);
            groupNbt.put("endpoints", endpointList);
            groupList.add(groupNbt);
        }
        nbt.put("groups", groupList);
        return nbt;
    }

    /** See {@link #chunkUnloading}: the world save that follows must write the current stack. */
    private void markChunkForSaving(HolderKey blockHolder) {
        ServerWorld world = this.server.getWorld(blockHolder.dimension());
        if (world != null) {
            BlockPos pos = blockHolder.pos();
            WorldChunk chunk = world.getChunkManager().getWorldChunk(pos.getX() >> 4, pos.getZ() >> 4);
            if (chunk != null) {
                chunk.setShouldSave(true);
            }
        }
    }

    /** After a load every endpoint is UNAVAILABLE and no group has a canonical instance. */
    private void readNbt(NbtCompound nbt) {
        int version = nbt.getInt("version");
        if (version != FORMAT_VERSION) {
            // Migrations for future formats go here.
            ShadowLink.LOGGER.warn("Unsupported shadow link data version {}; saved links are ignored", version);
            return;
        }
        NbtList groupList = nbt.getList("groups", TAG_COMPOUND);
        for (int i = 0; i < groupList.size(); i++) {
            NbtCompound groupNbt = groupList.getCompound(i);
            if (!groupNbt.containsUuid("id")) {
                continue;
            }
            NbtCompound snapshot = groupNbt.getCompound("stack");
            ShadowGroup group = new ShadowGroup(groupNbt.getUuid("id"), null, snapshot);
            NbtList endpointList = groupNbt.getList("endpoints", TAG_COMPOUND);
            for (int j = 0; j < endpointList.size(); j++) {
                NbtCompound endpointNbt = endpointList.getCompound(j);
                HolderKey holder = readHolder(endpointNbt);
                if (holder == null) {
                    continue;
                }
                NbtCompound awaiting = endpointNbt.contains("stack", TAG_COMPOUND) ? endpointNbt.getCompound("stack") : snapshot;
                Endpoint endpoint = new Endpoint(holder, endpointNbt.getInt("slot"));
                if (!this.byEndpoint.containsKey(endpoint)) {
                    this.link(group, endpoint, awaiting);
                }
            }
            if (group.links.isEmpty()) {
                continue;
            }
            this.groups.put(group.id, group);
        }
    }

    private static HolderKey readHolder(NbtCompound nbt) {
        HolderKey.Kind kind = HolderKey.Kind.byId(nbt.getString("kind"));
        if (kind == null || kind == HolderKey.Kind.CURSOR) {
            return null;
        }
        UUID uuid = nbt.containsUuid("uuid") ? nbt.getUuid("uuid") : null;
        Identifier dimensionId = Identifier.tryParse(nbt.getString("dimension"));
        RegistryKey<World> dimension = nbt.contains("dimension", TAG_STRING) && dimensionId != null
                ? RegistryKey.of(Registry.WORLD_KEY, dimensionId) : null;
        switch (kind) {
            case PLAYER:
                return uuid == null ? null : HolderKey.player(uuid);
            case ENDER_CHEST:
                return uuid == null ? null : HolderKey.enderChest(uuid);
            case BLOCK:
                return dimension == null || !nbt.contains("pos", TAG_LONG) ? null
                        : HolderKey.block(dimension, BlockPos.fromLong(nbt.getLong("pos")));
            case ENTITY:
                return uuid == null || dimension == null ? null : HolderKey.entity(uuid, dimension);
            default:
                return null;
        }
    }
}
