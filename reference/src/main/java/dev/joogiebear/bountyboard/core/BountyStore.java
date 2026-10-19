package dev.joogiebear.bountyboard.core;

public interface BountyStore {
    StoreData load();

    void save(StoreData data);
}
