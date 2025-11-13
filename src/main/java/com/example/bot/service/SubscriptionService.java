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
        try { runReminders(); } catch (Exception e) { log.error("Reminder task failed", e); }
    }

    private void runReminders() {
        List<Long> in3days = db.findSubsEndingInDays(3);
        for (Long chatId : in3days) {
            SendMessage m = SendMessage.builder()
                    .chatId(chatId.toString())
                    .text("⏰ Напоминание: до окончания вашей подписки осталось 3 дня.\nЧтобы не потерять доступ — продлите её заранее 💳")
                    .replyMarkup(Keyboards.buyButton())
                    .build();
            try { bot.execute(m); } catch (TelegramApiException ignored) {}
        }

        List<Long> today = db.findSubsEndingToday();
        for (Long chatId : today) {
            db.cancelSubscription(chatId);
            SendMessage m = SendMessage.builder()
                    .chatId(chatId.toString())
                    .text("⚠️ Ваша подписка закончилась.\nЧтобы восстановить доступ — оформите подписку снова 💳")
                    .replyMarkup(Keyboards.buyButton())
                    .build();
            try { bot.execute(m); } catch (TelegramApiException ignored) {}
        }
    }

    public void showMySubscription(long chatId) {
        boolean active = db.isSubscriptionActive(chatId);
        String text;
        if (active) {
            LocalDate end = db.getSubscriptionEnd(chatId);
            String pretty = end != null ? DateUtils.formatRu(end) : "—";
            text = "📅 Ваша подписка активна до: " + pretty;
        } else {
            text = "ℹ️ У вас нет активной подписки.\nНажмите «💳 Оформить подписку», чтобы её подключить.";
        }

        try {
            bot.execute(
                    org.telegram.telegrambots.meta.api.methods.send.SendMessage.builder()
                            .chatId(Long.toString(chatId))
                            .text(text)
                            .replyMarkup(com.example.bot.Keyboards.backToMenu())
                            .build()
            );
        } catch (TelegramApiException e) {
            log.warn("Send failed", e);
        }
    }

    public void activateSubscription(long chatId) {
        LocalDate end = LocalDate.now().plusDays(env.subscriptionDurationDays());
        db.setSubscription(chatId, true, end);
        send(chatId, "✅ Подписка успешно активирована!\n📅 Дата окончания: " + DateUtils.formatRu(end));
    }

    public void cancelSubscription(long chatId) {
        db.cancelSubscription(chatId);
    }

    private void send(long chatId, String text) {
        try { bot.execute(SendMessage.builder().chatId(Long.toString(chatId)).text(text).build()); }
        catch (TelegramApiException e) { log.warn("Send failed", e); }
    }
}
