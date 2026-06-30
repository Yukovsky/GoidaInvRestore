package com.goida.goidainvrestore.menu;

import com.goida.goidainvrestore.backup.BackupManager;
import com.goida.goidainvrestore.backup.InventorySnapshot;
import com.goida.goidainvrestore.backup.InventorySnapshot.Origin;
import com.goida.goidainvrestore.backup.RestoreMode;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Paginated view of a single backup's items, with restore/extract controls. */
public final class SnapshotMenu extends PaginatedChestMenu {

    private record Entry(ItemStack stack, String section) {}

    private static final int BTN_OVERWRITE = 1;
    private static final int BTN_GIVE = 2;
    private static final int BTN_EXTRACT = 3;
    private static final int BTN_BACK = 6;

    private final MinecraftServer server;
    private final UUID targetId;
    private final String targetName;
    private final int snapshotIndex;
    private final List<Entry> entries;
    private boolean extractMode;

    private SnapshotMenu(int id, Inventory playerInv, MinecraftServer server, UUID targetId,
                         String targetName, int snapshotIndex, InventorySnapshot snapshot) {
        super(id, playerInv, new SimpleContainer(SIZE));
        this.server = server;
        this.targetId = targetId;
        this.targetName = targetName;
        this.snapshotIndex = snapshotIndex;
        this.entries = buildEntries(snapshot);
        rebuild();
    }

    public static void open(ServerPlayer admin, UUID targetId, String targetName, int snapshotIndex) {
        InventorySnapshot snapshot = BackupManager.loadSnapshot(targetId, snapshotIndex);
        if (snapshot == null) {
            admin.sendSystemMessage(Component.literal("§cБэкап не найден (#" + snapshotIndex + ")."));
            return;
        }
        Component title = Component.literal("§8Бэкап §f" + targetName + " §8(" + BackupManager.formatTime(snapshot.timestamp()) + ")");
        admin.openMenu(new SimpleMenuProvider(
                (id, inv, p) -> new SnapshotMenu(id, inv, admin.server, targetId, targetName, snapshotIndex, snapshot),
                title));
    }

    private static List<Entry> buildEntries(InventorySnapshot snapshot) {
        List<Entry> list = new ArrayList<>();
        for (Origin o : snapshot.buildLayout()) {
            ItemStack stack = snapshot.get(o);
            if (stack != null && !stack.isEmpty()) {
                list.add(new Entry(stack.copy(), sectionLabel(o.section())));
            }
        }
        return list;
    }

    private static String sectionLabel(InventorySnapshot.Section section) {
        return switch (section) {
            case VANILLA -> "Инвентарь";
            case ENDER -> "Эндер-сундук";
            case CURIOS -> "Curios";
            case COSMETIC -> "Косметика";
        };
    }

    @Override
    protected int contentCount() {
        return entries.size();
    }

    @Override
    protected ItemStack contentDisplay(int absoluteIndex) {
        Entry e = entries.get(absoluteIndex);
        if (extractMode) {
            return withLore(e.stack(), "§7Раздел: §f" + e.section(), "§eКлик — забрать копию себе");
        }
        return withLore(e.stack(), "§7Раздел: §f" + e.section());
    }

    @Override
    protected void onContentClick(int absoluteIndex, Player player) {
        if (!extractMode || !(player instanceof ServerPlayer sp)) return;
        ItemStack copy = entries.get(absoluteIndex).stack().copy();
        if (!sp.getInventory().add(copy)) {
            sp.drop(copy, false);
        }
        sp.sendSystemMessage(Component.literal("§aВыдан предмет: §f" + copy.getHoverName().getString()));
    }

    @Override
    protected void fillExtraControls(SimpleContainer c) {
        c.setItem(CONTROL_ROW + BTN_OVERWRITE, button(Items.LIME_WOOL,
                "§aВосстановить (перезапись)",
                "§7Очистит текущий инвентарь и запишет бэкап.",
                "§7Перед этим создаётся страховочный бэкап."));
        c.setItem(CONTROL_ROW + BTN_GIVE, button(Items.CHEST,
                "§aВыдать всё (без очистки)",
                "§7Добавит все предметы бэкапа в инвентарь;",
                "§7лишнее выпадет на землю."));
        c.setItem(CONTROL_ROW + BTN_EXTRACT, button(Items.HOPPER,
                extractMode ? "§eРежим извлечения: ВКЛ" : "§7Режим извлечения: выкл",
                "§7Клик по предмету выдаёт его копию."));
        c.setItem(CONTROL_ROW + BTN_BACK, button(Items.BARRIER, "§cНазад к истории"));
    }

    @Override
    protected void onControlClick(int controlIndex, Player player) {
        if (!(player instanceof ServerPlayer sp)) return;
        switch (controlIndex) {
            case BTN_OVERWRITE -> doRestore(sp, RestoreMode.OVERWRITE);
            case BTN_GIVE -> doRestore(sp, RestoreMode.GIVE_ALL);
            case BTN_EXTRACT -> {
                extractMode = !extractMode;
                rebuild();
            }
            case BTN_BACK -> HistoryMenu.open(sp, targetId, targetName);
            default -> { /* no-op */ }
        }
    }

    private void doRestore(ServerPlayer admin, RestoreMode mode) {
        ServerPlayer target = server.getPlayerList().getPlayer(targetId);
        if (target == null) {
            admin.sendSystemMessage(Component.literal("§cИгрок §f" + targetName + " §cне в сети — восстановление невозможно."));
            return;
        }
        boolean ok = BackupManager.restore(admin.createCommandSourceStack(), target, snapshotIndex, mode);
        if (ok) {
            admin.sendSystemMessage(Component.literal("§aБэкап восстановлен игроку §f" + targetName
                    + " §a(" + (mode == RestoreMode.OVERWRITE ? "перезапись" : "выдача") + ")."));
            admin.closeContainer();
        } else {
            admin.sendSystemMessage(Component.literal("§cНе удалось восстановить бэкап."));
        }
    }
}
