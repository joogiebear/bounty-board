package dev.joogiebear.bountyboard.core;

import java.io.IOException;
import java.io.UncheckedIOException;
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
            if (containsNullByte(file)) {
                moveCorruptAside();
                return new StoreData(List.of(), List.of(), 1);
            }
            String content = Files.readString(file);
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.loadFromString(content);
            return readData(yaml);
        } catch (Exception e) {
            moveCorruptAside();
            return new StoreData(List.of(), List.of(), 1);
        }
    }

    private static boolean containsNullByte(Path file) throws IOException {
        try (var is = Files.newInputStream(file)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) != -1) {
                for (int i = 0; i < n; i++) {
                    if (buf[i] == 0) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public void save(StoreData data) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("next-id", data.nextId());

        List<Object> bountyList = new ArrayList<>();
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
            bountyList.add(m);
        }
        yaml.set("bounties", bountyList);

        List<Object> noticeList = new ArrayList<>();
        for (Notice n : data.notices()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("player", n.player().toString());
            m.put("type", n.type().name());
            m.put("bounty-id", n.bountyId());
            m.put("amount", n.amount());
            m.put("other", n.other());
            noticeList.add(m);
        }
        yaml.set("notices", noticeList);

        Path target = file.toAbsolutePath();
        Path parent = target.getParent();
        try {
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path temp = Files.createTempFile(parent, ".bounty-", ".tmp");
            try {
                yaml.save(temp.toString());
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception e) {
                Files.deleteIfExists(temp);
                throw e;
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void moveCorruptAside() {
        try {
            Path corrupt = file.resolveSibling(file.getFileName() + ".corrupt-" + System.currentTimeMillis());
            Files.move(file, corrupt, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {
        }
    }

    private static StoreData readData(YamlConfiguration yaml) {
        int nextId = yaml.getInt("next-id", 1);

        List<Bounty> bounties = new ArrayList<>();
        List<?> rawBounties = yaml.getList("bounties");
        if (rawBounties != null) {
            for (Object obj : rawBounties) {
                if (obj instanceof Map<?, ?> m) {
                    bounties.add(readBounty(m));
                }
            }
        }

        List<Notice> notices = new ArrayList<>();
        List<?> rawNotices = yaml.getList("notices");
        if (rawNotices != null) {
            for (Object obj : rawNotices) {
                if (obj instanceof Map<?, ?> m) {
                    notices.add(readNotice(m));
                }
            }
        }

        return new StoreData(bounties, notices, nextId);
    }

    private static Bounty readBounty(Map<?, ?> m) {
        Object claimedBy = m.get("claimed-by");
        Object resolvedAt = m.get("resolved-at");
        return new Bounty(
                asInt(m.get("id")),
                UUID.fromString(str(m.get("target"))),
                str(m.get("target-name")),
                UUID.fromString(str(m.get("poster"))),
                str(m.get("poster-name")),
                asLong(m.get("amount")),
                Instant.ofEpochMilli(asLong(m.get("created-at"))),
                Instant.ofEpochMilli(asLong(m.get("expires-at"))),
                BountyStatus.valueOf(str(m.get("status"))),
                claimedBy != null ? UUID.fromString(str(claimedBy)) : null,
                resolvedAt != null ? Instant.ofEpochMilli(asLong(resolvedAt)) : null
        );
    }

    private static Notice readNotice(Map<?, ?> m) {
        return new Notice(
                UUID.fromString(str(m.get("player"))),
                NoticeType.valueOf(str(m.get("type"))),
                asInt(m.get("bounty-id")),
                asLong(m.get("amount")),
                str(m.get("other"))
        );
    }

    private static String str(Object value) {
        return value == null ? null : value.toString();
    }

    private static int asInt(Object value) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        return Integer.parseInt(value.toString());
    }

    private static long asLong(Object value) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        return Long.parseLong(value.toString());
    }
}
