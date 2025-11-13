package com.example.bot;

import com.example.bot.db.Database;
import com.example.bot.payment.PaymentService;
import com.example.bot.service.BroadcastService;
import com.example.bot.service.SubscriptionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.*;
import org.telegram.telegrambots.meta.api.objects.payments.PreCheckoutQuery;
import org.telegram.telegrambots.meta.api.objects.payments.SuccessfulPayment;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.Comparator;
import java.util.List;

public class TelegramBot extends TelegramLongPollingBot {
    private static final Logger log = LoggerFactory.getLogger(TelegramBot.class);

    private final Env env;
    private final Database db;

    private SubscriptionService subscriptionService;
    private BroadcastService broadcastService;
    private PaymentService paymentService;

    public TelegramBot(Env env, Database db) {
        super(env.botToken());
        this.env = env;
        this.db = db;
    }

    public void setServices(SubscriptionService subscriptionService, BroadcastService broadcastService, PaymentService paymentService) {
        this.subscriptionService = subscriptionService;
        this.broadcastService = broadcastService;
        this.paymentService = paymentService;
    }

    @Override
    public String getBotUsername() {
        return env.botUsername();
    }

    private boolean isAdmin(long chatId) {
        return env.adminIds().contains(chatId);
    }

    private void ensureUserSaved(User user, long chatId) {
        if (user == null) return;
        db.upsertUser(chatId, user.getUserName(), user.getFirstName(), user.getLastName(), isAdmin(chatId));
    }

    @Override
    public void onUpdateReceived(Update update) {
        try {
            if (update.hasMessage()) {
                Message msg = update.getMessage();
                long chatId = msg.getChatId();
                ensureUserSaved(msg.getFrom(), chatId);

                SuccessfulPayment sp = msg.getSuccessfulPayment();
                if (sp != null) {
                    subscriptionService.activateSubscription(chatId);
                    return;
                }

                if (msg.hasText()) {
                    String text = msg.getText().trim();
                    if (text.equals("/start")) {
                        sendStart(chatId);
                        return;
                    }
                    if (text.equals("/admin") && isAdmin(chatId)) {
                        sendAdminPanel(chatId);
                        return;
                    }
                    if (text.equals("/subs") && isAdmin(chatId)) {
                        handleSubs(chatId);
                        return;
                    }
                    if (text.equals("/send") && isAdmin(chatId)) {
                        handleSendStart(chatId);
                        return;
                    }

                    if (isAdmin(chatId) && broadcastService.isCollecting(chatId)) {
                        broadcastService.setCaption(chatId, text);
                        broadcastService.finalizeAndBroadcast(chatId);
                        execute(SendMessage.builder().chatId(Long.toString(chatId)).text("📨 Рассылка успешно отправлена всем пользователям.")
                                .build());
                        return;
                    }
                }

                if (isAdmin(chatId) && broadcastService.isCollecting(chatId)) {
                    if (msg.hasPhoto()) {
                        List<PhotoSize> list = msg.getPhoto();
                        PhotoSize best = list.stream().max(Comparator.comparing(PhotoSize::getFileSize)).orElse(list.get(list.size() - 1));
                        broadcastService.addPhoto(chatId, best.getFileId());
                        return;
                    }
                    if (msg.hasVideo()) {
                        broadcastService.addVideo(chatId, msg.getVideo().getFileId());
                        return;
                    }
                    if (msg.hasDocument()) {
                        broadcastService.addDocument(chatId, msg.getDocument().getFileId());
                        return;
                    }
                }
            }

            if (update.hasCallbackQuery()) {
                CallbackQuery cq = update.getCallbackQuery();
                String data = cq.getData();
                long chatId = cq.getMessage().getChatId();
                ensureUserSaved(cq.getFrom(), chatId);

                switch (data) {
                    case "MY_SUBSCRIPTION" -> subscriptionService.showMySubscription(chatId);
                    case "BUY_SUBSCRIPTION" -> paymentService.sendInvoice(chatId);
                    case "CANCEL_SUBSCRIPTION" -> askCancelConfirm(cq);
                    case "CONFIRM_CANCEL_YES" -> {
                        subscriptionService.cancelSubscription(chatId);
                        sendCancelOk(chatId);
                    }
                    case "CONFIRM_CANCEL_NO" -> sendStart(chatId);
                    case "BACK_TO_MENU" -> sendStart(chatId);
                }
            }

            if (update.hasPreCheckoutQuery()) {
                PreCheckoutQuery pq = update.getPreCheckoutQuery();
                paymentService.answerPreCheckout(pq.getId(), true, null);
            }
        } catch (Exception e) {
            log.error("Update handling failed", e);
        }
    }

    private void sendCancelOk(long chatId) throws TelegramApiException {
        SendMessage msg = SendMessage.builder()
                .chatId(Long.toString(chatId))
                .text("❌ Подписка отменена.\nВы всегда можете оформить новую подписку через «💳 Оформить подписку».")
                .replyMarkup(Keyboards.backToMenu())
                .build();
        execute(msg);
    }

    private void sendStart(long chatId) throws TelegramApiException {
        SendMessage m = SendMessage.builder()
                .chatId(Long.toString(chatId))
                .text("""
                        👋 Привет! 
                        
                        Это бот Собери Очки.
                        Выберите нужный пункт ниже ⬇️
                        """)

                .replyMarkup(Keyboards.startMenu())
                .build();
        execute(m);
    }

    private void sendAdminPanel(long chatId) throws TelegramApiException {
        String txt = """
                🛠 Админ-панель
                
                • /subs — 👥 показать активные подписки
                • /send — 📣 сделать рассылку (сначала медиа/файлы, потом текст)
                """;
        execute(SendMessage.builder().chatId(Long.toString(chatId)).text(txt).build());
    }

    private void handleSubs(long chatId) throws TelegramApiException {
        var lines = db.listActiveSubscribersTagAndDate();
        String body = lines.isEmpty()
                ? "🕊 Сейчас нет ни одной активной подписки."
                : String.join("\\n", lines);
        execute(SendMessage.builder().chatId(Long.toString(chatId)).text(body).build());
    }

    private void handleSendStart(long chatId) throws TelegramApiException {
        broadcastService.startCollecting(chatId);
        String txt = """
                📣 Режим рассылки
                
                1️⃣ Отправьте фото (можно несколько), видео и/или файлы.
                2️⃣ Когда закончите с медиа — пришлите одним сообщением текст рассылки.
                
                ✉️ Всё будет отправлено пользователям одним сообщением.
                """;
        execute(SendMessage.builder().chatId(Long.toString(chatId)).text(txt).build());
    }

    private void askCancelConfirm(CallbackQuery cq) throws TelegramApiException {
        SendMessage msg = SendMessage.builder()
                .chatId(cq.getMessage().getChatId().toString())
                .text("❓ Вы действительно хотите отменить подписку?")
                .replyMarkup(Keyboards.confirmCancel())
                .build();
        execute(msg);
    }
}
