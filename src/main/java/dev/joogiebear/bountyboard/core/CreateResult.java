package dev.joogiebear.bountyboard.core;

/** bounty is null unless status is OK. */
public record CreateResult(CreateStatus status, Bounty bounty) {}
