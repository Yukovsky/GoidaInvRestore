package com.goida.goidainvrestore.backup;

import net.minecraft.nbt.CompoundTag;

/**
 * Lightweight backup descriptor used for history listings / pagination without loading the full
 * item payload. {@code index} is the snapshot's position in the player's history.
 */
public record SnapshotMeta(int index, long timestamp, String reason, String dimension,
                           int xpLevel, int totalXp, int itemCount) {

    public static SnapshotMeta fromNbt(int index, CompoundTag tag) {
        return new SnapshotMeta(
                index,
                tag.getLong("timestamp"),
                tag.getString("reason"),
                tag.getString("dimension"),
                tag.getInt("xpLevel"),
                tag.getInt("totalXp"),
                tag.getInt("itemCount"));
    }
}
