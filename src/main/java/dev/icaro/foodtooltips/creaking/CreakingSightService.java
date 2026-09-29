package dev.icaro.foodtooltips.creaking;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams;
import dev.icaro.foodtooltips.skills.AccessoryBagService;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.citizensnpcs.api.CitizensAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

/**
 * The Pale Oak Log Collection's own Creaking Sight glow (Talisman/Ring/Artifact - see {@code
 * item.ForagingCollectionsItemsService}): reveals nearby creatures with a colored glowing
 * outline, visible ONLY to the equipping player, through walls - matching the Creaking's own
 * "you can see it, but only you" theme. Deliberately NOT {@link Entity#setGlowing(boolean)}
 * (that flips the real, server-wide flag - every player would see it, and it would collide
 * with any other plugin/effect that also uses the real flag). Instead this sends the SAME
 * per-player packets a real "glow API" plugin (ProtocolLib-based ones on SpigotMC do this same
 * trick) would, via PacketEvents:
 *
 * <ol>
 *   <li>An entity metadata packet ({@link WrapperPlayServerEntityMetadata}) with index 0 (the
 *   "shared flags" byte - on-fire/sneaking/sprinting/swimming/invisible/glowing/gliding,
 *   stable at this bit layout since Minecraft 1.9) carrying the entity's own REAL current
 *   flags (see {@link #trueSharedFlags}) OR'd with just the glow bit ({@value #GLOW_BIT}) -
 *   never a hardcoded byte, so a real glow/fire/invisibility another plugin already applied
 *   is never clobbered, and reverting (see {@link #revertOne}) just resends the true byte
 *   with no OR at all.
 *   <li>A virtual scoreboard team ({@link WrapperPlayServerTeams}) - real glow color is driven
 *   client-side by whatever scoreboard team (if any) the glowing entity belongs to, in that
 *   VIEWER's own view of the scoreboard. {@link #createTeamsIfNeeded} creates the 4 {@link
 *   GlowCategory} teams (once per player, never recreated - see its own doc) entirely via
 *   packets, never touching this server's real scoreboard, then {@link #addToTeam}/{@link
 *   #removeFromTeam} move an entity's own UUID (the standard "affect a non-player entity via
 *   a team" trick - team membership entries can be any string, not just player names, and the
 *   client accepts a raw UUID string as one) between them as its category changes.
 * </ol>
 *
 * <p>{@link #scanAll} runs every {@value #SCAN_INTERVAL_TICKS} ticks (never per-tick - see the
 * player's own explicit performance spec), each pass only searching the equipping player's own
 * {@code AccessoryBagService#creakingSightRange} radius, and only sending packets for an entity
 * that actually entered, left, or changed {@link GlowCategory} since the last pass - {@link
 * #tracked} is exactly that "already glowing, as of what color" memory, keyed by player then by
 * entity UUID (never a live {@link Entity} reference - a dead/unloaded one is looked back up
 * via {@link Bukkit#getEntity(UUID)} only at the moment a revert packet actually needs it,
 * never held onto). A range drop, an accessory removal, a death, a world change, or a quit all
 * clear/revert immediately (see {@link #clearAll}) rather than waiting for the next pass.
 *
 * <p>Entirely inert (never touches {@link PacketEvents}, since even referencing {@code
 * PacketEvents.getAPI()} without the real plugin backing it would misbehave) unless the
 * {@code packetevents} plugin is actually installed and enabled - checked once at {@link
 * #start()} - matching this project's own established soft-dependency pattern (WorldGuard/
 * Citizens/Sentinel/PlaceholderAPI). Citizens NPCs are excluded the same defensive way: only
 * asked about if the {@code Citizens} plugin is itself present.
 *
 * <p><b>Bedrock/Geyser:</b> {@code skills.BedrockPlayers#isBedrock} isn't consulted here at
 * all - the metadata glow bit itself works identically for Bedrock viewers connected through
 * Geyser (Geyser translates it directly into Bedrock's own entity flag), but Geyser's
 * translation of the scoreboard-team-color-for-glow trick is known to be inconsistent across
 * Geyser versions (some builds render every packet-glow color as the same default outline
 * regardless of team color). This is a client-translation limitation outside this plugin's
 * control, not a bug here - a Bedrock player equipping Creaking Sight still sees every
 * qualifying creature glow (the functional part of the feature), just not reliably recolored
 * per category.
 */
