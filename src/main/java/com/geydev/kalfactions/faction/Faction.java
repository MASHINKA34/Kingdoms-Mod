package com.geydev.kalfactions.faction;

import com.geydev.kalfactions.claim.ClaimKey;
import com.geydev.kalfactions.economy.PriceMath;
import com.geydev.kalfactions.economy.Treasury;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

public final class Faction {
    private static final String TAG_ID = "id";
    private static final String TAG_NAME = "name";
    private static final String TAG_OWNER = "owner";
    private static final String TAG_COLOR = "color";
    private static final String TAG_ICON = "icon";
    private static final String TAG_BONUS = "bonus";
    private static final String TAG_BONUSES = "bonuses";
    private static final String TAG_EMBLEM = "emblem";
    private static final String TAG_EMBLEM_URL = "emblemUrl";
    private static final String TAG_INTERNAL_PVP = "internalPvp";
    private static final String TAG_ALLIES = "allies";
    private static final String TAG_OUTPOSTS = "outposts";
    private static final String TAG_PROTECTED_CLAIMS = "protectedClaims";
    private static final String TAG_CREATED_AT = "createdAt";
    private static final String TAG_TREASURY = "treasury";
    private static final String TAG_INFLUENCE = "influence";
    private static final String TAG_MEMBERS = "members";
    private static final String TAG_CLAIMS = "claims";
    private static final String TAG_CLAIM_KEY = "key";
    private static final String TAG_CLAIM_PRICE = "paidPrice";

    public static final int EMBLEM_SIZE = 16;
    public static final int EMBLEM_PIXELS = EMBLEM_SIZE * EMBLEM_SIZE;
    public static final int UPLOADED_EMBLEM_PIXELS = 32 * 32;
    public static final int MAX_EMBLEM_URL_LENGTH = 256;

    public static boolean isValidEmblemLength(int length) {
        return length == EMBLEM_PIXELS || length == UPLOADED_EMBLEM_PIXELS;
    }

    private final UUID id;
    private final long createdAtEpochMillis;
    private final Map<UUID, FactionMember> members;
    private final Map<ClaimKey, Long> claims;
    private final Treasury treasury;
    private String name;
    private UUID ownerId;
    private int color;
    private ResourceLocation iconId;
    private Set<FactionBonus> bonuses;
    private int[] emblem;
    private String emblemUrl;
    private boolean internalPvp;
    private final Set<UUID> allies;
    private final Map<UUID, Outpost> outposts;
    private final Set<ClaimKey> protectedClaims;
    private long influence;

    Faction(
        UUID id,
        String name,
        UUID ownerId,
        int color,
        ResourceLocation iconId,
        Set<FactionBonus> bonuses,
        boolean internalPvp,
        long createdAtEpochMillis
    ) {
        this(
            id,
            name,
            ownerId,
            color,
            iconId,
            bonuses,
            internalPvp,
            createdAtEpochMillis,
            new Treasury(),
            0L,
            Map.of(ownerId, new FactionMember(ownerId, FactionRole.LEADER)),
            Map.of()
        );
    }

    private Faction(
        UUID id,
        String name,
        UUID ownerId,
        int color,
        ResourceLocation iconId,
        Set<FactionBonus> bonuses,
        boolean internalPvp,
        long createdAtEpochMillis,
        Treasury treasury,
        long influence,
        Map<UUID, FactionMember> members,
        Map<ClaimKey, Long> claims
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
        this.ownerId = Objects.requireNonNull(ownerId, "ownerId");
        this.color = color;
        this.iconId = Objects.requireNonNull(iconId, "iconId");
        this.bonuses = sanitizeBonuses(bonuses);
        this.emblem = new int[0];
        this.emblemUrl = "";
        this.internalPvp = internalPvp;
        this.allies = new LinkedHashSet<>();
        this.outposts = new LinkedHashMap<>();
        this.protectedClaims = new LinkedHashSet<>();
        this.createdAtEpochMillis = Math.max(0L, createdAtEpochMillis);
        this.treasury = Objects.requireNonNull(treasury, "treasury");
        this.influence = Math.max(0L, influence);
        this.members = new LinkedHashMap<>(members);
        this.claims = new LinkedHashMap<>(claims);
        enforceLeadershipInvariant();
    }

