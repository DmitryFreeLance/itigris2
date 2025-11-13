package com.example.bot;

import java.time.LocalDate;

public class DateUtils {
    private static final String[] MONTHS_RU_GEN = {
            "января", "февраля", "марта", "апреля", "мая", "июня",
            "июля", "августа", "сентября", "октября", "ноября", "декабря"
    };

    public static String formatRu(LocalDate date) {
        int m = date.getMonthValue();
        return date.getDayOfMonth() + " " + MONTHS_RU_GEN[m-1] + " " + date.getYear();
    }
}
