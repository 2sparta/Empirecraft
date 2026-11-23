package ua.empirecraft.townyforge;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import ua.empirecraft.townyforge.TownySnapshot.Source;

final class TownyDaily {
    static final String LAST_CYCLE = "runtime.lastDailyCycle";
    private static final String TAX = "daily_taxes.";
    private static final Pattern TIME = Pattern.compile("(\\d+)([dhms]?)");
    private final Source state;
    private final TownyEconomy bank;

    TownyDaily(Source state) {
        this.state = state;
        bank = new TownyEconomy(state);
    }

    static long cycle(Map<String, String> settings, long epochSeconds) {
        long interval = Math.max(60, Math.min(86400, seconds(settings.getOrDefault("day_timer.day_interval", "1d"))));
        long offset = Math.min(interval, seconds(settings.getOrDefault("day_timer.new_day_time", "24h")));
        return Math.floorDiv(epochSeconds - offset, interval);
    }

    private static long seconds(String value) {
        var match = TIME.matcher(value.strip().toLowerCase(Locale.ROOT));
        if (!match.matches()) return 86400;
        long unit = switch (match.group(2)) {
            case "d" -> 86400;
            case "h" -> 3600;
            case "m" -> 60;
            default -> 1;
        };
        return Math.min(86400, Long.parseLong(match.group(1))) * unit;
    }

    void run() {
        if (!bank.enabled() || !enabled(TAX + "enabled")) return;
        for (var entry : state.towns().entrySet()) collectResidentTax(entry.getKey(), entry.getValue());
        for (var plot : state.claims()) {
            var resident = state.residents().get(plot.data().getOrDefault("resident", ""));
            if (resident == null || "false".equalsIgnoreCase(plot.data().get("taxed"))) continue;
            var town = state.towns().get(plot.town());
            String type = plot.data().getOrDefault("type", "default").toLowerCase(Locale.ROOT);
            BigDecimal amount = decimal(state.settings(), "plots." + type + ".tax", "0");
            if (amount.signum() == 0) {
                String key = switch (type) {
                    case "shop" -> "commercialPlotTax";
                    case "embassy" -> "embassyPlotTax";
                    default -> "plotTax";
                };
                amount = decimal(town, key, town.getOrDefault("plotTax", "0"));
            }
            if (!collect(TownyEconomy.account("resident", resident), TownyEconomy.account("town", town),
                    amount, enabled(TAX + "allow_negative_plot_tax"))) {
                TownyGovernance.resetPlot(plot);
                if (TownySnapshot.flag(state.settings(), "town.default_taxes.does_non-payment_place_plot_for_sale")) {
                    plot.data().put("price", town.getOrDefault("plotPrice", "0"));
                }
            }
        }
        for (String name : List.copyOf(state.towns().keySet())) collectNationTax(name);
        for (String name : List.copyOf(state.towns().keySet())) {
            var town = state.towns().get(name);
            if ("false".equalsIgnoreCase(town.get("hasUpkeep"))) continue;
            String account = TownyEconomy.account("town", town);
            long members = state.residents().values().stream().filter(r -> name.equals(r.get("town"))).count();
            long plots = state.claims().stream().filter(p -> name.equals(p.town())).count();
            BigDecimal amount = cost("town", members, plots, enabled(TAX + "town_plotbased_upkeep"),
                    enabled(TAX + "town_plotbased_upkeep_affected_by_town_level_modifier"));
            amount = floorCap(amount, TAX + "town_plotbased_upkeep_minimum_amount", TAX + "town_plotbased_upkeep_maximum_amount",
                    enabled(TAX + "town_plotbased_upkeep"));
            long outposts = state.claims().stream().filter(p -> name.equals(p.town())
                    && TownySnapshot.flag(p.data(), "outpost")).count();
            amount = amount.add(bank.configured(TAX + "per_outpost_cost").multiply(BigDecimal.valueOf(outposts)));
            if (!payUpkeep(account, amount, town, members, plots, outposts)) {
                leaveNation(name);
                TownyGovernance.removeTown(state, name);
                state.balances().remove(account);
            }
        }
        for (String name : List.copyOf(state.nations().keySet())) {
            var nation = state.nations().get(name);
            if ("false".equalsIgnoreCase(nation.get("hasUpkeep"))) continue;
            String account = TownyEconomy.account("nation", nation);
            var towns = state.towns().entrySet().stream().filter(t -> name.equals(t.getValue().get("nation")))
                    .map(Map.Entry::getKey).toList();
            long members = state.residents().values().stream().filter(r -> towns.contains(r.get("town"))).count();
            boolean byPlot = enabled(TAX + "nation_perplot_upkeep"), byTown = enabled(TAX + "nation_pertown_upkeep");
            long count = byPlot ? state.claims().stream().filter(p -> towns.contains(p.town())).count() : towns.size();
            BigDecimal amount = cost("nation", members, count, byPlot || byTown,
                    !byPlot && enabled(TAX + "nation_pertown_upkeep_affected_by_nation_level_modifier"));
            if (!payUpkeep(account, amount, null, members, 0, 0)) {
                TownyGovernance.removeNation(state, name);
                state.balances().remove(account);
            }
        }
    }

    private void collectResidentTax(String name, Map<String, String> town) {
        String target = TownyEconomy.account("town", town);
        for (var entry : state.residents().entrySet()) {
            var resident = entry.getValue();
            boolean mayor = entry.getKey().equals(town.get("mayor"));
            if (!name.equals(resident.get("town")) || mayor && !enabled(TAX + "do_mayors_pay_town_tax")) continue;
            String account = TownyEconomy.account("resident", resident);
            if (!collect(account, target, tax(town, account, "town"), enabled(TAX + "allow_negative_town_tax")) && !mayor) {
                TownyGovernance.removeResident(state, entry.getKey(), resident);
            }
        }
    }

