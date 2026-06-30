package com.goida.goidainvrestore.menu;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;

/**
 * A read-only, paginated 6×9 chest GUI rendered by the vanilla client (no custom networking).
 * The top 5 rows (45 slots) show content; the bottom row (9 slots) holds control buttons. All item
 * movement is blocked — clicks only navigate or trigger subclass actions — so nothing can be duped.
 */
public abstract class PaginatedChestMenu extends ChestMenu {

    protected static final int ROWS = 6;
    protected static final int PAGE_SIZE = 45;            // top 5 rows
    protected static final int CONTROL_ROW = PAGE_SIZE;   // first control slot (45)
    protected static final int SIZE = ROWS * 9;           // 54

    // Control-row positions (relative to CONTROL_ROW).
    protected static final int CTRL_PREV = 0;
    protected static final int CTRL_INFO = 4;
    protected static final int CTRL_NEXT = 8;

    protected final SimpleContainer container;
    protected int page;

    protected PaginatedChestMenu(int id, Inventory playerInv, SimpleContainer container) {
        super(MenuType.GENERIC_9x6, id, playerInv, container, ROWS);
        this.container = container;
    }

    // ---- Subclass contract ----------------------------------------------------------------

    protected abstract int contentCount();

    /** Display item for an absolute content index (0..contentCount-1). */
    protected abstract ItemStack contentDisplay(int absoluteIndex);

    /** Called when a content slot is clicked (e.g. extract). Default: no-op (read-only). */
    protected void onContentClick(int absoluteIndex, Player player) {}

    /** Add subclass buttons to the control row (avoid CTRL_PREV/CTRL_INFO/CTRL_NEXT). */
    protected void fillExtraControls(SimpleContainer c) {}

    /** Called when an extra control button is clicked (control index 0..8, excl. prev/info/next). */
    protected void onControlClick(int controlIndex, Player player) {}

    // ---- Pagination -----------------------------------------------------------------------

    protected int totalPages() {
        return Math.max(1, (contentCount() + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    protected void rebuild() {
        for (int i = 0; i < SIZE; i++) container.setItem(i, ItemStack.EMPTY);

        int start = page * PAGE_SIZE;
        int count = contentCount();
        for (int i = 0; i < PAGE_SIZE; i++) {
            int idx = start + i;
            container.setItem(i, idx < count ? contentDisplay(idx) : ItemStack.EMPTY);
        }

        if (page > 0) {
            container.setItem(CONTROL_ROW + CTRL_PREV,
                    button(Items.ARROW, "§a« Предыдущая страница"));
        }
        if (page < totalPages() - 1) {
            container.setItem(CONTROL_ROW + CTRL_NEXT,
                    button(Items.ARROW, "§aСледующая страница »"));
        }
        container.setItem(CONTROL_ROW + CTRL_INFO,
                button(Items.PAPER, "§eСтраница " + (page + 1) + "/" + totalPages()));

        fillExtraControls(container);
        broadcastChanges();
    }

    // ---- Interaction (everything blocked except navigation/actions) -----------------------

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId >= 0 && slotId < PAGE_SIZE) {
            int abs = page * PAGE_SIZE + slotId;
            if (abs < contentCount()) onContentClick(abs, player);
        } else if (slotId >= CONTROL_ROW && slotId < SIZE) {
            int ctrl = slotId - CONTROL_ROW;
            if (ctrl == CTRL_PREV && page > 0) {
                page--;
                rebuild();
            } else if (ctrl == CTRL_NEXT && page < totalPages() - 1) {
                page++;
                rebuild();
            } else if (ctrl != CTRL_INFO) {
                onControlClick(ctrl, player);
            }
        }
        // Block all item movement; keep the client in sync.
        setCarried(ItemStack.EMPTY);
        broadcastChanges();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    // ---- Button helpers -------------------------------------------------------------------

    protected static ItemStack button(Item item, String name, String... loreLines) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        if (loreLines.length > 0) {
            List<Component> lore = new ArrayList<>();
            for (String line : loreLines) lore.add(Component.literal(line));
            stack.set(DataComponents.LORE, new ItemLore(lore));
        }
        return stack;
    }

    /** Adds lore to a copy of a real item stack (used to tag content items with their section). */
    protected static ItemStack withLore(ItemStack base, String... loreLines) {
        ItemStack stack = base.copy();
        List<Component> lore = new ArrayList<>();
        for (String line : loreLines) lore.add(Component.literal(line));
        stack.set(DataComponents.LORE, new ItemLore(lore));
        return stack;
    }
}
