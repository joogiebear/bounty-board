package dev.joogiebear.bountyboard.core;

import java.util.List;

/** Everything the store persists. nextId starts at 1. */
public record StoreData(List<Bounty> bounties, List<Notice> notices, int nextId) {}