    private static Set<FactionBonus> sanitizeBonuses(Set<FactionBonus> bonuses) {
        Objects.requireNonNull(bonuses, "bonuses");
        EnumSet<FactionBonus> copy = EnumSet.noneOf(FactionBonus.class);
        copy.addAll(bonuses);
        copy.remove(null);
        if (copy.isEmpty()) {
            copy.add(FactionBonus.TRADERS);
        }
        return copy;
    }

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    public UUID ownerId() {
        return ownerId;
    }

    public UUID leaderId() {
        return ownerId;
    }

    public int color() {
        return color;
    }

    public ResourceLocation iconId() {
        return iconId;
    }

    public Set<FactionBonus> bonuses() {
        return Set.copyOf(bonuses);
    }

    public boolean hasBonus(FactionBonus bonus) {
        return bonuses.contains(bonus);
    }

    public double claimDiscount() {
        double discount = 0.0D;
        for (FactionBonus bonus : bonuses) {
            discount = Math.max(discount, bonus.claimDiscount());
        }
        return discount;
    }

    public int[] emblem() {
        return emblem.clone();
    }

    public boolean hasEmblem() {
        return isValidEmblemLength(emblem.length) || !emblemUrl.isEmpty();
    }

    public String emblemUrl() {
        return emblemUrl;
    }

    public boolean internalPvp() {
        return internalPvp;
    }

    public Set<UUID> allies() {
        return Set.copyOf(allies);
    }

    public boolean isAlliedWith(UUID factionId) {
        return factionId != null && allies.contains(factionId);
    }

    boolean addAlly(UUID factionId) {
        return !id.equals(factionId) && allies.add(factionId);
    }

    boolean removeAlly(UUID factionId) {
        return allies.remove(factionId);
    }

    public Collection<Outpost> outposts() {
        return List.copyOf(outposts.values());
    }

    public int outpostCount() {
        return outposts.size();
    }

    public Set<ClaimKey> outpostChunks() {
        Set<ClaimKey> all = new LinkedHashSet<>();
        for (Outpost outpost : outposts.values()) {
            all.addAll(outpost.chunks());
        }
        return all;
    }

    public boolean isOutpostChunk(ClaimKey key) {
        return outposts.values().stream().anyMatch(outpost -> outpost.chunks().contains(key));
    }

    public Optional<Outpost> outpostAt(ClaimKey key) {
        return outposts.values().stream().filter(outpost -> outpost.chunks().contains(key)).findFirst();
    }

    public Optional<Outpost> outpostByCore(ResourceKey<Level> dimension, BlockPos core) {
        return outposts.values().stream()
            .filter(outpost -> outpost.dimension().equals(dimension) && outpost.core().equals(core))
            .findFirst();
    }

    public Optional<Outpost> outpost(UUID outpostId) {
        return Optional.ofNullable(outposts.get(outpostId));
    }

    void addOutpost(Outpost outpost) {
        outposts.put(outpost.id(), outpost);
    }

    Optional<Outpost> removeOutpost(UUID outpostId) {
        return Optional.ofNullable(outposts.remove(outpostId));
    }

    public Set<ClaimKey> protectedClaims() {
        return Set.copyOf(protectedClaims);
    }

    public boolean isProtectedClaim(ClaimKey key) {
        return protectedClaims.contains(key);
    }

    void addProtectedClaim(ClaimKey key) {
        protectedClaims.add(key);
    }

    public long createdAtEpochMillis() {
        return createdAtEpochMillis;
    }

    public long treasuryBalance() {
        return treasury.balance();
    }

    public long influence() {
        return influence;
    }

    public int memberCount() {
        return members.size();
    }

    public int claimCount() {
        return claims.size();
    }

    public Map<UUID, FactionMember> members() {
        return Map.copyOf(members);
    }

