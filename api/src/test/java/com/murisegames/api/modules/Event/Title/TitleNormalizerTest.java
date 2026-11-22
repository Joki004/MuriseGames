package com.murisegames.api.modules.Event.Title;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TitleNormalizerTest {

    @Test
    void ignoresCaseAccentsAndPunctuation() {
        assertEquals("amelie", TitleNormalizer.normalize("Amélie"));
        assertEquals(
                "spider man homecoming",
                TitleNormalizer.normalize("  Spider-Man:  Homecoming ")
        );
        assertEquals("zolc", TitleNormalizer.normalize("Żółć"));
    }

    @Test
    void nullAndSymbolsOnlyBecomeEmpty() {
        assertEquals("", TitleNormalizer.normalize(null));
        assertEquals("", TitleNormalizer.normalize("?!..."));
    }
}