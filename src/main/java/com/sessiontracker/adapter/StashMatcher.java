package com.sessiontracker.adapter;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;
import java.util.regex.Pattern;

/**
 * Recognises the message the game prints when a storage container swallows something you
 * gathered, and says which item it was.
 *
 * <p>The containers this covers expose no contents to the client, so the message is the only
 * evidence that anything happened. Matching is deliberately literal: a trigger decides whether
 * the line is a gather at all, then the longest item name mentioned in it wins, so "yew logs"
 * is not read as plain "logs".
 */
public final class StashMatcher {

    /** The game wraps some lines in colour tags; they are markup, not part of the sentence. */
    private static final Pattern TAGS = Pattern.compile("<[^>]*>");

    private final Pattern trigger;
    private final Map<String, Integer> keywords;

    public StashMatcher(String triggerRegex, Map<String, Integer> keywordToItemId) {
        this.trigger = Pattern.compile(triggerRegex, Pattern.CASE_INSENSITIVE);
        Map<String, Integer> lower = new HashMap<>();
        for (Map.Entry<String, Integer> e : keywordToItemId.entrySet()) {
            lower.put(e.getKey().toLowerCase(Locale.US), e.getValue());
        }
        this.keywords = lower;
    }

    public OptionalInt match(String message) {
        if (message == null) {
            return OptionalInt.empty();
        }
        String text = TAGS.matcher(message).replaceAll("").trim().toLowerCase(Locale.US);
        if (!trigger.matcher(text).matches()) {
            return OptionalInt.empty();
        }
        String best = null;
        for (String keyword : keywords.keySet()) {
            if (text.contains(keyword) && (best == null || keyword.length() > best.length())) {
                best = keyword;
            }
        }
        return best == null ? OptionalInt.empty() : OptionalInt.of(keywords.get(best));
    }
}
