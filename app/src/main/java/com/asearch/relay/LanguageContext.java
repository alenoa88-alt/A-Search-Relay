package com.asearch.relay;

public final class LanguageContext {
    public final String originalText;
    public final String primaryLanguage;
    public final String secondaryLanguage;
    public final boolean mixed;
    public final double confidence;

    LanguageContext(
            String originalText,
            String primaryLanguage,
            String secondaryLanguage,
            boolean mixed,
            double confidence
    ) {
        this.originalText = originalText;
        this.primaryLanguage = primaryLanguage;
        this.secondaryLanguage = secondaryLanguage;
        this.mixed = mixed;
        this.confidence = confidence;
    }
}

