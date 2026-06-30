package com.goida.goidainvrestore.backup;

import com.goida.goidainvrestore.Config;
import com.goida.goidainvrestore.compat.CosmeticArmorCompat;
import com.goida.goidainvrestore.compat.CuriosCompat;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A point-in-time copy of a player's storable state: vanilla inventory (0–35 main, 36–39 armor,
 * 40 offhand), ender chest, Curios + Cosmetic Armor slots and experience. Stored per-slot so the
 * GUI can page through it and so a restore writes every stack back to its exact slot. Item fidelity
 * (incl. modded containers such as Sophisticated Backpacks, whose contents live in the ItemStack's
 * data components) is preserved by the registry-aware {@code ItemStack.save / parseOptional} pair.
 */
public final class InventorySnapshot {

    /** Vanilla inventory size via {@code Inventory#getItem}: 0–35 main, 36–39 armor, 40 offhand. */
    public static final int VANILLA_SIZE = 41;

    public enum Section { VANILLA, ENDER, CURIOS, COSMETIC }

    /** A logical slot: a section plus its index within that section. */
    public record Origin(Section section, int index) {}

    private final ItemStack[] vanilla;
    private final ItemStack[] ender;
    private final ItemStack[] curios;
    private final ItemStack[] cosmetic;

    private long timestamp;
    private String reason = "MANUAL";
    private String dimension = "";
    private int xpLevel;
    private float xpProgress;
    private int totalXp;

    private InventorySnapshot(int enderSize, int curiosSize, int cosmeticSize) {
        this.vanilla = filled(VANILLA_SIZE);
        this.ender = filled(Math.max(0, enderSize));
        this.curios = filled(Math.max(0, curiosSize));
        this.cosmetic = filled(Math.max(0, cosmeticSize));
    }

    private static ItemStack[] filled(int n) {
        ItemStack[] a = new ItemStack[n];
        Arrays.fill(a, ItemStack.EMPTY);
        return a;
    }

    // ---- Capture --------------------------------------------------------------------------

    /** Snapshots the player's current state (copies; does not modify the player). */
    public static InventorySnapshot capture(ServerPlayer player, String reason) {
        boolean modded = Config.CAPTURE_CURIOS.get();
        boolean enderEnabled = Config.CAPTURE_ENDER_CHEST.get();

        int enderSize = enderEnabled ? player.getEnderChestInventory().getContainerSize() : 0;
        int curiosSize = (modded && CuriosCompat.isLoaded()) ? CuriosCompat.slotCount(player) : 0;
        int cosmeticSize = (modded && CosmeticArmorCompat.isLoaded()) ? CosmeticArmorCompat.slotCount() : 0;

        InventorySnapshot s = new InventorySnapshot(enderSize, curiosSize, cosmeticSize);
        Inventory inv = player.getInventory();
        for (int i = 0; i < VANILLA_SIZE; i++) s.vanilla[i] = inv.getItem(i).copy();
        Container ec = player.getEnderChestInventory();
        for (int i = 0; i < enderSize; i++) s.ender[i] = ec.getItem(i).copy();
        for (int i = 0; i < curiosSize; i++) s.curios[i] = CuriosCompat.getStack(player, i).copy();
        for (int i = 0; i < cosmeticSize; i++) s.cosmetic[i] = CosmeticArmorCompat.getStack(player, i).copy();

        s.timestamp = System.currentTimeMillis();
        s.reason = reason;
        s.dimension = player.level().dimension().location().toString();
        if (Config.CAPTURE_XP.get()) {
            s.xpLevel = player.experienceLevel;
            s.xpProgress = player.experienceProgress;
            s.totalXp = player.totalExperience;
        }
        return s;
    }

    // ---- Restore --------------------------------------------------------------------------

    public void restore(ServerPlayer player, RestoreMode mode) {
        if (mode == RestoreMode.OVERWRITE) {
            restoreOverwrite(player);
        } else {
            restoreGiveAll(player);
        }
        player.inventoryMenu.broadcastChanges();
    }

    private void restoreOverwrite(ServerPlayer player) {
        Inventory inv = player.getInventory();
        inv.clearContent();
        for (int i = 0; i < VANILLA_SIZE; i++) {
            if (!vanilla[i].isEmpty()) inv.setItem(i, vanilla[i].copy());
        }
        if (Config.CAPTURE_ENDER_CHEST.get()) {
            Container ec = player.getEnderChestInventory();
            ec.clearContent();
            for (int i = 0; i < ender.length && i < ec.getContainerSize(); i++) {
                if (!ender[i].isEmpty()) ec.setItem(i, ender[i].copy());
            }
        }
        if (CuriosCompat.isLoaded()) {
            int n = CuriosCompat.slotCount(player);
            for (int i = 0; i < n; i++) {
                CuriosCompat.setStack(player, i, i < curios.length ? curios[i].copy() : ItemStack.EMPTY);
            }
        }
        if (CosmeticArmorCompat.isLoaded()) {
            int n = CosmeticArmorCompat.slotCount();
            for (int i = 0; i < n; i++) {
                CosmeticArmorCompat.setStack(player, i, i < cosmetic.length ? cosmetic[i].copy() : ItemStack.EMPTY);
            }
        }
        if (Config.CAPTURE_XP.get()) {
            player.experienceLevel = 0;
            player.experienceProgress = 0;
            player.totalExperience = 0;
            player.giveExperiencePoints(totalXp);
        }
    }

