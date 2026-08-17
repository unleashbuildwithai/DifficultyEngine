package net.yourserver.coreengine.rank;

/**
 * Donor rank tiers assignable via {@code /givemember <user> <1|2|3>}, and the
 * combined active-listing cap each tier is allowed on the Market (per the
 * "Rank-Based Listing Caps" section of the Module 1 spec).
 */
public enum PlayerRank {

    NONE(0, "Non-Member", 3, 3),
    MEMBER(1, "Member", 6, 10),
    MEMBER_PLUS(2, "Member+", 12, 25),
    MEMBER_PLUS_PLUS(3, "Member++", 25, 50);

    private final int tier;
    private final String displayName;
    private final int maxListings;
    private final int maxHomes;

    PlayerRank(int tier, String displayName, int maxListings, int maxHomes) {
        this.tier = tier;
        this.displayName = displayName;
        this.maxListings = maxListings;
        this.maxHomes = maxHomes;
    }

    public int getTier() {
        return tier;
    }

    public String getDisplayName() {
        return displayName;
    }

    /** Max COMBINED active SELL + BUY listings allowed for this rank. */
    public int getMaxListings() {
        return maxListings;
    }

    /** Max number of home slots this rank can save. */
    public int getMaxHomes() {
        return maxHomes;
    }

    public static PlayerRank fromTier(int tier) {
        for (PlayerRank rank : values()) {
            if (rank.tier == tier) {
                return rank;
            }
        }
        return NONE;
    }
}
