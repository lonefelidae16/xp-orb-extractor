package me.lonefelidae16.xpOrbExtractor.util;

import me.lonefelidae16.xpOrbExtractor.XpOrbExtractor;
import me.lonefelidae16.xpOrbExtractor.XpOrbExtractorConfig;
import me.lonefelidae16.xpOrbExtractor.state.DrainResult;
import net.minecraft.world.entity.player.Player;

public class PlayerUtil {
    private PlayerUtil() {
    }

    /**
     * Too heavy method when the target is {@link me.lonefelidae16.xpOrbExtractor.XpOrbExtractorConfig.DrainTarget#LEVEL}
     * and the amount is {@link Integer#MAX_VALUE}.
     * <p><strong>The argument {@code player} may be null while calculating!</strong></p>
     *
     * @param player A Player instance.
     * @return A {@link DrainResult} instance that contains the amount of experience and whether the Player has
     * enough points.
     */
    public static DrainResult getAndDecreaseXp(Player player) {
        final int maxAmount = XpOrbExtractor.config().amountToDrain;
        final XpOrbExtractorConfig.DrainTarget drainTarget = XpOrbExtractor.config().drainTarget;
        if (maxAmount < 0 || drainTarget == null) {
            return DrainResult.EMPTY;
        }

        switch (drainTarget) {
            case LEVEL -> {
                int xp = 0;
                int drainedLevel = 0;
                boolean bLevelReached;
                try {
                    while (drainedLevel < maxAmount && xp < Integer.MAX_VALUE && player.experienceLevel > 0) {
                        final int remaining = Integer.MAX_VALUE - xp;
                        final int toDrain = Math.min(player.getXpNeededForNextLevel(), remaining);
                        player.giveExperiencePoints(-toDrain);
                        xp += toDrain;
                        ++drainedLevel;
                    }

                    bLevelReached = drainedLevel == maxAmount;
                    if (!bLevelReached && xp < Integer.MAX_VALUE) {
                        while (canExtractXpAsOneOrb(player) && xp < Integer.MAX_VALUE) {
                            player.giveExperiencePoints(-1);
                            ++xp;
                        }
                    }
                } catch (Exception ex) {
                    XpOrbExtractor.LOGGER.error("An error occurred while getting xp", ex);
                    return new DrainResult(xp, false, true);
                }
                return new DrainResult(xp, !bLevelReached && xp < Integer.MAX_VALUE);
            }
            case XP -> {
                int xp = 0;
                try {
                    while (player.experienceLevel > 0 && xp < maxAmount) {
                        final int remaining = maxAmount - xp;
                        final int toDrain = Math.min(player.getXpNeededForNextLevel(), remaining);
                        player.giveExperiencePoints(-toDrain);
                        xp += toDrain;
                    }

                    if (xp < maxAmount) {
                        while (canExtractXpAsOneOrb(player) && xp < maxAmount) {
                            player.giveExperiencePoints(-1);
                            ++xp;
                        }
                    }
                } catch (Exception ex) {
                    XpOrbExtractor.LOGGER.error("An error occurred while getting xp", ex);
                    return new DrainResult(xp, false, true);
                }
                return new DrainResult(xp, xp < maxAmount);
            }
        }
        return DrainResult.EMPTY;
    }

    private static boolean canExtractXpAsOneOrb(Player player) {
        if (player.experienceLevel > 0) {
            return true;
        }

        final float oneOrbProgress = 1f / player.getXpNeededForNextLevel();
        return player.experienceProgress - oneOrbProgress > 0f;
    }
}
