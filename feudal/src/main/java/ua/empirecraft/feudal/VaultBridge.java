package ua.empirecraft.feudal;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import static ua.empirecraft.feudal.Model.*;

/** Optional Vault API, resolved through its owning plugin class loader. */
public final class VaultBridge {
    private final FeudalService service;
    public VaultBridge(FeudalService service){this.service=service;}
    @SuppressWarnings({"unchecked","rawtypes"})
    public void exchange(Player player,boolean deposit,long amount) throws Exception {
        var vault=Bukkit.getPluginManager().getPlugin("Vault");
        require(vault!=null && vault.isEnabled(),"Плагін Vault недоступний.");
        Class<?> api=Class.forName("net.milkbowl.vault.economy.Economy",true,vault.getClass().getClassLoader());
        RegisteredServiceProvider<?> registration=Bukkit.getServicesManager().getRegistration((Class)api);
        require(registration!=null,"Vault не знайшов базову економіку. Перевірте EssentialsX.");
        Object economy=registration.getProvider();
        int precision=(Integer)api.getMethod("fractionalDigits").invoke(economy);
        require(precision<0 || precision>=2 || amount % (precision==0?100:10)==0,"Базова економіка не підтримує таку точність суми.");
        var actor=new FeudalService.Actor(player.getUniqueId().toString(),false);
        String id=service.prepareVault(actor,deposit?"IN":"OUT",amount);
        // If the provider throws or the process dies here, PREPARED remains for manual reconciliation.
        Object response;
        try {
            response=api.getMethod(deposit?"withdrawPlayer":"depositPlayer",OfflinePlayer.class,double.class).invoke(economy,player,amount/100.0);
        } catch(Exception ex) {
            throw new IllegalStateException("Статус переказу невідомий. Адміністратор має перевірити операцію "+id+"; повторного списання не буде.",ex);
        }
        boolean success=(Boolean)response.getClass().getMethod("transactionSuccess").invoke(response);
        service.finishVault(actor,id,success);
        require(success,"Базова економіка відхилила переказ. Баланс плагіна не змінено.");
    }
}
