package ua.empirecraft.townyforge;

import java.math.BigDecimal;
import java.util.Map;
import ua.empirecraft.townyforge.TownySnapshot.Source;

final class TownyEconomy {
    private final Source source;

    TownyEconomy(Source source) {
        this.source = source;
    }

    static String account(String type, Map<String, String> record) {
        return type + ":" + record.get("uuid");
    }

    boolean enabled() {
        return Boolean.parseBoolean(source.settings().get("economy.using_economy"));
    }

    BigDecimal configured(String key) {
        return new BigDecimal(source.settings().getOrDefault("economy." + key, "0"));
    }

    BigDecimal balance(String account) {
        return new BigDecimal(source.balances().getOrDefault(account, "0"));
    }

    static BigDecimal payment(String text) {
        if (!text.matches("[0-9]{1,12}(?:\\.[0-9]{1,2})?")) throw new TownyProblem("Вкажіть суму з точністю до копійок.");
        BigDecimal amount = new BigDecimal(text);
        if (amount.signum() <= 0) throw new TownyProblem("Сума має бути більшою за нуль.");
        return amount;
    }

    void transfer(String from, String to, BigDecimal amount) {
        if (amount.signum() < 0) throw new TownyProblem("Від’ємна сума.");
        if (amount.signum() == 0 || from.equals(to)) return;
        if (!enabled()) throw new TownyProblem("Економіку вимкнено в конфігурації Towny.");
        BigDecimal remain = balance(from).subtract(amount);
        if (remain.signum() < 0) throw new TownyProblem("Недостатньо коштів.");
        source.balances().put(from, remain.toPlainString());
        if (!to.isEmpty()) source.balances().put(to, balance(to).add(amount).toPlainString());
    }

    void fee(String from, BigDecimal amount) {
        if (!enabled() || amount.signum() <= 0) return;
        transfer(from, serverAccount(), amount);
    }

    void assessFee(String from, BigDecimal amount) {
        String destination = serverAccount();
        if (destination.equals(from)) return;
        source.balances().put(from, balance(from).subtract(amount).toPlainString());
        if (!destination.isEmpty()) source.balances().put(destination, balance(destination).add(amount).toPlainString());
    }

    private String serverAccount() {
        return Boolean.parseBoolean(source.settings().get("economy.closed_economy.enabled"))
                ? "resident:" + source.settings().get("economy.closed_economy.server_account_uuid") : "";
    }

    void bank(String type, String player, String organization, BigDecimal amount, boolean withdraw) {
        if (withdraw && !Boolean.parseBoolean(source.settings().get("economy.banks." + type + "_allow_withdrawals"))) {
            throw new TownyProblem("Зняття коштів з цього банку вимкнено.");
        }
        BigDecimal min = configured("banks." + type + (withdraw ? "_min_withdraw" : "_min_deposit"));
        if (amount.compareTo(min) < 0) throw new TownyProblem("Сума нижча за встановлений мінімум.");
        BigDecimal cap = configured("banks." + type + "_bank_cap");
        if (!withdraw && cap.signum() > 0 && balance(organization).add(amount).compareTo(cap) > 0) {
            throw new TownyProblem("Перевищено ліміт банку.");
        }
        transfer(withdraw ? organization : player, withdraw ? player : organization, amount);
    }
}
