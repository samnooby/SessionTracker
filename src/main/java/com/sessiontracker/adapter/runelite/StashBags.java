package com.sessiontracker.adapter.runelite;

import com.sessiontracker.adapter.StashMatcher;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import net.runelite.api.gameval.ItemID;

/**
 * The storage containers whose contents the client cannot read, and the message the game prints
 * as each one swallows something you gathered.
 *
 * <p>An item only reaches these containers while they are open, and open is a different item id
 * from closed, so the open ids are what decide whether a message counts. The keyword for each
 * item is the word the message uses, which is not always the item's own name: the herb sack says
 * "Grimy ranarr herb" for an item called "Grimy ranarr weed".
 */
final class StashBags {

    /** One container: what it is called, which item ids are it, and what its messages mean. */
    static final class Bag {
        private final String name;
        private final int[] openIds;
        private final int[] closedIds;
        private final StashMatcher matcher;

        Bag(String name, int[] openIds, int[] closedIds, StashMatcher matcher) {
            this.name = name;
            this.openIds = openIds;
            this.closedIds = closedIds;
            this.matcher = matcher;
        }

        String name() {
            return name;
        }

        /** The item this message says was swallowed, if it is one this container holds. */
        OptionalInt match(String message) {
            return matcher.match(message);
        }

        boolean isOpen(int itemId) {
            return contains(openIds, itemId);
        }

        boolean isAnyForm(int itemId) {
            return contains(openIds, itemId) || contains(closedIds, itemId);
        }
    }

