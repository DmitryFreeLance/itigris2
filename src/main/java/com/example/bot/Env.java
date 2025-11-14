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
        int priceYearRubKopeks,
        int priceMonthRubKopeks,
        int subscriptionDurationDays,
        String dbPath,
        ZoneId zone
) {
    public static Env load() {
        String botToken = getenvOrDefault("BOT_TOKEN", "123456:TEST_TOKEN_FROM_BOTFATHER");
        String botUsername = getenvOrDefault("BOT_USERNAME", "SoberiOchki_bot");
        String admins = getenvOrDefault("ADMIN_IDS", "726773708");
        String providerToken = getenvOrDefault("PROVIDER_TOKEN", "381764678:TEST:YourYooKassaProviderToken");

        // 2 900 ₽ за год (по умолчанию)
        int priceYearRubKopeks = Integer.parseInt(getenvOrDefault("PRICE_RUB", "290000"));
        // 200 ₽ за месяц (по умолчанию)
        int priceMonthRubKopeks = Integer.parseInt(getenvOrDefault("PRICE_MONTH_RUB", "20000"));

        int subscriptionDurationDays = Integer.parseInt(getenvOrDefault("SUBSCRIPTION_DURATION_DAYS", "365"));
        String dbPath = getenvOrDefault("DB_PATH", "/app/bot.db");
        String tz = getenvOrDefault("TIMEZONE", "Asia/Yekaterinburg");

        Set<Long> adminIds = Arrays.stream(admins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Long::parseLong)
                .collect(Collectors.toSet());

        return new Env(
                botToken,
                botUsername,
                adminIds,
                providerToken,
                priceYearRubKopeks,
                priceMonthRubKopeks,
                subscriptionDurationDays,
                dbPath,
                ZoneId.of(tz)
        );
    }

    private static String getenvOrDefault(String key, String def) {
        String v = System.getenv(key);
        return (v == null || v.isBlank()) ? def : v;
    }
}