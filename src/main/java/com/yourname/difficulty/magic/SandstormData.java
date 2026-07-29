package com.yourname.difficulty.magic;

import org.bukkit.Location;

import java.util.UUID;

/**
 * Mutable state for a single active Sandstorm instance.
 * Extracted from {@link SandstormManager} to keep files under the 400-line limit.
 */
class SandstormData {
    final Location centre;
    private long ticksLeft;
    /** UUID of the player who triggered this storm — used for immunity + upkeep. */
    UUID casterUuid;
    /** Hard cap on this storm's total duration in ticks (e.g. from remaining Downpour). */
    long capTicks;
    /** Ticks elapsed since the last upkeep (sand) consumption. */
    long ticksSinceUpkeep = 0;

    SandstormData(Location centre, long ticks, UUID casterUuid, long capTicks) {
        this.centre     = centre;
        this.ticksLeft  = ticks;
        this.casterUuid = casterUuid;
        this.capTicks   = capTicks;
    }

    /** Decrements by 40 (the tick interval). Returns true if still alive. */
    boolean tickDown() {
        ticksLeft -= 40;
        return ticksLeft > 0;
    }

    boolean isAlive() { return ticksLeft > 0; }
    long remainingTicks() { return ticksLeft; }
    void setRemainingTicks(long t) { ticksLeft = t; }
    void cancel() { ticksLeft = 0; }
}
