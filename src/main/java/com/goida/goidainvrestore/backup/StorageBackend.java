package com.goida.goidainvrestore.backup;

import net.minecraft.nbt.CompoundTag;

import java.util.List;
import java.util.UUID;

/**
 * Persistence abstraction for backup history. Implementations store an opaque, fully-serialized
 * snapshot {@link CompoundTag} per record. {@code index} is an implementation-defined handle that
 * round-trips through {@link #list}, {@link #load} and {@link #delete} (a list position for the
 * file backend, a row id for MySQL) — callers must treat it as opaque.
 */
public interface StorageBackend {

    /** Stores a snapshot for the player and trims the history down to {@code maxRecords}. */
    void append(UUID uuid, CompoundTag snapshot, int maxRecords);

    /** Lightweight metadata for every stored snapshot, oldest first. */
    List<SnapshotMeta> list(UUID uuid);

    /** Full snapshot payload for a handle from {@link #list}, or {@code null} if gone. */
    CompoundTag load(UUID uuid, int index);

    void delete(UUID uuid, int index);

    void clear(UUID uuid);

    default void close() {}
}
