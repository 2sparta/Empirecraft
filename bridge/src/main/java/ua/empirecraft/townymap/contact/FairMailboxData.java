package ua.empirecraft.townymap.contact;

import net.minecraft.nbt.CompoundTag;

/** Stored inside Contact's own mailbox capability, together with the delivered items. */
public interface FairMailboxData {
    CompoundTag empirecraft$fairMail();
}
