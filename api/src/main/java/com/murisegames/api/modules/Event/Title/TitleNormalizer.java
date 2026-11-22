package com.murisegames.api.modules.Event.Title;

import java.text.Normalizer;
import java.util.Locale;

public final class TitleNormalizer {

    private TitleNormalizer() {
    }

    /** Lowercase, no accents, punctuation and repeated spaces become one space. */
    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }

        // ł and Ł do not split into letter + accent, so map them by hand.
        String text = raw.replace('ł', 'l').replace('Ł', 'L');
        text = Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");

        return text.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .trim();
    }
}