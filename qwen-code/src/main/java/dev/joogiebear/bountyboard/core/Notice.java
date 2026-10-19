package dev.joogiebear.bountyboard.core;

import java.util.UUID;

/** A message waiting for a poster. See DESIGN.md for what `other` holds for each type. */
public record Notice(UUID player, NoticeType type, int bountyId, long amount, String other) {}
