package dev.icaro.foodtooltips.combat;

import dev.icaro.foodtooltips.i18n.Language;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Display;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Golem;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.WaterMob;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class MobVisualService {
    private static final String TAG = "foodtooltips_mob_label";
    private static final Pattern LEGACY = Pattern.compile("^\\[Lv\\.?\\s*\\d+].*\\d+/\\d+\u2764$");
    private final Plugin plugin;
    private final Map<UUID, TextDisplay> labels = new HashMap<UUID, TextDisplay>();
    private final Map<UUID, Long> renderState = new HashMap<UUID, Long>();
    private final Map<UUID, Set<UUID>> shownTo = new HashMap<UUID, Set<UUID>>();
    /** Mobs with a per-viewer localized name (see {@link #setLocalizedName}) - one TextDisplay passenger per language, only one of which is shown to each viewer. Suppresses the generic translated-species name in {@link #update}. */
    private final Map<UUID, TextDisplay> namePt = new HashMap<UUID, TextDisplay>();
    private final Map<UUID, TextDisplay> nameEn = new HashMap<UUID, TextDisplay>();
    private final Random random = new Random();
    private final float labelRange;
    private final float damageRange;
    private final double maxDistanceSquared;

    public MobVisualService(Plugin p) {
        this.plugin = p;
        this.labelRange = (float)p.getConfig().getDouble("mob-visuals.label-view-range", 0.12);
        this.damageRange = (float)p.getConfig().getDouble("mob-visuals.damage-view-range", 0.25);
        double max = p.getConfig().getDouble("mob-visuals.label-max-distance-blocks", 10.0);
        this.maxDistanceSquared = max * max;
        this.removeOrphans();
    }

    public void track(LivingEntity entity) {
        if (!this.supported(entity) || this.labels.containsKey(entity.getUniqueId())) {
            return;
        }
        this.clearLegacyName(entity);
        TextDisplay d = (TextDisplay)entity.getWorld().spawn(entity.getLocation(), TextDisplay.class, x -> {
            x.addScoreboardTag(TAG);
            x.setPersistent(false);
            x.setGravity(false);
            x.setInvulnerable(true);
            x.setBillboard(Display.Billboard.CENTER);
            x.setSeeThrough(true);
            x.setShadowed(true);
            x.setViewRange(this.labelRange);
            x.setTeleportDuration(0);
            x.setInterpolationDuration(0);
            x.setTransformation(new Transformation(new Vector3f(0.0f, 0.55f, 0.0f), new Quaternionf(), new Vector3f(1.0f, 1.0f, 1.0f), new Quaternionf()));
            x.setVisibleByDefault(false);
        });
        entity.addPassenger((Entity)d);
        this.labels.put(entity.getUniqueId(), d);
        this.update(entity);
    }

    /**
     * Gives {@code entity} a name shown above it that varies with each viewer's own
     * client language, instead of one fixed vanilla customName every viewer sees the
     * same. Vanilla nametags and this label's own generic species name (see
     * {@link #update}) are broadcast identically to everyone, so a custom flavor name
     * (not a real Minecraft translation key) can't vary per viewer through those -
     * this spawns one TextDisplay passenger per language and shows only the one
     * matching each viewer (see {@link #tick}). {@code entity} must already be
     * {@link #track}ed (true for anything spawned through the normal
     * CreatureSpawnEvent path, since that always tracks first).
     */
    public void setLocalizedName(LivingEntity entity, String pt, String en) {
        UUID id = entity.getUniqueId();
        if (!this.labels.containsKey(id) || this.namePt.containsKey(id)) {
            return;
        }
        this.namePt.put(id, this.spawnNameDisplay(entity, pt));
        this.nameEn.put(id, this.spawnNameDisplay(entity, en));
        this.update(entity);
    }

    private TextDisplay spawnNameDisplay(LivingEntity entity, String text) {
        TextDisplay d = (TextDisplay)entity.getWorld().spawn(entity.getLocation(), TextDisplay.class, x -> {
            x.addScoreboardTag(TAG);
            x.setPersistent(false);
            x.setGravity(false);
            x.setInvulnerable(true);
            x.setBillboard(Display.Billboard.CENTER);
            x.setSeeThrough(true);
            x.setShadowed(true);
            x.setViewRange(this.labelRange);
            x.setTeleportDuration(0);
            x.setInterpolationDuration(0);
            x.setTransformation(new Transformation(new Vector3f(0.0f, 0.8f, 0.0f), new Quaternionf(), new Vector3f(1.0f, 1.0f, 1.0f), new Quaternionf()));
            x.setVisibleByDefault(false);
            x.text(Component.text((String)text, (TextColor)NamedTextColor.RED));
        });
        entity.addPassenger((Entity)d);
        return d;
    }

    private boolean supported(LivingEntity e) {
        return e instanceof Enemy || e instanceof Player || e instanceof Animals || e instanceof WaterMob || e instanceof Golem;
    }

    public void tick() {
        Iterator<Map.Entry<UUID, TextDisplay>> it = this.labels.entrySet().iterator();
        while (it.hasNext()) {
            LivingEntity living;
            Map.Entry<UUID, TextDisplay> e = it.next();
            UUID id = e.getKey();
            TextDisplay d = e.getValue();
            Entity raw = Bukkit.getEntity((UUID)id);
            if (!(raw instanceof LivingEntity) || !(living = (LivingEntity)raw).isValid() || living.isDead() || !d.isValid()) {
                if (d.isValid()) {
                    d.remove();
                }
                it.remove();
                this.renderState.remove(id);
                this.shownTo.remove(id);
                TextDisplay pt = this.namePt.remove(id);
                if (pt != null && pt.isValid()) {
                    pt.remove();
                }
                TextDisplay en = this.nameEn.remove(id);
                if (en != null && en.isValid()) {
                    en.remove();
                }
                continue;
            }
            if (!living.getPassengers().contains(d)) {
                living.addPassenger((Entity)d);
            }
            TextDisplay pt = this.namePt.get(id);
            TextDisplay en = this.nameEn.get(id);
            if (pt != null && !living.getPassengers().contains(pt)) {
                living.addPassenger((Entity)pt);
            }
            if (en != null && !living.getPassengers().contains(en)) {
                living.addPassenger((Entity)en);
            }
            this.update(living);
            Set previous = this.shownTo.getOrDefault(id, Set.of());
            HashSet<UUID> nowVisible = new HashSet<UUID>();
            for (Player viewer : Bukkit.getOnlinePlayers()) {
                boolean visible;
                boolean bl = visible = viewer != living && viewer.getWorld() == living.getWorld() && viewer.getLocation().distanceSquared(living.getLocation()) <= this.maxDistanceSquared;
                if (visible) {
                    nowVisible.add(viewer.getUniqueId());
                    if (!previous.contains(viewer.getUniqueId())) {
                        viewer.showEntity(this.plugin, (Entity)d);
                    }
                } else if (previous.contains(viewer.getUniqueId())) {
                    viewer.hideEntity(this.plugin, (Entity)d);
                }
                if (pt == null || en == null) {
                    continue;
                }
                if (visible) {
                    TextDisplay shown = Language.of(viewer) == Language.PT ? pt : en;
                    TextDisplay hidden = shown == pt ? en : pt;
                    viewer.showEntity(this.plugin, (Entity)shown);
                    viewer.hideEntity(this.plugin, (Entity)hidden);
                } else {
                    viewer.hideEntity(this.plugin, (Entity)pt);
                    viewer.hideEntity(this.plugin, (Entity)en);
                }
            }
            this.shownTo.put(id, nowVisible);
        }
    }

    public int level(LivingEntity e) {
        AttributeInstance a = e.getAttribute(Attribute.MAX_HEALTH);
        double hp = a == null ? e.getHealth() : a.getValue();
        return Math.max(1, (int)Math.round(hp / 5.0));
    }

    public void update(LivingEntity e) {
        TextDisplay d = this.labels.get(e.getUniqueId());
        if (d == null) {
            return;
        }
        AttributeInstance a = e.getAttribute(Attribute.MAX_HEALTH);
        double max = a == null ? e.getHealth() : a.getValue();
        boolean showName = !(e instanceof Player) && e.customName() == null && !this.namePt.containsKey(e.getUniqueId());
        long hp = Math.max(0L, Math.round(e.getHealth()));
        long maxHp = Math.round(max);
        long key = (hp * 100000L + maxHp) * 2L + (long)(showName ? 1 : 0);
        Long last = this.renderState.get(e.getUniqueId());
        if (last != null && last == key) {
            return;
        }
        this.renderState.put(e.getUniqueId(), key);
        TextComponent prefix = e instanceof Enemy ? Component.text((String)("[Lv" + this.level(e) + "] "), (TextColor)NamedTextColor.GRAY) : Component.empty();
        Component named = showName ? Component.translatable(e.getType().translationKey()).color(NamedTextColor.RED).append(Component.space()) : Component.empty();
        d.text(prefix.append((Component)named).append((Component)Component.text((String)(hp + "/" + maxHp), (TextColor)NamedTextColor.GREEN)).append((Component)Component.text((String)"\u2764", (TextColor)NamedTextColor.RED)));
    }

    public void damageNumber(LivingEntity e, double damage, boolean critical) {
        Component text = critical ? this.criticalNumber(this.number(damage)) : Component.text((String)this.number(damage), (TextColor)NamedTextColor.RED);
        this.spawnDamageNumber(e, text);
    }

    /** Same floating number as {@link #damageNumber(LivingEntity, double, boolean)}, but in a fixed {@code color} instead of the melee red/rainbow-crit styling - see {@code ElementalDamageListener}. */
    public void damageNumber(LivingEntity e, double damage, TextColor color) {
        this.spawnDamageNumber(e, Component.text((String)this.number(damage), color));
    }

    private void spawnDamageNumber(LivingEntity e, Component text) {
        Location at = e.getLocation().add((this.random.nextDouble() - 0.5) * 0.8, e.getHeight() * 0.7 + 0.4, (this.random.nextDouble() - 0.5) * 0.8);
        TextDisplay d = (TextDisplay)e.getWorld().spawn(at, TextDisplay.class, x -> {
            x.text(text);
            x.setPersistent(false);
            x.setGravity(false);
            x.setInvulnerable(true);
            x.setBillboard(Display.Billboard.CENTER);
            x.setSeeThrough(true);
            x.setShadowed(true);
            x.setViewRange(this.damageRange);
        });
        Bukkit.getScheduler().runTaskLater(this.plugin, () -> ((TextDisplay)d).remove(), 24L);
    }

    private static final Particle.DustOptions FEROCITY_DUST = new Particle.DustOptions(Color.fromRGB(200, 0, 0), 1.1f);

    /**
     * A short line of red dust particles from the attacker to the target, one per
     * Ferocity extra hit — a busy fight has a lot going on (damage numbers, mob
     * flinch animation, other players' effects), so a number that pops up and fades
     * in under a second is easy to miss; a visible line makes "yes, that extra swing
     * really landed" unambiguous.
     */
    public void ferocityHit(Player attacker, LivingEntity target) {
        World world = target.getWorld();
        Location from = attacker.getEyeLocation();
        Location to = target.getLocation().add(0.0, target.getHeight() * 0.5, 0.0);
        Vector step = to.toVector().subtract(from.toVector());
        int points = 6;
        for (int i = 0; i <= points; i++) {
            double t = (double) i / points;
            Location at = from.clone().add(step.clone().multiply(t));
            world.spawnParticle(Particle.DUST, at, 1, 0.0, 0.0, 0.0, 0.0, FEROCITY_DUST);
        }
    }

    private Component criticalNumber(String value) {
        TextColor[] colors = new TextColor[]{NamedTextColor.RED, NamedTextColor.GOLD, NamedTextColor.YELLOW, NamedTextColor.GREEN, NamedTextColor.AQUA, NamedTextColor.LIGHT_PURPLE};
        Component out = Component.text((String)"\u2726 ", (TextColor)NamedTextColor.GOLD);
        for (int i = 0; i < value.length(); ++i) {
            out = out.append(Component.text((String)String.valueOf(value.charAt(i)), (TextColor)colors[i % colors.length]));
        }
        return out.append(Component.text((String)" \u2726", (TextColor)NamedTextColor.LIGHT_PURPLE));
    }

    private String number(double value) {
        return Long.toString(Math.round(Math.max(0.0, value)));
    }

    private String humanize(String key) {
        String v = key.substring(key.indexOf(58) + 1).replace('_', ' ');
        return Character.toUpperCase(v.charAt(0)) + v.substring(1);
    }

    private void clearLegacyName(LivingEntity e) {
        if (e instanceof Player || e.customName() == null) {
            return;
        }
        String plain = PlainTextComponentSerializer.plainText().serialize(e.customName());
        if (LEGACY.matcher(plain).matches()) {
            e.customName(null);
            e.setCustomNameVisible(false);
        }
    }

    private void removeOrphans() {
        for (World w : Bukkit.getWorlds()) {
            for (TextDisplay d : w.getEntitiesByClass(TextDisplay.class)) {
                if (!d.getScoreboardTags().contains(TAG)) continue;
                d.remove();
            }
        }
    }

    public void shutdown() {
        this.labels.values().forEach(Entity::remove);
        this.labels.clear();
        this.namePt.values().forEach(Entity::remove);
        this.namePt.clear();
        this.nameEn.values().forEach(Entity::remove);
        this.nameEn.clear();
        this.renderState.clear();
        this.shownTo.clear();
        this.removeOrphans();
    }
}

