package net.prismarin.shared.moderation;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.prismarin.shared.storage.DocumentStore;

public final class AltStore {

    static final String ACCOUNTS = "alt-accounts";
    static final String ADDRESSES = "alt-addresses";

    private static final int LOCKS = 64;

    public record Link(UUID uuid, String name, long firstSeen, long lastSeen, int sharedAddresses) {
    }

    record Seen(String hash, long first, long last) {
    }

    record Account(String uuid, String name, long first, long last, List<Seen> addresses) {

        Account {
            addresses = addresses == null ? List.of() : List.copyOf(addresses);
        }
    }

    record Member(String uuid, String name, long first, long last) {
    }

    record Address(String hash, List<Member> accounts) {

        Address {
            accounts = accounts == null ? List.of() : List.copyOf(accounts);
        }
    }

    private final DocumentStore documents;
    private final Clock clock;
    private final int maxAddresses;
    private final int sharedLimit;
    private final long retentionMillis;
    private final Gson gson = new Gson();
    private final Object[] locks = new Object[LOCKS];

    public AltStore(DocumentStore documents, Clock clock, int maxAddresses, int sharedLimit, long retentionMillis) {
        this.documents = Objects.requireNonNull(documents, "documents");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.maxAddresses = Math.max(1, maxAddresses);
        this.sharedLimit = Math.max(1, sharedLimit);
        this.retentionMillis = Math.max(1L, retentionMillis);
        for (int index = 0; index < LOCKS; index++) {
            locks[index] = new Object();
        }
    }

    public void record(UUID player, String name, String addressHash) {
        Objects.requireNonNull(player, "player");
        if (addressHash == null || addressHash.isBlank()) {
            return;
        }
        long now = clock.millis();
        String id = player.toString();
        String cleanName = name == null ? "" : name;
        synchronized (lock(ACCOUNTS + id)) {
            Optional<Account> stored = account(id);
            List<Seen> addresses = new ArrayList<>();
            long first = stored.map(Account::first).orElse(now);
            boolean found = false;
            for (Seen seen : stored.map(Account::addresses).orElse(List.of())) {
                if (seen.hash().equals(addressHash)) {
                    addresses.add(new Seen(seen.hash(), seen.first(), now));
                    found = true;
                } else if (fresh(seen.last(), now)) {
                    addresses.add(seen);
                }
            }
            if (!found) {
                addresses.add(new Seen(addressHash, now, now));
            }
            addresses.sort(Comparator.comparingLong(Seen::last).reversed());
            List<Seen> kept = addresses.subList(0, Math.min(maxAddresses, addresses.size()));
            documents.save(ACCOUNTS, id, gson.toJson(new Account(id, cleanName, first, now, kept)));
        }
        synchronized (lock(ADDRESSES + addressHash)) {
            Optional<Address> stored = address(addressHash);
            List<Member> members = new ArrayList<>();
            boolean found = false;
            for (Member member : stored.map(Address::accounts).orElse(List.of())) {
                if (member.uuid().equals(id)) {
                    members.add(new Member(id, cleanName, member.first(), now));
                    found = true;
                } else if (fresh(member.last(), now)) {
                    members.add(member);
                }
            }
            if (!found) {
                members.add(new Member(id, cleanName, now, now));
            }
            members.sort(Comparator.comparingLong(Member::last).reversed());
            List<Member> kept = members.subList(0, Math.min(sharedLimit + 1, members.size()));
            documents.save(ADDRESSES, addressHash, gson.toJson(new Address(addressHash, kept)));
        }
    }

