package dev.joogiebear.bountyboard.core;

import java.nio.file.Path;

/** YAML file persistence. See DESIGN.md, section "Persistence". */
public class FileBountyStore implements BountyStore {

    public FileBountyStore(Path file) {
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    public StoreData load() {
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    public void save(StoreData data) {
        throw new UnsupportedOperationException("TODO");
    }
}
