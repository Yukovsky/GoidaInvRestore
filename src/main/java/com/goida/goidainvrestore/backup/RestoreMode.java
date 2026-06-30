package com.goida.goidainvrestore.backup;

/** How a backup is applied to a live player. */
public enum RestoreMode {
    /** Clear the player's current items, then write the snapshot back exactly (incl. XP). */
    OVERWRITE,
    /** Add every snapshot item to the player without clearing; overflow is dropped. */
    GIVE_ALL
}
