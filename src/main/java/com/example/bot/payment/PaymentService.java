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

    // Годовая подписка 2 900 ₽
    public void sendYearInvoice(long chatId) {
        List<LabeledPrice> prices = new ArrayList<>();
        prices.add(new LabeledPrice("Годовая подписка", env.priceYearRubKopeks()));

        SendInvoice inv = SendInvoice.builder()
                .chatId(Long.toString(chatId))
                .title("💳 Подписка на 1 год")
                .description("Подписка на вечные очки: первый платёж 3900 ₽ за год, далее 390 ₽ в месяц до конца срока.")
                .payload("subscribe_year_1")
                .providerToken(env.providerToken())
                .currency("RUB")
                .prices(prices)
                .startParameter("subscribe_year")
                .needEmail(false)
                .needName(false)
                .isFlexible(false)
                .build();
        try {
            bot.execute(inv);
        } catch (TelegramApiException e) {
            log.error("sendYearInvoice failed", e);
            try {
                bot.execute(SendMessage.builder()
                        .chatId(Long.toString(chatId))
                        .text("⚠️ Ошибка при создании счёта на годовую подписку. Попробуйте позже.")
                        .build());
            } catch (TelegramApiException ignored) {}
        }
    }

    public void sendMonthInvoice(long chatId) {
        if (!db.isSubscriptionActive(chatId)) {
            try {
                bot.execute(SendMessage.builder()
                        .chatId(Long.toString(chatId))
                        .text("⚠️ Месячная оплата 390 ₽ доступна только при активной годовой подписке за 3900 ₽.\n" +
                                "Сначала оформите годовую подписку.")
                        .build());
            } catch (TelegramApiException ignored) {}
            return;
        }

        List<LabeledPrice> prices = new ArrayList<>();
        prices.add(new LabeledPrice("Месячная оплата", env.priceMonthRubKopeks()));

        SendInvoice inv = SendInvoice.builder()
                .chatId(Long.toString(chatId))
                .title("💳 Месячная оплата 390 ₽")
                .description("Оплата месяца обслуживания в рамках вашей годовой подписки на вечные очки.")
                .payload("subscribe_month_1")
                .providerToken(env.providerToken())
                .currency("RUB")
                .prices(prices)
                .startParameter("subscribe_month")
                .needEmail(false)
                .needName(false)
                .isFlexible(false)
                .build();
        try {
            bot.execute(inv);
        } catch (TelegramApiException e) {
            log.error("sendMonthInvoice failed", e);
            try {
                bot.execute(SendMessage.builder()
                        .chatId(Long.toString(chatId))
                        .text("⚠️ Ошибка при создании счёта на месяц. Попробуйте позже.")
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
        try {
            bot.execute(ans);
        } catch (TelegramApiException ignored) {}
    }
}