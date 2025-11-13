package com.example.bot.service;

import com.example.bot.db.Database;
import com.example.bot.TelegramBot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMediaGroup;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.send.SendVideo;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.media.*;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class BroadcastService {
    private static final Logger log = LoggerFactory.getLogger(BroadcastService.class);

    public static class Session {
        public final long adminId;
        public final List<InputMedia> media = new ArrayList<>();
        public String caption = null;
        public Session(long adminId) { this.adminId = adminId; }
    }

    private final Database db;
    private final TelegramBot bot;
    private final Map<Long, Session> sessions = new ConcurrentHashMap<>();

    public BroadcastService(Database db, TelegramBot bot) {
        this.db = db;
        this.bot = bot;
    }

    public boolean isCollecting(long adminId) { return sessions.containsKey(adminId); }
    public void startCollecting(long adminId) { sessions.put(adminId, new Session(adminId)); }
    public void addPhoto(long adminId, String fileId) { Session s = sessions.get(adminId); if (s != null) s.media.add(new InputMediaPhoto(fileId)); }
    public void addVideo(long adminId, String fileId) { Session s = sessions.get(adminId); if (s != null) s.media.add(new InputMediaVideo(fileId)); }
    public void addDocument(long adminId, String fileId) { Session s = sessions.get(adminId); if (s != null) s.media.add(new InputMediaDocument(fileId)); }
    public void setCaption(long adminId, String text) { Session s = sessions.get(adminId); if (s != null) s.caption = text; }

    public void finalizeAndBroadcast(long adminId) {
        Session s = sessions.remove(adminId);
        if (s == null) return;
        List<Long> recipients = db.getAllUserChatIds();
        if (recipients.isEmpty()) return;

        if (!s.media.isEmpty() && s.caption != null && !s.caption.isBlank()) {
            s.media.get(0).setCaption(s.caption);
        }

        for (Long chatId : recipients) {
            try {
                if (s.media.isEmpty()) {
                    bot.execute(SendMessage.builder().chatId(chatId.toString()).text(s.caption == null ? "" : s.caption).build());
                } else if (s.media.size() == 1) {
                    InputMedia first = s.media.get(0);
                    if (first instanceof InputMediaPhoto p) {
                        SendPhoto sp = new SendPhoto();
                        sp.setChatId(chatId.toString());
                        sp.setPhoto(new InputFile(p.getMedia()));
                        sp.setCaption(p.getCaption());
                        bot.execute(sp);
                    } else if (first instanceof InputMediaVideo v) {
                        SendVideo sv = new SendVideo();
                        sv.setChatId(chatId.toString());
                        sv.setVideo(new InputFile(v.getMedia()));
                        sv.setCaption(v.getCaption());
                        bot.execute(sv);
                    } else if (first instanceof InputMediaDocument d) {
                        SendDocument sd = new SendDocument();
                        sd.setChatId(chatId.toString());
                        sd.setDocument(new InputFile(d.getMedia()));
                        sd.setCaption(d.getCaption());
                        bot.execute(sd);
                    } else {
                        bot.execute(SendMessage.builder().chatId(chatId.toString()).text(s.caption == null ? "" : s.caption).build());
                    }
                } else {
                    SendMediaGroup group = new SendMediaGroup();
                    group.setChatId(chatId.toString());
                    group.setMedias(s.media);
                    bot.execute(group);
                }
                Thread.sleep(30);
            } catch (Exception e) {
                log.warn("Broadcast to {} failed: {}", chatId, e.getMessage());
            }
        }
    }
}