public final class CreakingSightService implements Listener {
    private static final int SCAN_INTERVAL_TICKS = 10;
    /** Entity metadata index 0 - the "shared flags" byte, stable since 1.9 (see this class's own doc). */
    private static final int SHARED_FLAGS_INDEX = 0;
    private static final byte FIRE_BIT = 0x01;
    private static final byte SNEAKING_BIT = 0x02;
    private static final byte SWIMMING_BIT = 0x10;
    private static final byte INVISIBLE_BIT = 0x20;
    private static final byte GLOW_BIT = 0x40;
    private static final byte GLIDING_BIT = (byte) 0x80;

    private final Plugin plugin;
    private final AccessoryBagService accessoryBag;
    /**
     * Whether the {@code packetevents} plugin is actually installed and enabled - {@code
     * null} until {@link #start()} resolves it (deliberately NOT computed in the constructor:
     * that would call {@link Bukkit#getPluginManager()}, which needs a live running server
     * and would break constructing this class in a plain unit test - see {@code
     * creaking.CreakingSightServiceTest}'s own doc for why that matters here specifically).
     */
    private Boolean packetEventsAvailable;
    /** Per-player: which entities (by UUID) they're currently made to glow, and in which color - see this class's own doc. */
    private final Map<UUID, Map<UUID, GlowCategory>> tracked = new HashMap<>();
    /** Players who've already received the 4 {@link GlowCategory} team CREATE packets - see {@link #createTeamsIfNeeded}. */
    private final Set<UUID> teamsInitialized = new HashSet<>();
    private BukkitTask task;

    public CreakingSightService(Plugin plugin, AccessoryBagService accessoryBag) {
        this.plugin = plugin;
        this.accessoryBag = accessoryBag;
    }

    /** Call once from {@code FoodTooltipsPlugin#onEnable} - a no-op (logged once) if PacketEvents isn't installed. */
    public void start() {
        this.packetEventsAvailable = Bukkit.getPluginManager().getPlugin("packetevents") != null;
        if (!this.packetEventsAvailable) {
            this.plugin.getLogger().info("PacketEvents não encontrado - o glow do Creaking Sight (Pale Oak) fica desativado.");
            return;
        }
        this.task = Bukkit.getScheduler().runTaskTimer(this.plugin, this::scanAll, SCAN_INTERVAL_TICKS, SCAN_INTERVAL_TICKS);
    }

