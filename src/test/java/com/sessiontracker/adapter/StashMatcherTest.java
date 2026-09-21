package com.sessiontracker.adapter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.util.HashMap;
import java.util.Map;
import java.util.OptionalInt;
import org.junit.Test;

/** Turning a gather message into the item it produced. */
public class StashMatcherTest {

    private static StashMatcher logs() {
        Map<String, Integer> keywords = new HashMap<>();
        keywords.put("logs", 1511);
        keywords.put("yew logs", 1515);
        keywords.put("oak logs", 1521);
        return new StashMatcher("^you get some .+\\.$", keywords);
    }

    @Test
    public void matchesTheItemNamedInTheMessage() {
        assertEquals(OptionalInt.of(1521), logs().match("You get some oak logs."));
    }

    @Test
    public void prefersTheLongestNameSoYewLogsBeatsPlainLogs() {
        assertEquals(OptionalInt.of(1515), logs().match("You get some yew logs."));
    }

    @Test
    public void stillMatchesThePlainNameOnItsOwn() {
        assertEquals(OptionalInt.of(1511), logs().match("You get some logs."));
    }

    @Test
    public void ignoresAMessageThatIsNotAGather() {
        assertFalse(logs().match("Your log basket is full.").isPresent());
    }

    @Test
    public void readsThroughTheColourTagsTheGameWrapsMessagesIn() {
        assertEquals(OptionalInt.of(1521), logs().match("<col=ff0000>You get some oak logs.</col>"));
    }

    @Test
    public void ignoresAGatherOfSomethingThisBagDoesNotHold() {
        assertFalse(logs().match("You get some mushrooms.").isPresent());
    }
}
