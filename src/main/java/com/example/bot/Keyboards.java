package com.example.bot;

import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Keyboards {

    public static InlineKeyboardMarkup startMenu() {
        InlineKeyboardButton sub = InlineKeyboardButton.builder()
                .text("📅 Моя подписка")
                .callbackData("MY_SUBSCRIPTION")
                .build();

        InlineKeyboardButton buy = InlineKeyboardButton.builder()
                .text("💳 Оформить подписку")
                .callbackData("BUY_SUBSCRIPTION")
                .build();

        InlineKeyboardButton cancel = InlineKeyboardButton.builder()
                .text("❌ Отменить подписку")
                .callbackData("CANCEL_SUBSCRIPTION")
                .build();

        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(Arrays.asList(sub));
        rows.add(Arrays.asList(buy));
        rows.add(Arrays.asList(cancel));

        return InlineKeyboardMarkup.builder()
                .keyboard(rows)
                .build();
    }

    public static InlineKeyboardMarkup confirmCancel() {
        InlineKeyboardButton yes = InlineKeyboardButton.builder()
                .text("✅ Да")
                .callbackData("CONFIRM_CANCEL_YES")
                .build();

        InlineKeyboardButton no = InlineKeyboardButton.builder()
                .text("↩️ Нет")
                .callbackData("CONFIRM_CANCEL_NO")
                .build();

        return InlineKeyboardMarkup.builder()
                .keyboard(List.of(List.of(yes, no)))
                .build();
    }

    public static InlineKeyboardMarkup backToMenu() {
        InlineKeyboardButton back = InlineKeyboardButton.builder()
                .text("⬅️ Вернуться в меню")
                .callbackData("BACK_TO_MENU")
                .build();

        return InlineKeyboardMarkup.builder()
                .keyboard(List.of(List.of(back)))
                .build();
    }

    public static InlineKeyboardMarkup buyYearButton() {
        InlineKeyboardButton buy = InlineKeyboardButton.builder()
                .text("💳 Оформить годовую")
                .callbackData("BUY_YEAR_SUBSCRIPTION")
                .build();

        return InlineKeyboardMarkup.builder()
                .keyboard(List.of(List.of(buy)))
                .build();
    }

    public static InlineKeyboardMarkup buyMonthButton() {
        InlineKeyboardButton buy = InlineKeyboardButton.builder()
                .text("💳 Оплатить месяц 200 ₽")
                .callbackData("BUY_MONTH_SUBSCRIPTION")
                .build();

        return InlineKeyboardMarkup.builder()
                .keyboard(List.of(List.of(buy)))
                .build();
    }
}