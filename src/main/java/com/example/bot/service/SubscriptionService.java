package com.example.bot.service;

import com.example.bot.DateUtils;
import com.example.bot.Env;
import com.example.bot.Keyboards;
import com.example.bot.TelegramBot;
import com.example.bot.db.Database;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class SubscriptionService {
    private static final Logger log = LoggerFactory.getLogger(SubscriptionService.class);

    private final Env env;
    private final Database db;
    private final TelegramBot bot;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public SubscriptionService(Env env, Database db, TelegramBot bot) {
        this.env = env;
        this.db = db;
        this.bot = bot;
    }

    public void startSchedulers() {
        scheduler.scheduleAtFixedRate(this::runRemindersSafe, 5, 60, TimeUnit.MINUTES);
    }

    private void runRemindersSafe() {
        try {
            runReminders();
        } catch (Exception e) {
            log.error("Reminder task failed", e);
        }
    }

    private void runReminders() {
        // 1) МЕСЯЧНАЯ ПОДПИСКА (внутри годовой)

        // за 3 дня до конца месяца
        List<Long> monthIn3days = db.findMonthSubsEndingInDays(3);
        for (Long chatId : monthIn3days) {
            SendMessage m = SendMessage.builder()
                    .chatId(chatId.toString())
                    .text("⏰ Через 3 дня заканчивается оплаченный месяц вашей подписки.\n" +
                            "Чтобы сохранить обслуживание за 200 ₽ в месяц, оплатите следующий месяц.")
                    .replyMarkup(Keyboards.buyMonthButton())
                    .build();
            try {
                bot.execute(m);
            } catch (TelegramApiException ignored) {}
        }

        // в день окончания месяца
        List<Long> monthToday = db.findMonthSubsEndingToday();
        for (Long chatId : monthToday) {
            db.setMonthly(chatId, false, LocalDate.now());
            SendMessage m = SendMessage.builder()
                    .chatId(chatId.toString())
                    .text("⚠️ Срок вашей месячной оплаты истёк.\n" +
                            "Оплатите 200 ₽, чтобы продолжить обслуживание в рамках годовой подписки.")
                    .replyMarkup(Keyboards.buyMonthButton())
                    .build();
            try {
                bot.execute(m);
            } catch (TelegramApiException ignored) {}
        }

        // 2) ГОДОВАЯ ПОДПИСКА

        // за 3 дня до конца года
        List<Long> yearIn3days = db.findYearSubsEndingInDays(3);
        for (Long chatId : yearIn3days) {
            SendMessage m = SendMessage.builder()
                    .chatId(chatId.toString())
                    .text("⏰ Через 3 дня заканчивается ваша годовая подписка на вечные очки.\n" +
                            "Продлите её, чтобы сохранить все преимущества.")
                    .replyMarkup(Keyboards.buyYearButton())
                    .build();
            try {
                bot.execute(m);
            } catch (TelegramApiException ignored) {}
        }

        // в день окончания годовой
        List<Long> yearToday = db.findYearSubsEndingToday();
        for (Long chatId : yearToday) {
            db.cancelSubscriptionHard(chatId);
            SendMessage m = SendMessage.builder()
                    .chatId(chatId.toString())
                    .text("⚠️ Ваша годовая подписка закончилась.\n" +
                            "Чтобы продолжить пользоваться сервисом, оформите новый год за 2 900 ₽.")
                    .replyMarkup(Keyboards.buyYearButton())
                    .build();
            try {
                bot.execute(m);
            } catch (TelegramApiException ignored) {}
        }
    }

    // --- Публичные методы ---

    public void showMySubscription(long chatId) {
        boolean yearActive = db.isSubscriptionActive(chatId);
        boolean monthActive = db.isMonthlyActive(chatId);
        LocalDate yearEnd = db.getSubscriptionEnd(chatId);
        LocalDate monthEnd = db.getMonthlyEnd(chatId);

        StringBuilder sb = new StringBuilder();
        if (yearActive && yearEnd != null) {
            sb.append("📅 Ваша годовая подписка активна до: ")
                    .append(DateUtils.formatRu(yearEnd))
                    .append("\n");
        } else {
            sb.append("ℹ️ У вас нет активной годовой подписки.\n");
        }

        if (yearActive) {
            if (monthActive && monthEnd != null) {
                sb.append("\n📆 Месячная оплата активна до: ")
                        .append(DateUtils.formatRu(monthEnd));
            } else {
                sb.append("\n⚠️ Месячная оплата сейчас не активна.\n")
                        .append("Оплатите 200 ₽, чтобы пользоваться обслуживанием в рамках года.");
            }
        } else {
            sb.append("\nДля использования сервиса сначала оформите годовую подписку за 2 900 ₽.");
        }

        try {
            bot.execute(SendMessage.builder()
                    .chatId(Long.toString(chatId))
                    .text(sb.toString())
                    .replyMarkup(Keyboards.backToMenu())
                    .build());
        } catch (TelegramApiException e) {
            log.warn("Send failed", e);
        }
    }

    // Активация годовой: первый платёж 2 900 ₽
    public void activateYearSubscription(long chatId) {
        LocalDate yearEnd = LocalDate.now().plusYears(1);
        LocalDate monthEnd = LocalDate.now().plusMonths(1);

        db.setSubscription(chatId, true, yearEnd);

        if (monthEnd.isAfter(yearEnd)) {
            monthEnd = yearEnd;
        }
        db.setMonthly(chatId, true, monthEnd);

        send(chatId,
                "✅ Подписка активирована на 1 год.\n" +
                        "📅 Годовая активна до: " + DateUtils.formatRu(yearEnd) + "\n" +
                        "📆 Месяц оплачен до: " + DateUtils.formatRu(monthEnd));
    }

    // Продление месяца: платёж 200 ₽
    public void extendMonthly(long chatId) {
        LocalDate now = LocalDate.now();
        LocalDate yearEnd = db.getSubscriptionEnd(chatId);

        if (yearEnd == null || yearEnd.isBefore(now)) {
            send(chatId, "⚠️ Сначала нужно оформить годовую подписку за 2 900 ₽.");
            return;
        }

        LocalDate currentMonthEnd = db.getMonthlyEnd(chatId);
        LocalDate base = (currentMonthEnd != null && !currentMonthEnd.isBefore(now))
                ? currentMonthEnd
                : now;

        LocalDate newMonthEnd = base.plusMonths(1);
        if (newMonthEnd.isAfter(yearEnd)) {
            newMonthEnd = yearEnd;
        }

        db.setMonthly(chatId, true, newMonthEnd);

        send(chatId,
                "✅ Месячная оплата обновлена.\n" +
                        "📆 Месяц оплачен до: " + DateUtils.formatRu(newMonthEnd));
    }

    public void cancelSubscription(long chatId) {
        db.cancelSubscriptionHard(chatId);
    }

    private void send(long chatId, String text) {
        try {
            bot.execute(SendMessage.builder()
                    .chatId(Long.toString(chatId))
                    .text(text)
                    .build());
        } catch (TelegramApiException e) {
            log.warn("Send failed", e);
        }
    }
}