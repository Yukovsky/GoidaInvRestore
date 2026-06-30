package com.goida.goidainvrestore.backup;

import com.goida.goidainvrestore.GoidaInvRestore;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Per-player history in {@code <world>/goidainvrestore/<uuid>.dat}: one compressed {@link CompoundTag}
 * holding a {@code "snapshots"} {@link ListTag} (oldest first), trimmed as a ring buffer. Writes are
 * atomic (temp file + move) so a crash never corrupts an existing history.
 */
public final class FileBackend implements StorageBackend {

    private final Path dir;

    public FileBackend(Path dir) {
        this.dir = dir;
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            GoidaInvRestore.LOGGER.error("Could not create backup directory {}", dir, e);
        }
    }

    private Path fileFor(UUID uuid) {
        return dir.resolve(uuid + ".dat");
    }

    private synchronized ListTag readList(UUID uuid) {
        Path f = fileFor(uuid);
        if (!Files.isRegularFile(f)) return new ListTag();
        try {
            CompoundTag root = NbtIo.readCompressed(f, NbtAccounter.unlimitedHeap());
            return root.getList("snapshots", Tag.TAG_COMPOUND);
        } catch (Exception e) {
            GoidaInvRestore.LOGGER.error("Failed to read backups for {} — treating as empty", uuid, e);
            return new ListTag();
        }
    }

    private synchronized void writeList(UUID uuid, ListTag list) {
        Path f = fileFor(uuid);
        try {
            if (list.isEmpty()) {
                Files.deleteIfExists(f);
                return;
            }
            CompoundTag root = new CompoundTag();
            root.put("snapshots", list);
            Path tmp = f.resolveSibling(uuid + ".dat.tmp");
            NbtIo.writeCompressed(root, tmp);
            Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            GoidaInvRestore.LOGGER.error("Failed to write backups for {}", uuid, e);
        }
    }

    @Override
    public synchronized void append(UUID uuid, CompoundTag snapshot, int maxRecords) {
        ListTag list = readList(uuid);
        list.add(snapshot);
        while (list.size() > maxRecords && !list.isEmpty()) {
            list.remove(0);
        }
        writeList(uuid, list);
    }

    @Override
    public synchronized List<SnapshotMeta> list(UUID uuid) {
        ListTag list = readList(uuid);
        List<SnapshotMeta> out = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            out.add(SnapshotMeta.fromNbt(i, list.getCompound(i)));
        }
        return out;
    }

    @Override
    public synchronized CompoundTag load(UUID uuid, int index) {
        ListTag list = readList(uuid);
        if (index < 0 || index >= list.size()) return null;
        return list.getCompound(index);
    }

    @Override
    public synchronized void delete(UUID uuid, int index) {
        ListTag list = readList(uuid);
        if (index < 0 || index >= list.size()) return;
        list.remove(index);
        writeList(uuid, list);
    }

    @Override
    public synchronized void clear(UUID uuid) {
        try {
            Files.deleteIfExists(fileFor(uuid));
        } catch (IOException e) {
            GoidaInvRestore.LOGGER.error("Failed to clear backups for {}", uuid, e);
        }
    }
}