    private void restoreGiveAll(ServerPlayer player) {
        // Items go to free inventory slots, overflow drops. Ender/Curios/Cosmetic also funnel into
        // the main inventory so nothing is silently overwritten.
        for (ItemStack[] section : new ItemStack[][]{vanilla, ender, curios, cosmetic}) {
            for (ItemStack stack : section) {
                if (stack == null || stack.isEmpty()) continue;
                giveOrDrop(player, stack.copy());
            }
        }
        if (Config.CAPTURE_XP.get() && totalXp > 0) {
            player.giveExperiencePoints(totalXp);
        }
    }

    private static void giveOrDrop(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    // ---- GUI layout access ----------------------------------------------------------------

    /** Ordered logical slots: vanilla, ender, Curios, cosmetic. */
    public List<Origin> buildLayout() {
        List<Origin> layout = new ArrayList<>(vanilla.length + ender.length + curios.length + cosmetic.length);
        for (int i = 0; i < vanilla.length; i++) layout.add(new Origin(Section.VANILLA, i));
        for (int i = 0; i < ender.length; i++) layout.add(new Origin(Section.ENDER, i));
        for (int i = 0; i < curios.length; i++) layout.add(new Origin(Section.CURIOS, i));
        for (int i = 0; i < cosmetic.length; i++) layout.add(new Origin(Section.COSMETIC, i));
        return layout;
    }

    public ItemStack get(Origin o) {
        ItemStack[] a = arrayFor(o.section());
        return (o.index() >= 0 && o.index() < a.length) ? a[o.index()] : ItemStack.EMPTY;
    }

    private ItemStack[] arrayFor(Section s) {
        return switch (s) {
            case VANILLA -> vanilla;
            case ENDER -> ender;
            case CURIOS -> curios;
            case COSMETIC -> cosmetic;
        };
    }

    // ---- Metadata -------------------------------------------------------------------------

    public long timestamp() { return timestamp; }
    public String reason() { return reason; }
    public String dimension() { return dimension; }
    public int xpLevel() { return xpLevel; }
    public int totalXp() { return totalXp; }

    public int itemCount() {
        int n = 0;
        for (ItemStack[] sec : new ItemStack[][]{vanilla, ender, curios, cosmetic}) {
            for (ItemStack st : sec) if (st != null && !st.isEmpty()) n++;
        }
        return n;
    }

    /**
     * A stable digest of just the item payload (no metadata), used to skip duplicate periodic
     * backups when nothing has changed.
     */
    public String contentHash(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.put("v", sectionToNbt(vanilla, provider));
        tag.put("e", sectionToNbt(ender, provider));
        tag.put("c", sectionToNbt(curios, provider));
        tag.put("k", sectionToNbt(cosmetic, provider));
        return Integer.toHexString(tag.toString().hashCode());
    }

    // ---- Serialization --------------------------------------------------------------------

    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putLong("timestamp", timestamp);
        tag.putString("reason", reason);
        tag.putString("dimension", dimension);
        tag.putInt("xpLevel", xpLevel);
        tag.putFloat("xpProgress", xpProgress);
        tag.putInt("totalXp", totalXp);
        tag.putInt("itemCount", itemCount());
        tag.putInt("enderSize", ender.length);
        tag.putInt("curiosSize", curios.length);
        tag.putInt("cosmeticSize", cosmetic.length);
        tag.put("vanilla", sectionToNbt(vanilla, provider));
        tag.put("ender", sectionToNbt(ender, provider));
        tag.put("curios", sectionToNbt(curios, provider));
        tag.put("cosmetic", sectionToNbt(cosmetic, provider));
        return tag;
    }

    public static InventorySnapshot load(CompoundTag tag, HolderLookup.Provider provider) {
        InventorySnapshot s = new InventorySnapshot(
                tag.getInt("enderSize"), tag.getInt("curiosSize"), tag.getInt("cosmeticSize"));
        s.timestamp = tag.getLong("timestamp");
        s.reason = tag.getString("reason");
        s.dimension = tag.getString("dimension");
        s.xpLevel = tag.getInt("xpLevel");
        s.xpProgress = tag.getFloat("xpProgress");
        s.totalXp = tag.getInt("totalXp");
        sectionFromNbt(s.vanilla, tag.getList("vanilla", Tag.TAG_COMPOUND), provider);
        sectionFromNbt(s.ender, tag.getList("ender", Tag.TAG_COMPOUND), provider);
        sectionFromNbt(s.curios, tag.getList("curios", Tag.TAG_COMPOUND), provider);
        sectionFromNbt(s.cosmetic, tag.getList("cosmetic", Tag.TAG_COMPOUND), provider);
        return s;
    }

    private static ListTag sectionToNbt(ItemStack[] arr, HolderLookup.Provider provider) {
        ListTag list = new ListTag();
        for (int i = 0; i < arr.length; i++) {
            if (arr[i] == null || arr[i].isEmpty()) continue;
            CompoundTag e = new CompoundTag();
            e.putInt("Slot", i);
            e.put("item", arr[i].save(provider));
            list.add(e);
        }
        return list;
    }

    private static void sectionFromNbt(ItemStack[] arr, ListTag list, HolderLookup.Provider provider) {
        for (int i = 0; i < list.size(); i++) {
            CompoundTag e = list.getCompound(i);
            int slot = e.getInt("Slot");
            if (slot < 0 || slot >= arr.length) continue;
            arr[slot] = ItemStack.parseOptional(provider, e.getCompound("item"));
        }
    }
}
