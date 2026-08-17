package com.yourname.difficulty.input;

import java.util.Arrays;

/**
 * ActionInputBuffer — reusable sub-tick ring buffer for skill inputs.
 *
 * <p>Records the wall-clock timestamps of the last {@code capacity}
 * activations of a single action in a small ring. {@link #recordAndCheck} is
 * called on every activation; it returns {@code true} the moment a NEW
 * activation arrives within {@code windowMs} of the previous one — the
 * classic "double-tap / double-input" detector used for dashes, combos and
 * other buffered gestures on a ~350-380 ms boundary.</p>
 *
 * <p>Because the buffer only stores timestamps and never blocks, the main
 * thread cost per activation is O(1). Stale entries naturally fall out of the
 * window on the next successful check.</p>
 */
public final class ActionInputBuffer {

    private final long[] buffer;
    private final long   windowMs;
    private int          next;

    /**
     * @param capacity number of activations remembered (at least 2)
     * @param windowMs max gap between two activations to count as a double
     */
    public ActionInputBuffer(int capacity, long windowMs) {
        this.buffer   = new long[Math.max(2, capacity)];
        this.windowMs = windowMs;
    }

    /**
     * Records an activation at {@code now} and returns {@code true} if the
     * two most recent activations (including this one) fall inside the window.
     */
    public boolean recordAndCheck(long now) {
        long prev = buffer[next];
        buffer[next] = now;
        next = (next + 1) % buffer.length;
        return prev != 0L && (now - prev) <= windowMs;
    }

    /** True if another activation right now would complete a double-input. */
    public boolean wouldMatch(long now) {
        long prev = buffer[(next - 1 + buffer.length) % buffer.length];
        return prev != 0L && (now - prev) <= windowMs;
    }

    /** Wipes all recorded timestamps. */
    public void clear() {
        Arrays.fill(buffer, 0L);
        next = 0;
    }
}