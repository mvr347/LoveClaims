package me.lovelace.loveclaims.util;

import dev.lovelace.lovecore.api.LoveCore;
import dev.lovelace.lovecore.api.economy.Denomination;
import dev.lovelace.lovecore.api.economy.LoveEconomy;
import org.bukkit.Bukkit;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Единый стандарт форматирования денег в виде глифов монет ItemsAdder (как в BankerGui LoveShops).
 * Любая сумма разбивается по номиналам LoveEconomy:
 * %img_diamond_coin% x2  %img_gold_coin% x1  %img_iron_coin% x3
 */
public final class CoinFormat {

    private CoinFormat() {}

    public static Optional<LoveEconomy> getEconomy() {
        if (!Bukkit.getPluginManager().isPluginEnabled("LoveCore")) return Optional.empty();
        try {
            return LoveCore.service(LoveEconomy.class);
        } catch (Throwable t) {
            return Optional.empty();
        }
    }

    public static String getCoinGlyph(Denomination den) {
        if (den == null || den.itemId() == null) return "%img_copper_coin%";
        String id = den.itemId();
        int colon = id.indexOf(':');
        String tag = colon >= 0 ? id.substring(colon + 1) : id;
        return "%img_" + tag + "%";
    }

    public static String formatGlyphs(LoveEconomy eco, long amount) {
        if (eco == null) {
            return "%img_copper_coin% x" + Math.max(0, amount);
        }
        List<Denomination> dens = new ArrayList<>(eco.denominations());
        dens.sort(Comparator.comparingLong(Denomination::value).reversed());

        if (amount <= 0) {
            Denomination smallest = dens.isEmpty() ? null : dens.get(dens.size() - 1);
            return (smallest != null ? getCoinGlyph(smallest) : "%img_copper_coin%") + " x0";
        }

        StringBuilder sb = new StringBuilder();
        long remaining = amount;
        for (Denomination den : dens) {
            if (den.value() <= 0) continue;
            long count = remaining / den.value();
            if (count > 0) {
                if (sb.length() > 0) sb.append("  ");
                sb.append(getCoinGlyph(den)).append(" x").append(count);
                remaining %= den.value();
            }
        }

        if (sb.length() == 0) {
            Denomination smallest = dens.isEmpty() ? null : dens.get(dens.size() - 1);
            return (smallest != null ? getCoinGlyph(smallest) : "%img_copper_coin%") + " x0";
        }

        return sb.toString();
    }

    public static String formatGlyphs(long amount) {
        return formatGlyphs(getEconomy().orElse(null), amount);
    }

    public static String formatGlyphs(double amount) {
        return formatGlyphs(Math.round(amount));
    }
}