    public List<Link> alts(UUID player) {
        long now = clock.millis();
        String id = player.toString();
        Optional<Account> account = account(id);
        if (account.isEmpty()) {
            return List.of();
        }
        Map<String, long[]> found = new LinkedHashMap<>();
        Map<String, String> names = new LinkedHashMap<>();
        for (Seen seen : account.get().addresses()) {
            if (!fresh(seen.last(), now)) {
                continue;
            }
            Optional<Address> address = address(seen.hash());
            if (address.isEmpty()) {
                continue;
            }
            List<Member> members = address.get().accounts().stream().filter(member -> fresh(member.last(), now))
                    .toList();
            if (members.size() > sharedLimit) {
                continue;
            }
            for (Member member : members) {
                if (member.uuid().equals(id)) {
                    continue;
                }
                long[] values = found.computeIfAbsent(member.uuid(), ignored -> new long[]{Long.MAX_VALUE, 0L, 0L});
                values[0] = Math.min(values[0], member.first());
                values[1] = Math.max(values[1], member.last());
                values[2]++;
                names.putIfAbsent(member.uuid(), member.name());
            }
        }
        List<Link> links = new ArrayList<>();
        for (Map.Entry<String, long[]> entry : found.entrySet()) {
            UUID uuid;
            try {
                uuid = UUID.fromString(entry.getKey());
            } catch (IllegalArgumentException e) {
                continue;
            }
            long[] values = entry.getValue();
            Optional<Account> other = account(entry.getKey());
            String name = other.map(Account::name).filter(value -> !value.isBlank())
                    .orElse(names.getOrDefault(entry.getKey(), ""));
            links.add(new Link(uuid, name, other.map(Account::first).orElse(values[0]),
                    Math.max(values[1], other.map(Account::last).orElse(0L)), (int) values[2]));
        }
        links.sort(Comparator.comparingLong(Link::lastSeen).reversed());
        return List.copyOf(links);
    }

    public int prune() {
        long now = clock.millis();
        int removed = 0;
        for (Map.Entry<String, String> entry : documents.entries(ACCOUNTS).entrySet()) {
            synchronized (lock(ACCOUNTS + entry.getKey())) {
                Optional<Account> account = parse(entry.getValue(), Account.class);
                if (account.isEmpty()) {
                    continue;
                }
                List<Seen> kept = account.get().addresses().stream().filter(seen -> fresh(seen.last(), now)).toList();
                if (kept.isEmpty() || !fresh(account.get().last(), now)) {
                    documents.delete(ACCOUNTS, entry.getKey());
                    removed++;
                } else if (kept.size() < account.get().addresses().size()) {
                    Account current = account.get();
                    documents.save(ACCOUNTS, entry.getKey(), gson.toJson(new Account(current.uuid(), current.name(),
                            current.first(), current.last(), kept)));
                }
            }
        }
        for (Map.Entry<String, String> entry : documents.entries(ADDRESSES).entrySet()) {
            synchronized (lock(ADDRESSES + entry.getKey())) {
                Optional<Address> address = parse(entry.getValue(), Address.class);
                if (address.isEmpty()) {
                    continue;
                }
                List<Member> kept = address.get().accounts().stream().filter(member -> fresh(member.last(), now))
                        .toList();
                if (kept.isEmpty()) {
                    documents.delete(ADDRESSES, entry.getKey());
                    removed++;
                } else if (kept.size() < address.get().accounts().size()) {
                    documents.save(ADDRESSES, entry.getKey(), gson.toJson(new Address(address.get().hash(), kept)));
                }
            }
        }
        return removed;
    }

    private boolean fresh(long last, long now) {
        return now - last <= retentionMillis;
    }

    private Optional<Account> account(String id) {
        return documents.find(ACCOUNTS, id).flatMap(json -> parse(json, Account.class));
    }

    private Optional<Address> address(String hash) {
        return documents.find(ADDRESSES, hash).flatMap(json -> parse(json, Address.class));
    }

    private <T> Optional<T> parse(String json, Class<T> type) {
        try {
            return Optional.ofNullable(gson.fromJson(json, type));
        } catch (JsonSyntaxException e) {
            return Optional.empty();
        }
    }

    private Object lock(String key) {
        return locks[Math.floorMod(key.hashCode(), LOCKS)];
    }
}
