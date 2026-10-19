package dev.joogiebear.bountyboard.core;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.configuration.file.YamlConfiguration;

/** YAML file persistence. See DESIGN.md, section "Persistence". */
public class FileBountyStore implements BountyStore {

    private final Path file;

    public FileBountyStore(Path file) {
        this.file = file;
    }

    @Override
    public StoreData load() {
        if (!Files.exists(file)) {
            return new StoreData(List.of(), List.of(), 1);
        }
        try {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.loadFromString(text);
            int nextId = yaml.getInt("next-id", 1);
            List<Bounty> bounties = new ArrayList<>();
            for (Map<?, ?> m : yaml.getMapList("bounties")) {
                bounties.add(readBounty(m));
            }
            List<Notice> notices = new ArrayList<>();
            for (Map<?, ?> m : yaml.getMapList("notices")) {
                notices.add(readNotice(m));
            }
            return new StoreData(bounties, notices, nextId);
        } catch (Exception e) {
            quarantine();
            return new StoreData(List.of(), List.of(), 1);
        }
    }

    @Override
    public void save(StoreData data) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("next-id", data.nextId());
        List<Map<String, Object>> bounties = new ArrayList<>();
        for (Bounty b : data.bounties()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", b.id());
            m.put("target", b.target().toString());
            m.put("target-name", b.targetName());
            m.put("poster", b.poster().toString());
            m.put("poster-name", b.posterName());
            m.put("amount", b.amount());
            m.put("created-at", b.createdAt().toEpochMilli());
            m.put("expires-at", b.expiresAt().toEpochMilli());
            m.put("status", b.status().name());
            if (b.claimedBy() != null) {
                m.put("claimed-by", b.claimedBy().toString());
            }
            if (b.resolvedAt() != null) {
                m.put("resolved-at", b.resolvedAt().toEpochMilli());
            }
            bounties.add(m);
        }
        yaml.set("bounties", bounties);
        List<Map<String, Object>> notices = new ArrayList<>();
        for (Notice n : data.notices()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("player", n.player().toString());
            m.put("type", n.type().name());
            m.put("bounty-id", n.bountyId());
            m.put("amount", n.amount());
            m.put("other", n.other());
            notices.add(m);
        }
        yaml.set("notices", notices);
        try {
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path tmp = Files.createTempFile(parent, file.getFileName().toString(), ".tmp");
            Files.writeString(tmp, yaml.saveToString(), StandardCharsets.UTF_8);
            try {
                Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void quarantine() {
        try {
            Path target = file.resolveSibling(file.getFileName() + ".corrupt-" + System.currentTimeMillis());
            Files.move(file, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | RuntimeException ignored) {
            // nothing more can be done; an empty store is returned either way
        }
    }

    private static Object require(Map<?, ?> m, String key) {
        Object v = m.get(key);
        if (v == null) {
            throw new IllegalArgumentException("missing " + key);
        }
        return v;
    }

    private static long number(Map<?, ?> m, String key) {
        Object v = require(m, key);
        if (v instanceof Number n) {
            return n.longValue();
        }
        throw new IllegalArgumentException("not a number: " + key);
    }

    private static Bounty readBounty(Map<?, ?> m) {
        Object claimedBy = m.get("claimed-by");
        Object resolvedAt = m.get("resolved-at");
        return new Bounty((int) number(m, "id"),
                UUID.fromString(require(m, "target").toString()),
                require(m, "target-name").toString(),
                UUID.fromString(require(m, "poster").toString()),
                require(m, "poster-name").toString(),
                number(m, "amount"),
                Instant.ofEpochMilli(number(m, "created-at")),
                Instant.ofEpochMilli(number(m, "expires-at")),
                BountyStatus.valueOf(require(m, "status").toString()),
                claimedBy == null ? null : UUID.fromString(claimedBy.toString()),
                resolvedAt == null ? null : Instant.ofEpochMilli(((Number) resolvedAt).longValue()));
    }

    private static Notice readNotice(Map<?, ?> m) {
        return new Notice(UUID.fromString(require(m, "player").toString()),
                NoticeType.valueOf(require(m, "type").toString()),
                (int) number(m, "bounty-id"),
                number(m, "amount"),
                require(m, "other").toString());
    }
}
