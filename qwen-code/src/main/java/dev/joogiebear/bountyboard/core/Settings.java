package dev.joogiebear.bountyboard.core;

public record Settings(long minAmount, long maxAmount, int maxActivePerPoster, int defaultHours, int maxHours,
                       int taxPercent, int claimCooldownMinutes, int cancelFullRefundMinutes, int cancelRefundPercent,
                       int purgeResolvedAfterDays) {

    public static Settings defaults() {
        return new Settings(10, 1_000_000, 3, 24, 72, 5, 10, 5, 80, 7);
    }
}
