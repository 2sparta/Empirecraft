package ua.empirecraft.feudal;
import org.bukkit.entity.Player;
/** Vault money is no longer part of the city-currency ledger. */
public final class VaultBridge {
    public VaultBridge(FeudalService service){}
    public void exchange(Player player,boolean deposit,long amount){throw new IllegalArgumentException(LocalText.tr("Vault/BASE вимкнено. Використовуйте валюту міста."));}
}
