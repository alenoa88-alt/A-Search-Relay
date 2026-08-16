package com.asearch.relay;

import com.asearch.relay.data.Entities;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public final class LocalCommunicationStyleAnalyzer implements CommunicationStyleAnalyzer {
    private final LanguageDetector languageDetector = new LanguageDetector();

    @Override
    public Entities.CommunicationStyleProfileEntity analyze(
            String contactId,
            List<Entities.MessageEntity> messages
    ) {
        List<Entities.MessageEntity> samples = selectAleSamples(messages, 80);
        Entities.CommunicationStyleProfileEntity profile =
                new Entities.CommunicationStyleProfileEntity();
        profile.contactId = contactId;
        profile.sampleCount = samples.size();
        profile.lastUpdated = System.currentTimeMillis();
        if (samples.isEmpty()) {
            profile.primaryLanguage = "UNKNOWN";
            profile.formalityScore = 0.5;
            profile.confidenceScore = 0;
            profile.punctuationStyle = "INSUFFICIENT_HISTORY";
            profile.capitalizationStyle = "INSUFFICIENT_HISTORY";
            return profile;
        }

        double weightedLength = 0;
        double weightTotal = 0;
        int emojiMessages = 0;
        int punctuationMessages = 0;
        int capitalStarts = 0;
        Map<String, Integer> languages = new HashMap<>();
        Map<String, Integer> terms = new HashMap<>();
        Map<String, Integer> emojis = new HashMap<>();
        Map<String, Integer> greetings = new HashMap<>();
        Map<String, Integer> closings = new HashMap<>();
        List<String> representatives = new ArrayList<>();

        for (int i = 0; i < samples.size(); i++) {
            Entities.MessageEntity message = samples.get(i);
            String text = message.text == null ? "" : message.text.trim();
            double weight = i < 20 ? 2.0 : 1.0;
            weightedLength += text.length() * weight;
            weightTotal += weight;
            if (representatives.size() < 12) representatives.add(message.messageId);

            LanguageContext language = languageDetector.detect(text);
            languages.merge(language.primaryLanguage, 1, Integer::sum);
            if (language.secondaryLanguage != null) {
                languages.merge(language.secondaryLanguage, 1, Integer::sum);
            }
            if (text.matches(".*[.!?,].*")) punctuationMessages++;
            if (!text.isEmpty() && Character.isUpperCase(text.codePointAt(0))) capitalStarts++;

            List<String> messageEmojis = extractEmojis(text);
            if (!messageEmojis.isEmpty()) emojiMessages++;
            for (String emoji : messageEmojis) emojis.merge(emoji, 1, Integer::sum);

            String[] words = text.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}']+", " ").trim().split("\\s+");
            for (String word : words) {
                if (word.length() >= 3) terms.merge(word, 1, Integer::sum);
            }
            if (words.length > 0 && !words[0].isEmpty()) greetings.merge(words[0], 1, Integer::sum);
            if (words.length > 1) closings.merge(words[words.length - 1], 1, Integer::sum);
        }

        List<String> rankedLanguages = rankedKeys(languages, 2);
        profile.primaryLanguage = rankedLanguages.isEmpty() ? "UNKNOWN" : rankedLanguages.get(0);
        profile.secondaryLanguage = rankedLanguages.size() > 1 ? rankedLanguages.get(1) : null;
        profile.averageMessageLength = weightedLength / Math.max(1, weightTotal);
        profile.emojiFrequency = emojiMessages / (double) samples.size();
        profile.commonEmojis = String.join(",", rankedKeys(emojis, 8));
        profile.commonTerms = String.join(",", rankedKeys(terms, 16));
        profile.greetingPatterns = String.join(",", rankedKeys(greetings, 6));
        profile.closingPatterns = String.join(",", rankedKeys(closings, 6));
        profile.punctuationStyle = punctuationMessages >= samples.size() * 0.6
                ? "FREQUENT" : punctuationMessages <= samples.size() * 0.2 ? "MINIMAL" : "MIXED";
        profile.capitalizationStyle = capitalStarts >= samples.size() * 0.6
                ? "USUALLY_CAPITALIZED" : capitalStarts <= samples.size() * 0.2
                ? "USUALLY_LOWERCASE" : "MIXED";
        profile.formalityScore = formality(profile, punctuationMessages, capitalStarts, samples.size());
        profile.representativeMessageIds = String.join(",", representatives);
        profile.confidenceScore = Math.min(1, samples.size() / 20.0);
        return profile;
    }

    public static List<Entities.MessageEntity> selectAleSamples(
            List<Entities.MessageEntity> messages,
            int limit
    ) {
        return messages.stream()
                .filter(message -> message.sentByMe)
                .filter(message -> message.text != null && !message.text.trim().isEmpty())
                .sorted(Comparator.comparingLong((Entities.MessageEntity item) -> item.timestamp).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    private static double formality(
            Entities.CommunicationStyleProfileEntity profile,
            int punctuation,
            int capitalStarts,
            int count
    ) {
        double score = 0.25;
        score += Math.min(0.25, profile.averageMessageLength / 300.0);
        score += punctuation / (double) count * 0.2;
        score += capitalStarts / (double) count * 0.2;
        if (profile.commonTerms != null && profile.commonTerms.matches(".*(please|regards|thank).*")) {
            score += 0.1;
        }
        return Math.min(1, score);
    }

    private static List<String> extractEmojis(String text) {
        List<String> result = new ArrayList<>();
        text.codePoints().forEach(codePoint -> {
            if ((codePoint >= 0x1F300 && codePoint <= 0x1FAFF)
                    || (codePoint >= 0x2600 && codePoint <= 0x27BF)) {
                result.add(new String(Character.toChars(codePoint)));
            }
        });
        return result;
    }

    private static List<String> rankedKeys(Map<String, Integer> values, int limit) {
        return values.entrySet().stream()
                .sorted((left, right) -> {
                    int count = Integer.compare(right.getValue(), left.getValue());
                    return count != 0 ? count : left.getKey().compareTo(right.getKey());
                })
                .limit(limit)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }
}
