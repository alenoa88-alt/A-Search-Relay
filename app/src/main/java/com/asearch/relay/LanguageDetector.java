package com.asearch.relay;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class LanguageDetector {
    private static final Set<String> MALTESE_TERMS = new HashSet<>(Arrays.asList(
            "u", "għal", "għandi", "għandek", "illum", "għada", "issa", "mela",
            "jekk", "tajjeb", "tajjeb", "grazzi", "ħafna", "biex", "ma", "int", "jien"
    ));
    private static final Set<String> ENGLISH_TERMS = new HashSet<>(Arrays.asList(
            "the", "and", "for", "with", "today", "tomorrow", "call", "meeting",
            "thanks", "please", "yes", "no", "can", "will", "music", "event"
    ));

    public LanguageContext detect(String text) {
        String original = text == null ? "" : text;
        String clean = original.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}']+", " ").trim();
        if (clean.isEmpty()) {
            return new LanguageContext(original, "UNKNOWN", null, false, 0);
        }
        int maltese = containsMalteseCharacters(clean) ? 3 : 0;
        int english = 0;
        for (String token : clean.split("\\s+")) {
            if (MALTESE_TERMS.contains(token)) maltese++;
            if (ENGLISH_TERMS.contains(token)) english++;
        }
        boolean mixed = maltese > 0 && english > 0;
        if (mixed) {
            String primary = maltese >= english ? "MALTESE" : "ENGLISH";
            String secondary = "MALTESE".equals(primary) ? "ENGLISH" : "MALTESE";
            return new LanguageContext(original, primary, secondary, true, confidence(maltese, english));
        }
        if (maltese > 0) {
            return new LanguageContext(original, "MALTESE", null, false, Math.min(1, 0.55 + maltese * 0.08));
        }
        return new LanguageContext(original, "ENGLISH", null, false, Math.min(0.85, 0.4 + english * 0.08));
    }

    private static boolean containsMalteseCharacters(String text) {
        return text.matches(".*[ċġħż].*");
    }

    private static double confidence(int first, int second) {
        int total = first + second;
        return total == 0 ? 0 : Math.min(1, 0.5 + Math.abs(first - second) / (double) total * 0.4);
    }
}