    private void collectNationTax(String name) {
        var town = state.towns().get(name);
        var nation = state.nations().get(town.getOrDefault("nation", ""));
        if (nation == null || name.equals(nation.get("capital")) && !enabled(TAX + "do_nation_capitals_pay_nation_tax")) return;
        String account = TownyEconomy.account("town", town);
        if (!collect(account, TownyEconomy.account("nation", nation), tax(nation, account, "nation"),
                enabled(TAX + "allow_negative_nation_tax"))) leaveNation(name);
    }

    private BigDecimal tax(Map<String, String> organization, String account, String type) {
        BigDecimal value = decimal(organization, "taxes", "0");
        if (!TownySnapshot.flag(organization, "taxpercent")) return value;
        BigDecimal amount = bank.balance(account).max(BigDecimal.ZERO).multiply(value.max(BigDecimal.ZERO)).movePointLeft(2);
        BigDecimal cap = decimal(organization, "maxPercentTaxAmount",
                bank.configured(TAX + "max_" + type + "_tax_percent_amount").toPlainString());
        if (cap.signum() > 0) amount = amount.min(cap);
        return amount.setScale(2, RoundingMode.DOWN);
    }

    private boolean collect(String from, String to, BigDecimal amount, boolean subsidy) {
        if (amount.signum() < 0) {
            if (subsidy && bank.balance(to).compareTo(amount.negate()) >= 0) bank.transfer(to, from, amount.negate());
            return true;
        }
        if (amount.signum() > 0 && bank.balance(from).compareTo(amount) < 0) return false;
        bank.transfer(from, to, amount);
        return true;
    }

    private boolean payUpkeep(String account, BigDecimal amount, Map<String, String> town, long members, long plots, long outposts) {
        if (amount.signum() <= 0) return true;
        if (bank.balance(account).compareTo(amount) >= 0) {
            bank.fee(account, amount);
            return true;
        }
        if (town == null || !enabled("bankruptcy.enabled")) return false;
        BigDecimal remaining = bank.balance(account).subtract(amount);
        BigDecimal cap = debtLimit(members, plots, outposts);
        int days = Integer.parseInt(town.getOrDefault("forgeDebtDays", "0")) + 1;
        town.put("forgeDebtDays", Integer.toString(days));
        town.put("bankrupt", "true");
        boolean delete = enabled("bankruptcy.upkeep.delete_towns_that_reach_debt_cap");
        if (delete && enabled("bankruptcy.debt_cap.debt_cap_uses_fixed_days")
                && days >= bank.configured("bankruptcy.debt_cap.allowed_days").intValue()) return false;
        if (remaining.negate().compareTo(cap) <= 0) {
            bank.assessFee(account, amount);
            return true;
        }
        return !delete;
    }

    private BigDecimal debtLimit(long members, long plots, long outposts) {
        BigDecimal override = bank.configured("bankruptcy.debt_cap.override");
        if (override.signum() > 0) {
            if (enabled("bankruptcy.debt_cap.debt_cap_uses_town_levels")) {
                override = override.multiply(levelModifier("town", members, "debtCapModifier"));
            }
            return override;
        }
        BigDecimal cap = bank.configured("new_expand.price_new_town")
                .add(bank.configured("new_expand.price_claim_townblock").multiply(BigDecimal.valueOf(Math.max(0, plots - outposts - 1))))
                .add(bank.configured("new_expand.price_outpost").multiply(BigDecimal.valueOf(outposts)));
        BigDecimal max = bank.configured("bankruptcy.debt_cap.maximum");
        return max.signum() > 0 ? cap.min(max) : cap;
    }

    private void leaveNation(String town) {
        String nation = state.towns().get(town).getOrDefault("nation", "");
        if (nation.isEmpty()) return;
        var record = state.nations().get(nation);
        TownyGovernance.assignNation(state, town, "");
        if (town.equals(record.get("capital"))) {
            String next = state.towns().entrySet().stream().filter(t -> nation.equals(t.getValue().get("nation")))
                    .map(Map.Entry::getKey).sorted().findFirst().orElse(null);
            if (next != null) record.put("capital", next);
            else {
                TownyGovernance.removeNation(state, nation);
                state.balances().remove(TownyEconomy.account("nation", record));
            }
        }
    }

    private BigDecimal cost(String type, long members, long count, boolean byCount, boolean modified) {
        BigDecimal base = bank.configured(TAX + "price_" + type + "_upkeep");
        BigDecimal modifier = !byCount || modified ? levelModifier(type, members, "upkeepModifier") : BigDecimal.ONE;
        return base.multiply(modifier).multiply(BigDecimal.valueOf(byCount ? count : 1)).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal levelModifier(String type, long members, String field) {
        BigDecimal modifier = BigDecimal.ONE;
        for (int i = 0; ; i++) {
            String prefix = "levels." + type + "." + i + ".";
            String minimum = state.settings().get(prefix + "numResidents");
            if (minimum == null) break;
            if (members >= Long.parseLong(minimum)) modifier = decimal(state.settings(), prefix + field, "1");
        }
        return modifier;
    }

    private BigDecimal floorCap(BigDecimal amount, String min, String max, boolean apply) {
        if (!apply) return amount;
        BigDecimal cap = bank.configured(max);
        amount = amount.max(bank.configured(min));
        return cap.signum() > 0 ? amount.min(cap) : amount;
    }

    private boolean enabled(String key) {
        return Boolean.parseBoolean(state.settings().get("economy." + key));
    }

    private static BigDecimal decimal(Map<String, String> data, String key, String fallback) {
        return new BigDecimal(data.getOrDefault(key, fallback));
    }
}
