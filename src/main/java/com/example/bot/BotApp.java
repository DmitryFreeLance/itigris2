package com.example.bot;

import com.example.bot.db.Database;
import com.example.bot.service.SubscriptionService;
import com.example.bot.service.BroadcastService;
import com.example.bot.payment.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

public class BotApp {
    private static final Logger log = LoggerFactory.getLogger(BotApp.class);

    public static void main(String[] args) {
        try {
            Env env = Env.load();
            Database db = new Database(env.dbPath());
            db.init();

            TelegramBot bot = new TelegramBot(env, db);
            SubscriptionService subscriptionService = new SubscriptionService(env, db, bot);
            BroadcastService broadcastService = new BroadcastService(db, bot);
            PaymentService paymentService = new PaymentService(env, db, bot);

            bot.setServices(subscriptionService, broadcastService, paymentService);

            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botsApi.registerBot(bot);
            log.info("Bot started as @{} (admins: {})", env.botUsername(), env.adminIds());

            subscriptionService.startSchedulers();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
