package com.example.bot;

import java.time.ZoneId;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public record Env(
        String botToken,
        String botUsername,
        Set<Long> adminIds,
        String providerToken,
        int priceRubKopeks,
        int subscriptionDurationDays,
        String dbPath,
        ZoneId zone
) {
    public static Env load() {
        String botToken = getenvOrDefault("BOT_TOKEN", "");
        String botUsername = getenvOrDefault("BOT_USERNAME", "SoberiOchki_bot");
        String admins = getenvOrDefault("ADMIN_IDS", "726773708");
        String providerToken = getenvOrDefault("PROVIDER_TOKEN", "381764678:TEST:YourYooKassaProviderToken");
        int priceRubKopeks = Integer.parseInt(getenvOrDefault("PRICE_RUB", "19900"));
        int subscriptionDurationDays = Integer.parseInt(getenvOrDefault("SUBSCRIPTION_DURATION_DAYS", "30"));
        String dbPath = getenvOrDefault("DB_PATH", "./data/bot.db");
        String tz = getenvOrDefault("TIMEZONE", "Asia/Yekaterinburg");

        Set<Long> adminIds = Arrays.stream(admins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Long::parseLong)
                .collect(Collectors.toSet());

        return new Env(botToken, botUsername, adminIds, providerToken, priceRubKopeks, subscriptionDurationDays, dbPath, ZoneId.of(tz));
    }

    private static String getenvOrDefault(String key, String def) {
        String v = System.getenv(key);
        return v == null || v.isBlank() ? def : v;
    }
}
