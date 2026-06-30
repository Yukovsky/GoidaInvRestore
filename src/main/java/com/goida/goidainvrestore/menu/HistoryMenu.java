package com.goida.goidainvrestore.menu;

import com.goida.goidainvrestore.backup.BackupManager;
import com.goida.goidainvrestore.backup.SnapshotMeta;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Paginated list of a player's backup history; clicking an entry opens it in {@link SnapshotMenu}. */
public final class HistoryMenu extends PaginatedChestMenu {

    private final UUID targetId;
    private final String targetName;
    private final List<SnapshotMeta> metas;

    private HistoryMenu(int id, Inventory playerInv, UUID targetId, String targetName, List<SnapshotMeta> metas) {
        super(id, playerInv, new SimpleContainer(SIZE));
        this.targetId = targetId;
        this.targetName = targetName;
        this.metas = metas;
        rebuild();
    }

    public static void open(ServerPlayer admin, UUID targetId, String targetName) {
        List<SnapshotMeta> metas = new ArrayList<>(BackupManager.list(targetId));
        // Newest first.
        metas.sort((a, b) -> Long.compare(b.timestamp(), a.timestamp()));
        if (metas.isEmpty()) {
            admin.sendSystemMessage(Component.literal("§eУ игрока §f" + targetName + " §eнет бэкапов."));
            return;
        }
        Component title = Component.literal("§8Бэкапы: §f" + targetName + " §8(" + metas.size() + ")");
        admin.openMenu(new SimpleMenuProvider(
                (id, inv, p) -> new HistoryMenu(id, inv, targetId, targetName, metas), title));
    }

    @Override
    protected int contentCount() {
        return metas.size();
    }

    @Override
    protected ItemStack contentDisplay(int absoluteIndex) {
        SnapshotMeta m = metas.get(absoluteIndex);
        return button(iconFor(m.reason()),
                "§b" + reasonLabel(m.reason()) + " §7— §f" + BackupManager.formatTime(m.timestamp()),
                "§7Измерение: §f" + shortDim(m.dimension()),
                "§7Предметов: §f" + m.itemCount(),
                "§7Опыт: §f" + m.xpLevel() + " ур.",
                "§eКлик — открыть бэкап");
    }

    @Override
    protected void onContentClick(int absoluteIndex, Player player) {
        if (player instanceof ServerPlayer sp) {
            SnapshotMeta m = metas.get(absoluteIndex);
            SnapshotMenu.open(sp, targetId, targetName, m.index());
        }
    }

    private static Item iconFor(String reason) {
        return switch (reason) {
            case BackupManager.DEATH -> Items.SKELETON_SKULL;
            case BackupManager.PERIODIC -> Items.CLOCK;
            case BackupManager.PRE_RESTORE -> Items.SHIELD;
            case BackupManager.LOGOUT -> Items.OAK_DOOR;
            default -> Items.WRITABLE_BOOK; // MANUAL and unknown
        };
    }

    private static String reasonLabel(String reason) {
        return switch (reason) {
            case BackupManager.DEATH -> "Смерть";
            case BackupManager.PERIODIC -> "Авто";
            case BackupManager.MANUAL -> "Вручную";
            case BackupManager.PRE_RESTORE -> "Перед откатом";
            case BackupManager.LOGOUT -> "Выход";
            default -> reason;
        };
    }

    private static String shortDim(String dim) {
        int idx = dim.indexOf(':');
        return idx >= 0 ? dim.substring(idx + 1) : dim;
    }
}
