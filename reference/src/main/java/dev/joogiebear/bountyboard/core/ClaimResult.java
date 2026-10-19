package dev.joogiebear.bountyboard.core;

import java.util.List;

public record ClaimResult(List<Bounty> claimed, long payout, boolean onCooldown) {}