    private static boolean contains(int[] ids, int itemId) {
        for (int id : ids) {
            if (id == itemId) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, Integer> keywords(Object... pairs) {
        Map<String, Integer> m = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            m.put((String) pairs[i], (Integer) pairs[i + 1]);
        }
        return m;
    }

    // "You manage to mine some coal."
    private static final StashMatcher COAL = new StashMatcher(
            "^you manage to mine some .+\\.$",
            keywords("coal", ItemID.COAL));

    // "You just found a Sapphire!" / "You just mined a piece of Jade!"
    private static final StashMatcher GEMS = new StashMatcher(
            "^you just (found|mined) (a|an|a piece of) .+!$",
            keywords(
                    "sapphire", ItemID.UNCUT_SAPPHIRE,
                    "emerald", ItemID.UNCUT_EMERALD,
                    "ruby", ItemID.UNCUT_RUBY,
                    "diamond", ItemID.UNCUT_DIAMOND,
                    "dragonstone", ItemID.UNCUT_DRAGONSTONE,
                    "opal", ItemID.UNCUT_OPAL,
                    "jade", ItemID.UNCUT_JADE,
                    "red topaz", ItemID.UNCUT_RED_TOPAZ));

    // "You put the Grimy ranarr herb into your herb sack."
    private static final StashMatcher HERBS = new StashMatcher(
            "^you put the .+ into your herb sack\\.$",
            keywords(
                    "guam", ItemID.UNIDENTIFIED_GUAM,
                    "marrentill", ItemID.UNIDENTIFIED_MARENTILL,
                    "tarromin", ItemID.UNIDENTIFIED_TARROMIN,
                    "harralander", ItemID.UNIDENTIFIED_HARRALANDER,
                    "ranarr", ItemID.UNIDENTIFIED_RANARR,
                    "toadflax", ItemID.UNIDENTIFIED_TOADFLAX,
                    "irit", ItemID.UNIDENTIFIED_IRIT,
                    "avantoe", ItemID.UNIDENTIFIED_AVANTOE,
                    "kwuarm", ItemID.UNIDENTIFIED_KWUARM,
                    "snapdragon", ItemID.UNIDENTIFIED_SNAPDRAGON,
                    "cadantine", ItemID.UNIDENTIFIED_CADANTINE,
                    "lantadyme", ItemID.UNIDENTIFIED_LANTADYME,
                    "dwarf weed", ItemID.UNIDENTIFIED_DWARF_WEED,
                    "torstol", ItemID.UNIDENTIFIED_TORSTOL,
                    "huasca", ItemID.UNIDENTIFIED_HUASCA));

    // "You catch a shark." / "You catch some anchovies."
    private static final StashMatcher FISH = new StashMatcher(
            "^you catch (a|an|some) .+[.!]$",
            keywords(
                    "shrimp", ItemID.RAW_SHRIMP,
                    "anchovies", ItemID.RAW_ANCHOVIES,
                    "sardine", ItemID.RAW_SARDINE,
                    "salmon", ItemID.RAW_SALMON,
                    "trout", ItemID.RAW_TROUT,
                    "cod", ItemID.RAW_COD,
                    "herring", ItemID.RAW_HERRING,
                    "pike", ItemID.RAW_PIKE,
                    "mackerel", ItemID.RAW_MACKEREL,
                    "tuna", ItemID.RAW_TUNA,
                    "bass", ItemID.RAW_BASS,
                    "swordfish", ItemID.RAW_SWORDFISH,
                    "lobster", ItemID.RAW_LOBSTER,
                    "shark", ItemID.RAW_SHARK,
                    "manta ray", ItemID.RAW_MANTARAY,
                    "sea turtle", ItemID.RAW_SEATURTLE,
                    "monkfish", ItemID.RAW_MONKFISH,
                    "anglerfish", ItemID.RAW_ANGLERFISH,
                    "dark crab", ItemID.RAW_DARK_CRAB));

    // "You get some yew logs."
    private static final StashMatcher LOGS = new StashMatcher(
            "^you get some .+\\.$",
            keywords(
                    "logs", ItemID.LOGS,
                    "oak logs", ItemID.OAK_LOGS,
                    "willow logs", ItemID.WILLOW_LOGS,
                    "teak logs", ItemID.TEAK_LOGS,
                    "maple logs", ItemID.MAPLE_LOGS,
                    "mahogany logs", ItemID.MAHOGANY_LOGS,
                    "yew logs", ItemID.YEW_LOGS,
                    "magic logs", ItemID.MAGIC_LOGS,
                    "redwood logs", ItemID.REDWOOD_LOGS));

    private static final List<Bag> BAGS = Collections.unmodifiableList(Arrays.asList(
            new Bag("coal bag",
                    new int[]{ItemID.COAL_BAG_OPEN},
                    new int[]{ItemID.COAL_BAG}, COAL),
            new Bag("gem bag",
                    new int[]{ItemID.GEM_BAG_OPEN, ItemID.GEM_SACK_OPEN},
                    new int[]{ItemID.GEM_BAG, ItemID.GEM_SACK}, GEMS),
            new Bag("herb sack",
                    new int[]{ItemID.SLAYER_HERB_SACK_OPEN, ItemID.SLAYER_HERB_SACK_SILK_OPEN},
                    new int[]{ItemID.SLAYER_HERB_SACK, ItemID.SLAYER_HERB_SACK_SILK}, HERBS),
            new Bag("fish barrel",
                    new int[]{ItemID.FISH_BARREL_OPEN, ItemID.FISH_SACK_BARREL_OPEN},
                    new int[]{ItemID.FISH_BARREL_CLOSED, ItemID.FISH_SACK_BARREL_CLOSED}, FISH),
            new Bag("log basket",
                    new int[]{ItemID.LOG_BASKET_OPEN, ItemID.FORESTRY_BASKET_OPEN},
                    new int[]{ItemID.LOG_BASKET_CLOSED, ItemID.FORESTRY_BASKET_CLOSED}, LOGS)));

    private StashBags() {
    }

    static List<Bag> all() {
        return BAGS;
    }

    /** The container this item id is, but only in its open form, which is the one that collects. */
    static Optional<Bag> byOpenItemId(int itemId) {
        for (Bag bag : BAGS) {
            if (bag.isOpen(itemId)) {
                return Optional.of(bag);
            }
        }
        return Optional.empty();
    }

    /** The container this item id is, open or closed, for forgetting it after a manual transfer. */
    static Optional<Bag> byAnyItemId(int itemId) {
        for (Bag bag : BAGS) {
            if (bag.isAnyForm(itemId)) {
                return Optional.of(bag);
            }
        }
        return Optional.empty();
    }
}