    public Set<UUID> officers() {
        return members.values().stream()
            .filter(member -> member.role() == FactionRole.OFFICER)
            .map(FactionMember::playerId)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public Set<UUID> regularMembers() {
        return members.values().stream()
            .filter(member -> member.role() == FactionRole.MEMBER)
            .map(FactionMember::playerId)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public Set<ClaimKey> claims() {
        return Set.copyOf(claims.keySet());
    }

    public Map<ClaimKey, Long> claimPrices() {
        return Map.copyOf(claims);
    }

    public Optional<FactionMember> member(UUID playerId) {
        return Optional.ofNullable(members.get(playerId));
    }

    public boolean isMember(UUID playerId) {
        return members.containsKey(playerId);
    }

    public boolean hasClaim(ClaimKey key) {
        return claims.containsKey(key);
    }

    public boolean hasClaimsIn(ResourceKey<Level> dimension) {
        return claims.keySet().stream().anyMatch(key -> key.dimension().equals(dimension));
    }

    public Optional<FactionRole> roleOf(UUID playerId) {
        return member(playerId).map(FactionMember::role);
    }

    void rename(String newName) {
        name = Objects.requireNonNull(newName, "newName");
    }

    void setColor(int newColor) {
        color = newColor;
    }

    void setIconId(ResourceLocation newIconId) {
        iconId = Objects.requireNonNull(newIconId, "newIconId");
    }

    void setBonuses(Set<FactionBonus> newBonuses) {
        bonuses = sanitizeBonuses(newBonuses);
    }

    void setEmblem(int[] pixels, String url) {
        emblem = pixels != null && isValidEmblemLength(pixels.length) ? pixels.clone() : new int[0];
        String cleaned = url == null ? "" : url.strip();
        emblemUrl = cleaned.length() > MAX_EMBLEM_URL_LENGTH ? cleaned.substring(0, MAX_EMBLEM_URL_LENGTH) : cleaned;
    }

    void setInternalPvp(boolean enabled) {
        internalPvp = enabled;
    }

    void addMember(UUID playerId) {
        members.put(playerId, new FactionMember(playerId, FactionRole.MEMBER));
    }

    void removeMember(UUID playerId) {
        members.remove(playerId);
    }

    void setMemberRole(UUID playerId, FactionRole role) {
        Objects.requireNonNull(members.get(playerId), "Unknown faction member");
        members.put(playerId, new FactionMember(playerId, role));
    }

    void transferLeadership(UUID newOwnerId) {
        FactionMember newOwner = Objects.requireNonNull(members.get(newOwnerId), "New owner must be a faction member");
        members.put(ownerId, new FactionMember(ownerId, FactionRole.OFFICER));
        members.put(newOwnerId, new FactionMember(newOwnerId, FactionRole.LEADER));
        ownerId = newOwnerId;
    }

    void addClaim(ClaimKey key, long paidPrice) {
        claims.put(key, Math.max(0L, paidPrice));
    }

    long removeClaim(ClaimKey key) {
        Long paidPrice = claims.remove(key);
        return paidPrice == null ? -1L : paidPrice;
    }

    boolean deposit(long amount) {
        return treasury.deposit(amount);
    }

    boolean withdraw(long amount) {
        return treasury.withdraw(amount);
    }

    boolean canDeposit(long amount) {
        return treasury.canDeposit(amount);
    }

    boolean canWithdraw(long amount) {
        return treasury.canWithdraw(amount);
    }

    void addInfluence(long amount) {
        influence = PriceMath.saturatedAdd(influence, amount);
    }

    boolean spendInfluence(long amount) {
        if (amount < 0L || influence < amount) {
            return false;
        }
        influence -= amount;
        return true;
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID(TAG_ID, id);
        tag.putString(TAG_NAME, name);
        tag.putUUID(TAG_OWNER, ownerId);
        tag.putInt(TAG_COLOR, color);
        tag.putString(TAG_ICON, iconId.toString());
        ListTag bonusesTag = new ListTag();
        bonuses.stream()
            .sorted(Comparator.comparing(FactionBonus::name))
            .forEach(value -> bonusesTag.add(net.minecraft.nbt.StringTag.valueOf(value.name())));
        tag.put(TAG_BONUSES, bonusesTag);
        if (isValidEmblemLength(emblem.length)) {
            tag.putIntArray(TAG_EMBLEM, emblem.clone());
        }
        if (!emblemUrl.isEmpty()) {
            tag.putString(TAG_EMBLEM_URL, emblemUrl);
        }
        tag.putBoolean(TAG_INTERNAL_PVP, internalPvp);
        ListTag alliesTag = new ListTag();
        allies.stream()
            .sorted(Comparator.comparing(UUID::toString))
            .forEach(ally -> alliesTag.add(net.minecraft.nbt.NbtUtils.createUUID(ally)));
        tag.put(TAG_ALLIES, alliesTag);
        ListTag outpostsTag = new ListTag();
        outposts.values().stream()
            .sorted(Comparator.comparing(outpost -> outpost.id().toString()))
            .forEach(outpost -> outpostsTag.add(outpost.save()));
        tag.put(TAG_OUTPOSTS, outpostsTag);
        ListTag protectedTag = new ListTag();
        protectedClaims.stream().sorted().forEach(claim -> protectedTag.add(claim.save()));
        tag.put(TAG_PROTECTED_CLAIMS, protectedTag);
        tag.putLong(TAG_CREATED_AT, createdAtEpochMillis);
        tag.put(TAG_TREASURY, treasury.save());
        tag.putLong(TAG_INFLUENCE, influence);

        ListTag membersTag = new ListTag();
        members.values().stream()
            .sorted(Comparator.comparing(member -> member.playerId().toString()))
            .map(FactionMember::save)
            .forEach(membersTag::add);
        tag.put(TAG_MEMBERS, membersTag);

        ListTag claimsTag = new ListTag();
        claims.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(entry -> {
                CompoundTag claimTag = new CompoundTag();
                claimTag.put(TAG_CLAIM_KEY, entry.getKey().save());
                claimTag.putLong(TAG_CLAIM_PRICE, entry.getValue());
                return claimTag;
            })
            .forEach(claimsTag::add);
        tag.put(TAG_CLAIMS, claimsTag);
        return tag;
    }

    static Optional<Faction> load(CompoundTag tag) {
        if (!tag.hasUUID(TAG_ID) || !tag.hasUUID(TAG_OWNER)) {
            return Optional.empty();
        }

        UUID ownerId = tag.getUUID(TAG_OWNER);
        ResourceLocation iconId = ResourceLocation.tryParse(tag.getString(TAG_ICON));
        if (iconId == null) {
            return Optional.empty();
        }
        EnumSet<FactionBonus> bonuses = EnumSet.noneOf(FactionBonus.class);
        ListTag bonusesTag = tag.getList(TAG_BONUSES, Tag.TAG_STRING);
        for (int index = 0; index < bonusesTag.size(); index++) {
            try {
                bonuses.add(FactionBonus.valueOf(bonusesTag.getString(index).toUpperCase(java.util.Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (bonuses.isEmpty() && tag.contains(TAG_BONUS, Tag.TAG_STRING)) {
            try {
                bonuses.add(FactionBonus.valueOf(tag.getString(TAG_BONUS).toUpperCase(java.util.Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (bonuses.isEmpty()) {
            bonuses.add(FactionBonus.TRADERS);
        }
        Map<UUID, FactionMember> members = new LinkedHashMap<>();
        ListTag membersTag = tag.getList(TAG_MEMBERS, Tag.TAG_COMPOUND);
        for (int index = 0; index < membersTag.size(); index++) {
            Optional<FactionMember> loaded = FactionMember.load(membersTag.getCompound(index));
            if (loaded.isEmpty() || members.putIfAbsent(loaded.get().playerId(), loaded.get()) != null) {
                return Optional.empty();
            }
        }
        if (!members.containsKey(ownerId)) {
            return Optional.empty();
        }

        Map<ClaimKey, Long> claims = new LinkedHashMap<>();
        ListTag claimsTag = tag.getList(TAG_CLAIMS, Tag.TAG_COMPOUND);
        for (int index = 0; index < claimsTag.size(); index++) {
            CompoundTag claimTag = claimsTag.getCompound(index);
            Optional<ClaimKey> key = ClaimKey.load(claimTag.getCompound(TAG_CLAIM_KEY));
            if (key.isEmpty() || claims.putIfAbsent(key.get(), Math.max(0L, claimTag.getLong(TAG_CLAIM_PRICE))) != null) {
                return Optional.empty();
            }
        }

        Treasury treasury = Treasury.load(tag.getCompound(TAG_TREASURY));
        Faction faction = new Faction(
            tag.getUUID(TAG_ID),
            tag.getString(TAG_NAME),
            ownerId,
            tag.getInt(TAG_COLOR),
            iconId,
            bonuses,
            tag.getBoolean(TAG_INTERNAL_PVP),
            tag.getLong(TAG_CREATED_AT),
            treasury,
            tag.getLong(TAG_INFLUENCE),
            members,
            claims
        );
        faction.setEmblem(
            tag.contains(TAG_EMBLEM, Tag.TAG_INT_ARRAY) ? tag.getIntArray(TAG_EMBLEM) : null,
            tag.getString(TAG_EMBLEM_URL)
        );
        ListTag alliesTag = tag.getList(TAG_ALLIES, Tag.TAG_INT_ARRAY);
        for (int index = 0; index < alliesTag.size(); index++) {
            try {
                faction.addAlly(net.minecraft.nbt.NbtUtils.loadUUID(alliesTag.get(index)));
            } catch (IllegalArgumentException ignored) {
            }
        }
        ListTag outpostsTag = tag.getList(TAG_OUTPOSTS, Tag.TAG_COMPOUND);
        for (int index = 0; index < outpostsTag.size(); index++) {
            Outpost.load(outpostsTag.getCompound(index)).ifPresent(faction::addOutpost);
        }
        ListTag protectedTag = tag.getList(TAG_PROTECTED_CLAIMS, Tag.TAG_COMPOUND);
        for (int index = 0; index < protectedTag.size(); index++) {
            ClaimKey.load(protectedTag.getCompound(index)).ifPresent(faction::addProtectedClaim);
        }
        return Optional.of(faction);
    }

    private void enforceLeadershipInvariant() {
        FactionMember owner = members.get(ownerId);
        if (owner == null) {
            throw new IllegalArgumentException("Faction owner must be a member");
        }

        List<UUID> playerIds = new ArrayList<>(members.keySet());
        for (UUID playerId : playerIds) {
            FactionMember member = members.get(playerId);
            FactionRole role = playerId.equals(ownerId)
                ? FactionRole.LEADER
                : member.role() == FactionRole.LEADER ? FactionRole.OFFICER : member.role();
            if (role != member.role()) {
                members.put(playerId, new FactionMember(playerId, role));
            }
        }
    }

    public record Outpost(UUID id, ResourceKey<Level> dimension, BlockPos core, Set<ClaimKey> chunks) {
        private static final String TAG_OUTPOST_ID = "id";
        private static final String TAG_OUTPOST_CORE = "core";
        private static final String TAG_OUTPOST_CHUNKS = "chunks";

        public Outpost {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(dimension, "dimension");
            core = core.immutable();
            chunks = Set.copyOf(chunks);
        }

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID(TAG_OUTPOST_ID, id);
            tag.put(TAG_OUTPOST_CORE, NbtUtils.writeBlockPos(core));
            ListTag chunksTag = new ListTag();
            chunks.stream().sorted().forEach(chunk -> chunksTag.add(chunk.save()));
            tag.put(TAG_OUTPOST_CHUNKS, chunksTag);
            return tag;
        }

        static Optional<Outpost> load(CompoundTag tag) {
            if (!tag.hasUUID(TAG_OUTPOST_ID)) {
                return Optional.empty();
            }
            Optional<BlockPos> core = NbtUtils.readBlockPos(tag, TAG_OUTPOST_CORE);
            if (core.isEmpty()) {
                return Optional.empty();
            }
            Set<ClaimKey> chunks = new LinkedHashSet<>();
            ListTag chunksTag = tag.getList(TAG_OUTPOST_CHUNKS, Tag.TAG_COMPOUND);
            for (int index = 0; index < chunksTag.size(); index++) {
                Optional<ClaimKey> key = ClaimKey.load(chunksTag.getCompound(index));
                if (key.isEmpty()) {
                    return Optional.empty();
                }
                chunks.add(key.get());
            }
            if (chunks.isEmpty()) {
                return Optional.empty();
            }
            ResourceKey<Level> dimension = chunks.iterator().next().dimension();
            return Optional.of(new Outpost(tag.getUUID(TAG_OUTPOST_ID), dimension, core.get(), chunks));
        }
    }
}
