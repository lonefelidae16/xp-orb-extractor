package me.lonefelidae16.xpOrbExtractor.state;

public class DrainResult {
    public static final DrainResult EMPTY = new DrainResult(0, true);

    public final int amount;
    public final boolean bDepleted;

    public DrainResult(int amount, boolean bDepleted) {
        this.amount = Math.max(amount, 0);
        this.bDepleted = bDepleted;
    }
}
