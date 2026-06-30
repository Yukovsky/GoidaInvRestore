package com.goida.goidainvrestore.compat;

import com.goida.goidainvrestore.GoidaInvRestore;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Reflective integration with CosmeticArmorReworked / its forks. Uses
 * {@code CosArmorAPI.getCAStacks(UUID)} → {@code CAStacksBase#getStackInSlot(int)} /
 * {@code #setStackInSlot(int, ItemStack)} (4 slots: 0=feet, 1=legs, 2=chest, 3=head). The mod is
 * not on a public maven, so everything is accessed reflectively and degrades gracefully when absent.
 */
public final class CosmeticArmorCompat {

    private static final int SLOTS = 4;

    private static final boolean LOADED;
    private static Method getCAStacks;    // static (UUID) -> CAStacksBase
    private static Method setStackInSlot; // (int, ItemStack) -> void
    private static Method getStackInSlot; // (int) -> ItemStack

    static {
        boolean ok = false;
        try {
            if (isAnyLoaded("cosmeticarmorreworked", "cosmeticarmorreworkedforked", "cosmetic_armor_reworked")) {
                Class<?> api = Class.forName("lain.mods.cos.api.CosArmorAPI");
                getCAStacks = api.getMethod("getCAStacks", UUID.class);
                Class<?> base = Class.forName("lain.mods.cos.api.inventory.CAStacksBase");
                setStackInSlot = findMethod(base, "setStackInSlot", 2);
                getStackInSlot = findMethod(base, "getStackInSlot", 1);
                ok = setStackInSlot != null && getStackInSlot != null;
                if (!ok) {
                    GoidaInvRestore.LOGGER.warn("CosmeticArmor present but expected CAStacksBase methods were not found; "
                            + "cosmetic slots will NOT be backed up/restored.");
                }
            }
        } catch (Throwable t) {
            GoidaInvRestore.LOGGER.warn("CosmeticArmor is present but its API could not be reflected; "
                    + "cosmetic slots will NOT be backed up/restored.", t);
            ok = false;
        }
        LOADED = ok;
    }

    private CosmeticArmorCompat() {}

    public static boolean isLoaded() {
        return LOADED;
    }

    /** Number of cosmetic slots (always 4 when loaded). */
    public static int slotCount() {
        return LOADED ? SLOTS : 0;
    }

    public static ItemStack getStack(ServerPlayer player, int index) {
        if (!LOADED || index < 0 || index >= SLOTS) return ItemStack.EMPTY;
        try {
            Object stacks = getCAStacks.invoke(null, player.getUUID());
            if (stacks == null) return ItemStack.EMPTY;
            ItemStack s = (ItemStack) getStackInSlot.invoke(stacks, index);
            return s == null ? ItemStack.EMPTY : s;
        } catch (Throwable t) {
            GoidaInvRestore.LOGGER.error("Cosmetic getStack failed for {}", player.getGameProfile().getName(), t);
            return ItemStack.EMPTY;
        }
    }

    public static void setStack(ServerPlayer player, int index, ItemStack stack) {
        if (!LOADED || index < 0 || index >= SLOTS) return;
        try {
            Object stacks = getCAStacks.invoke(null, player.getUUID());
            if (stacks != null) setStackInSlot.invoke(stacks, index, stack);
        } catch (Throwable t) {
            GoidaInvRestore.LOGGER.error("Cosmetic setStack failed for {}", player.getGameProfile().getName(), t);
        }
    }

    private static boolean isAnyLoaded(String... ids) {
        for (String id : ids) {
            if (ModList.get().isLoaded(id)) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    private static Method findMethod(Class<?> c, String name, int paramCount) {
        for (Method m : c.getMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == paramCount) {
                m.setAccessible(true);
                return m;
            }
        }
        return null;
    }
}
