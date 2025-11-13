package com.example.bot.payment;

import com.example.bot.Env;
import com.example.bot.db.Database;
import com.example.bot.TelegramBot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.api.methods.AnswerPreCheckoutQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendInvoice;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.payments.LabeledPrice;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.ArrayList;
import java.util.List;

public class PaymentService {
    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final Env env;
    private final Database db;
    private final TelegramBot bot;

    public PaymentService(Env env, Database db, TelegramBot bot) {
        this.env = env;
        this.db = db;
        this.bot = bot;
    }

    public void sendInvoice(long chatId) {
        List<LabeledPrice> prices = new ArrayList<>();
        prices.add(new LabeledPrice("Месяц подписки", env.priceRubKopeks()));
        SendInvoice inv = SendInvoice.builder()
                .chatId(Long.toString(chatId))
                .title("💳 Подписка на 1 месяц")
                .description("🔓 Доступ к эксклюзивному контенту на 30 дней")
                .payload("subscribe_month_1")
                .providerToken(env.providerToken())
                .currency("RUB")
                .prices(prices)
                .startParameter("subscribe")
                .needEmail(false)
                .needName(false)
                .isFlexible(false)
                .build();
        try {
            bot.execute(inv);
        } catch (TelegramApiException e) {
            log.error("sendInvoice failed", e);
            try {
                bot.execute(SendMessage.builder().chatId(Long.toString(chatId)).text("⚠️ Произошла ошибка при создании счёта.\nПопробуйте ещё раз чуть позже.")
                        .build());
            } catch (TelegramApiException ignored) {}
        }
    }

    public void answerPreCheckout(String preCheckoutQueryId, boolean ok, String error) {
        AnswerPreCheckoutQuery ans = AnswerPreCheckoutQuery.builder()
                .preCheckoutQueryId(preCheckoutQueryId)
                .ok(ok)
                .errorMessage(error)
                .build();
        try { bot.execute(ans); } catch (TelegramApiException ignored) {}
    }
}
