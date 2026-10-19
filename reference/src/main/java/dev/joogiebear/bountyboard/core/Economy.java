package dev.joogiebear.bountyboard.core;

import java.util.UUID;

public interface Economy {
    long balance(UUID player);

    /** Returns false and changes nothing when the balance is too low. */
    boolean withdraw(UUID player, long amount);

    void deposit(UUID player, long amount);
}
