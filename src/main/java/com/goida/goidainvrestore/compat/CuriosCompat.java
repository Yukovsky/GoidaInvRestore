package com.goida.goidainvrestore.compat;

import com.goida.goidainvrestore.GoidaInvRestore;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.Optional;

/**
 * Reflective integration with Curios (NeoForge 1.21.1). Uses
 * {@code CuriosApi.getCuriosInventory(LivingEntity)} → {@code ICuriosItemHandler#getEquippedCurios()}
 * (a flat modifiable view of every equipped curio slot) so the mod compiles and runs with or
 * without Curios installed.
 */
public final class CuriosCompat {

    private static final boolean LOADED;
    private static Method getCuriosInventory; // static (LivingEntity) -> Optional<ICuriosItemHandler>
    private static Method getEquippedCurios;  // () -> IItemHandlerModifiable (flat view of all slots)

    static {
        boolean ok = false;
        try {
            if (ModList.get().isLoaded("curios")) {
                Class<?> api = Class.forName("top.theillusivec4.curios.api.CuriosApi");
                getCuriosInventory = api.getMethod("getCuriosInventory", LivingEntity.class);
                Class<?> handler = Class.forName("top.theillusivec4.curios.api.type.capability.ICuriosItemHandler");
                getEquippedCurios = handler.getMethod("getEquippedCurios");
                ok = true;
            }
        } catch (Throwable t) {
            GoidaInvRestore.LOGGER.warn("Curios is present but its API could not be reflected; "
                    + "Curios slots will NOT be backed up/restored.", t);
            ok = false;
        }
        LOADED = ok;
    }

    private CuriosCompat() {}

    public static boolean isLoaded() {
        return LOADED;
    }

    /** Flat modifiable view of all equipped curio slots, or {@code null}. */
    @Nullable
    private static IItemHandlerModifiable equipped(ServerPlayer player) throws Exception {
        Optional<?> opt = (Optional<?>) getCuriosInventory.invoke(null, player);
        if (opt.isEmpty()) {
            return null;
        }
        return (IItemHandlerModifiable) getEquippedCurios.invoke(opt.get());
    }

    /** Number of equipped curio slots (0 if Curios absent or unavailable). */
    public static int slotCount(ServerPlayer player) {
        if (!LOADED) return 0;
        try {
            IItemHandlerModifiable h = equipped(player);
            return h == null ? 0 : h.getSlots();
        } catch (Throwable t) {
            GoidaInvRestore.LOGGER.error("Curios slotCount failed for {}", player.getGameProfile().getName(), t);
            return 0;
        }
    }

    public static ItemStack getStack(ServerPlayer player, int index) {
        if (!LOADED) return ItemStack.EMPTY;
        try {
            IItemHandlerModifiable h = equipped(player);
            if (h == null || index < 0 || index >= h.getSlots()) return ItemStack.EMPTY;
            return h.getStackInSlot(index);
        } catch (Throwable t) {
            GoidaInvRestore.LOGGER.error("Curios getStack failed for {}", player.getGameProfile().getName(), t);
            return ItemStack.EMPTY;
        }
    }

    public static void setStack(ServerPlayer player, int index, ItemStack stack) {
        if (!LOADED) return;
        try {
            IItemHandlerModifiable h = equipped(player);
            if (h == null || index < 0 || index >= h.getSlots()) return;
            h.setStackInSlot(index, stack);
        } catch (Throwable t) {
            GoidaInvRestore.LOGGER.error("Curios setStack failed for {}", player.getGameProfile().getName(), t);
        }
    }
}