    /** Call once from {@code FoodTooltipsPlugin#onDisable} - cancels the scan task and reverts every currently-online player's own glow packets, so nothing artificial survives a reload/shutdown. */
    public void stop() {
        if (this.task != null) {
            this.task.cancel();
            this.task = null;
        }
        if (Boolean.TRUE.equals(this.packetEventsAvailable)) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                this.clearAll(p);
            }
        }
        this.tracked.clear();
        this.teamsInitialized.clear();
    }

    /** No revert packets needed - a disconnecting client discards all of its own scoreboard/entity state on its own. Just drop the bookkeeping so nothing leaks. */
    @EventHandler
    public void quit(PlayerQuitEvent e) {
        this.tracked.remove(e.getPlayer().getUniqueId());
        this.teamsInitialized.remove(e.getPlayer().getUniqueId());
    }

    /** Every previously-glowing entity belonged to the OLD world - none of them are even loaded/relevant anymore, so clear immediately instead of waiting for the next scan to notice. */
    @EventHandler
    public void worldChange(PlayerChangedWorldEvent e) {
        this.clearAll(e.getPlayer());
    }

    /** Per the player's own spec ("Se o jogador ... morrer ... limpe imediatamente"). */
    @EventHandler
    public void death(PlayerDeathEvent e) {
        this.clearAll(e.getEntity());
    }

    /**
     * A dead creature can't be reverted (there's nothing left client-side to send a "true
     * flags" packet about - it's already been destroyed by vanilla's own death packet), so
     * this only ever needs to drop the bookkeeping, for every player who had it tracked -
     * matching the player's own "se a entidade morrer ... retire-a do estado rastreado".
     * MONITOR since this never cancels or reacts to anything else about the death itself.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void entityDeath(EntityDeathEvent e) {
        UUID id = e.getEntity().getUniqueId();
        for (Map<UUID, GlowCategory> perPlayer : this.tracked.values()) {
            perPlayer.remove(id);
        }
    }

    private void scanAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            this.scanOne(p);
        }
    }

    /**
     * One player's own pass: resolves their current range (0 if they have no Creaking Sight
     * accessory equipped right now - see {@link AccessoryBagService#creakingSightRange}),
     * finds every safely-classifiable creature within it (see {@link EntityClassifier}), and
     * only sends packets for what actually changed since last pass - see this class's own doc.
     */
    private void scanOne(Player p) {
        int range = this.accessoryBag.creakingSightRange(p);
        if (range <= 0) {
            this.clearAll(p);
            return;
        }
        Map<UUID, GlowCategory> current = this.tracked.computeIfAbsent(p.getUniqueId(), k -> new HashMap<>());
        boolean citizensInstalled = Bukkit.getPluginManager().getPlugin("Citizens") != null;
        Map<UUID, GlowCategory> nearbyNow = new HashMap<>();
        Map<UUID, Entity> nearbyEntities = new HashMap<>();
        for (Entity entity : p.getNearbyEntities(range, range, range)) {
            if (isCitizensNpc(citizensInstalled, entity)) {
                continue;
            }
            GlowCategory category = EntityClassifier.classify(entity);
            if (category == null) {
                continue;
            }
            nearbyNow.put(entity.getUniqueId(), category);
            nearbyEntities.put(entity.getUniqueId(), entity);
        }
        DiffResult diff = diff(current, nearbyNow);
        for (Map.Entry<UUID, GlowCategory> entry : diff.changed().entrySet()) {
            UUID id = entry.getKey();
            GlowCategory category = entry.getValue();
            GlowCategory previous = current.put(id, category);
            this.createTeamsIfNeeded(p);
            if (previous == null) {
                Entity entity = nearbyEntities.get(id);
                this.sendMetadata(p, entity.getEntityId(), (byte) (trueSharedFlags(entity) | GLOW_BIT));
            } else {
                this.removeFromTeam(p, previous, id);
            }
            this.addToTeam(p, category, id);
        }
        for (UUID id : diff.left()) {
            GlowCategory category = current.remove(id);
            this.revertOne(p, id, category);
        }
    }

    /**
     * Whether {@code entity} should be skipped because it's a Citizens NPC - {@code
     * citizensInstalled} is resolved by the caller (a plain {@link Bukkit#getPluginManager()}
     * check, see {@link #scanOne}) rather than read live in here, so this method itself never
     * touches a Bukkit static and is directly unit-testable: passing {@code false} proves
     * Citizens is never even queried when it isn't installed, without needing a live registry.
     */
    static boolean isCitizensNpc(boolean citizensInstalled, Entity entity) {
        return citizensInstalled && CitizensAPI.getNPCRegistry().isNPC(entity);
    }

    /**
     * Pure diff between what {@code current} already has glowing (for one player) and what's
     * actually classifiable-and-nearby right now ({@code nearbyNow}) - {@link
     * DiffResult#changed} is every entity that's new or switched {@link GlowCategory} since
     * last pass (needs a packet), {@link DiffResult#left} is every previously-tracked entity
     * no longer present at all (dropped out of range, died, stopped being classifiable, or -
     * when {@code nearbyNow} is empty, e.g. the accessory was just removed - literally
     * everything). No Bukkit/PacketEvents call in here at all, so it's directly unit-testable
     * with plain {@link Map}s - see {@code creaking.CreakingSightServiceTest}.
     */
    static DiffResult diff(Map<UUID, GlowCategory> current, Map<UUID, GlowCategory> nearbyNow) {
        Map<UUID, GlowCategory> changed = new HashMap<>();
        for (Map.Entry<UUID, GlowCategory> entry : nearbyNow.entrySet()) {
            if (current.get(entry.getKey()) != entry.getValue()) {
                changed.put(entry.getKey(), entry.getValue());
            }
        }
        Set<UUID> left = new HashSet<>(current.keySet());
        left.removeAll(nearbyNow.keySet());
        return new DiffResult(changed, left);
    }

    record DiffResult(Map<UUID, GlowCategory> changed, Set<UUID> left) {
    }

    /** Reverts every entity currently tracked for {@code p} (range dropped to 0, accessory removed, world changed, died...) and forgets them - see this class's own doc on when this is called. */
    private void clearAll(Player p) {
        Map<UUID, GlowCategory> current = this.tracked.get(p.getUniqueId());
        if (current == null || current.isEmpty()) {
            return;
        }
        for (Map.Entry<UUID, GlowCategory> entry : current.entrySet()) {
            this.revertOne(p, entry.getKey(), entry.getValue());
        }
        current.clear();
    }

    /** Removes {@code entityId} from its own {@code category} team and, if it's still resolvable (see {@link Bukkit#getEntity(UUID)}'s own doc - null if unloaded/gone), sends its real current flags back with no glow bit added - the "restore true metadata" half of {@link #trueSharedFlags}'s own contract. */
    private void revertOne(Player viewer, UUID entityId, GlowCategory category) {
        if (!Boolean.TRUE.equals(this.packetEventsAvailable)) {
            return;
        }
        this.removeFromTeam(viewer, category, entityId);
        Entity entity = Bukkit.getEntity(entityId);
        if (entity != null) {
            this.sendMetadata(viewer, entity.getEntityId(), trueSharedFlags(entity));
        }
    }

    /**
     * {@code entity}'s own real shared-flags byte, reconstructed from public API rather than
     * ever trusting a stale value - see this class's own doc for why. Sprinting (bit 0x08) is
     * deliberately always left unset: Bukkit exposes no generic "is this arbitrary Mob
     * sprinting" getter (only {@link Player} reliably tracks it), and every entity this
     * service ever touches is a non-player {@link org.bukkit.entity.Mob} by the time it
     * reaches here (see {@link EntityClassifier}) - a documented, low-impact simplification,
     * not an oversight.
     */
    static byte trueSharedFlags(Entity entity) {
        byte flags = 0;
        if (entity.getFireTicks() > 0 || entity.isVisualFire()) {
            flags |= FIRE_BIT;
        }
        if (entity.isSneaking()) {
            flags |= SNEAKING_BIT;
        }
        if (entity.isGlowing()) {
            flags |= GLOW_BIT;
        }
        if (entity instanceof LivingEntity living) {
            if (living.isSwimming()) {
                flags |= SWIMMING_BIT;
            }
            if (living.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
                flags |= INVISIBLE_BIT;
            }
            if (living.isGliding()) {
                flags |= GLIDING_BIT;
            }
        }
        return flags;
    }

    private void sendMetadata(Player viewer, int entityId, byte sharedFlags) {
        List<EntityData<?>> data = List.of(new EntityData<>(SHARED_FLAGS_INDEX, EntityDataTypes.BYTE, sharedFlags));
        PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, new WrapperPlayServerEntityMetadata(entityId, data));
    }

    /**
     * Creates {@code viewer}'s own 4 {@link GlowCategory} teams (packet-only - see this
     * class's own doc), the first time any glow packet is ever sent to them, and never again -
     * per the player's own explicit "evite recriar a mesma equipe continuamente", membership
     * afterward only ever changes via {@link #addToTeam}/{@link #removeFromTeam}.
     */
    private void createTeamsIfNeeded(Player viewer) {
        if (!this.teamsInitialized.add(viewer.getUniqueId())) {
            return;
        }
        for (GlowCategory category : GlowCategory.values()) {
            WrapperPlayServerTeams.ScoreBoardTeamInfo info = new WrapperPlayServerTeams.ScoreBoardTeamInfo(
                    Component.empty(), Component.empty(), Component.empty(),
                    WrapperPlayServerTeams.NameTagVisibility.ALWAYS, WrapperPlayServerTeams.CollisionRule.ALWAYS,
                    category.color(), WrapperPlayServerTeams.OptionData.NONE);
            PacketEvents.getAPI().getPlayerManager().sendPacket(viewer,
                    new WrapperPlayServerTeams(category.teamName(), WrapperPlayServerTeams.TeamMode.CREATE, info));
        }
    }

    private void addToTeam(Player viewer, GlowCategory category, UUID entityId) {
        PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, new WrapperPlayServerTeams(
                category.teamName(), WrapperPlayServerTeams.TeamMode.ADD_ENTITIES, Optional.empty(), entityId.toString()));
    }

    private void removeFromTeam(Player viewer, GlowCategory category, UUID entityId) {
        PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, new WrapperPlayServerTeams(
                category.teamName(), WrapperPlayServerTeams.TeamMode.REMOVE_ENTITIES, Optional.empty(), entityId.toString()));
    }
}
