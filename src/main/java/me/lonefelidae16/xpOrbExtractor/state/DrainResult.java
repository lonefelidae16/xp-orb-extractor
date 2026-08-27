package me.lonefelidae16.xpOrbExtractor.state;

public class DrainResult {
    public static final DrainResult EMPTY = new DrainResult(0, true);

    public final int amount;
    public final boolean bDepleted;
    public final boolean bFatal;

    /**
     * Shows the result of draining the Player's experience points.
     *
     * @param amount An amount of experience points. Must be positive Integer.
     * @param bDepleted If {@code true}, Player does not have required points.
     */
    public DrainResult(int amount, boolean bDepleted) {
        this(amount, bDepleted, false);
    }

    /**
     * @param bFatal If {@code true}, something went wrong draining xp.
     */
    public DrainResult(int amount, boolean bDepleted, boolean bFatal) {
        this.amount = Math.max(amount, 0);
        this.bDepleted = bDepleted;
        this.bFatal = bFatal;
    }
}
