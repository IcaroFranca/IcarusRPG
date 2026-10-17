package dev.icaro.foodtooltips.mob;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.icaro.foodtooltips.combat.MobDifficultyService;
import dev.icaro.foodtooltips.combat.MobVisualService;
import io.papermc.paper.datacomponent.DataComponentTypes;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.joml.Matrix4f;

/**
 * Custom mobs with a real 3D model, no client mod needed. Each mob is a vanilla base mob that does
 * the moving, fighting and taking hits (invisible, scaled to the model's hitbox), carrying one
 * {@link ItemDisplay} per model part as passengers. A part's item model comes from IcarusTexture
 * ({@code icarus:mob/<id>/<part>}); its transformation for every tick of every animation is
 * pre-baked by IcarusTexture's tools/convert_mob.py into {@code mobs/<id>.json} (this jar), so
 * here it's only "pick the animation, pick the tick, turn it to face where the mob faces".
 *
 * <p>Animations: {@code attack} plays once each time the mob hits something, {@code walk} while
 * it's moving, {@code idle} otherwise. The displays are never saved with the world - they're
 * rebuilt whenever a tagged mob is loaded ({@link #loaded}) and removed with it.
 *
 * <p>With BetterModel on the server the same mob is played by it instead, from
 * {@code mobs/<id>.bbmodel} (IcarusTexture's tools/gltf_to_bbmodel.py) - see
 * {@link BetterModelBackend}. Its resource pack part ships inside IcarusTexture, which is why
 * BetterModel's name obfuscation is turned off: the names it generates have to match the pack.
 */
public final class CustomMobService implements Listener {
    public static final NamespacedKey MOB_KEY = new NamespacedKey("foodtooltips", "custom_mob");
    private static final NamespacedKey EGG_KEY = new NamespacedKey("foodtooltips", "custom_mob_egg");
    private static final NamespacedKey PART_KEY = new NamespacedKey("foodtooltips", "custom_mob_part");

    /** One kind of custom mob: its rig, base mob and stats. */
    public enum Type {
        REDSTONE_MONSTROSITY("redstone_monstrosity", "Redstone Monstrosity", org.bukkit.entity.Ravager.class, 2.2, 5000, 250);

        public final String id;
        public final String label;
        final Class<? extends Mob> base;
        /** The base mob's own vanilla height - its scale is the model's height / this. */
        final double baseHeight;
        final double health;
        final double damage;

        Type(String id, String label, Class<? extends Mob> base, double baseHeight, double health, double damage) {
            this.id = id;
            this.label = label;
            this.base = base;
            this.baseHeight = baseHeight;
            this.health = health;
            this.damage = damage;
        }

        public static Type of(String id) {
            for (Type t : values()) {
                if (t.id.equalsIgnoreCase(id)) {
                    return t;
                }
            }
            return null;
        }
    }

    record Animation(boolean loop, float[][][] frames) {
    }

    record Rig(List<Key> parts, Map<String, Animation> animations, double height) {
    }

    /** A live mob and its part displays. */
    private static final class Live {
        final Type type;
        final Mob mob;
        final List<ItemDisplay> parts = new ArrayList<>();
        int tick;
        int attackTick = -1;
        Location last;
        /** Played by BetterModel - no displays of our own. */
        boolean modeled;

        Live(Type type, Mob mob) {
            this.type = type;
            this.mob = mob;
        }
    }

    private final Plugin plugin;
    private final MobDifficultyService difficulty;
    private final MobVisualService visuals;
    private final Map<Type, Rig> rigs = new HashMap<>();
    private final Map<UUID, Live> live = new HashMap<>();

    public CustomMobService(Plugin plugin, MobDifficultyService difficulty, MobVisualService visuals) {
        this.plugin = plugin;
        this.difficulty = difficulty;
        this.visuals = visuals;
        for (Type t : Type.values()) {
            this.rigs.put(t, loadRig(plugin, t.id));
        }
    }

    /**
     * Copies every mob's .bbmodel into BetterModel's models folder (when it differs) and turns
     * off its name obfuscation, so the pack it generates matches the one IcarusTexture ships.
     * Called from onLoad - before BetterModel enables and reads either.
     */
    public static void installBetterModelFiles(Plugin plugin) {
        if (Bukkit.getPluginManager().getPlugin("BetterModel") == null) {
            return;
        }
        java.io.File dir = new java.io.File(plugin.getDataFolder().getParentFile(), "BetterModel");
        java.io.File models = new java.io.File(dir, "models");
        try {
            java.nio.file.Files.createDirectories(models.toPath());
            for (Type t : Type.values()) {
                try (InputStream in = plugin.getResource("mobs/" + t.id + ".bbmodel")) {
                    if (in == null) {
                        continue;
                    }
                    byte[] data = in.readAllBytes();
                    java.nio.file.Path target = new java.io.File(models, t.id + ".bbmodel").toPath();
                    if (!java.nio.file.Files.exists(target) || !java.util.Arrays.equals(java.nio.file.Files.readAllBytes(target), data)) {
                        java.nio.file.Files.write(target, data);
                        plugin.getLogger().info("Installed BetterModel model " + t.id + ".bbmodel");
                    }
                }
            }
            java.io.File config = new java.io.File(dir, "config.yml");
            if (config.exists()) {
                org.bukkit.configuration.file.YamlConfiguration yaml = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(config);
                if (yaml.getBoolean("pack.use-obfuscation", true)) {
                    yaml.set("pack.use-obfuscation", false);
                    yaml.save(config);
                    plugin.getLogger().info("Turned off BetterModel's pack.use-obfuscation - IcarusTexture ships the custom mob models under their real names.");
                }
            } else {
                plugin.getLogger().warning("BetterModel has no config.yml yet - restart once after its first start so IcarusRPG can turn off pack.use-obfuscation.");
            }
        } catch (java.io.IOException e) {
            plugin.getLogger().warning("Couldn't install the BetterModel models: " + e);
        }
    }

    private static boolean betterModel() {
        return Bukkit.getPluginManager().isPluginEnabled("BetterModel");
    }

    static Rig loadRig(Plugin plugin, String id) {
        try (InputStream in = plugin.getResource("mobs/" + id + ".json")) {
            if (in == null) {
                throw new IllegalStateException("missing mobs/" + id + ".json");
            }
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            return parseRig(root);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    static Rig parseRig(JsonObject root) {
        List<Key> parts = new ArrayList<>();
        for (JsonElement p : root.getAsJsonArray("parts")) {
            parts.add(Key.key(p.getAsJsonObject().get("model").getAsString()));
        }
        Map<String, Animation> animations = new HashMap<>();
        for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("animations").entrySet()) {
            JsonObject a = e.getValue().getAsJsonObject();
            JsonArray frames = a.getAsJsonArray("frames");
            float[][][] out = new float[frames.size()][][];
            for (int f = 0; f < frames.size(); f++) {
                JsonArray perPart = frames.get(f).getAsJsonArray();
                out[f] = new float[perPart.size()][16];
                for (int p = 0; p < perPart.size(); p++) {
                    JsonArray m = perPart.get(p).getAsJsonArray();
                    for (int k = 0; k < 16; k++) {
                        out[f][p][k] = m.get(k).getAsFloat();
                    }
                }
            }
            animations.put(e.getKey(), new Animation(a.get("loop").getAsBoolean(), out));
        }
        return new Rig(parts, animations, root.getAsJsonObject("hitbox").get("height").getAsDouble());
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(this.plugin, this::tick, 1L, 1L);
        for (World w : Bukkit.getWorlds()) {
            for (Entity e : w.getEntities()) {
                this.attachIfCustom(e);
            }
        }
    }

    // ---- Spawning ----

    public Mob spawn(Type type, Location at) {
        Rig rig = this.rigs.get(type);
        Mob mob = at.getWorld().spawn(at, type.base, CreatureSpawnEvent.SpawnReason.CUSTOM, m -> {
            m.getPersistentDataContainer().set(MOB_KEY, PersistentDataType.STRING, type.id);
            m.setPersistent(true);
            m.setRemoveWhenFarAway(false);
            m.setInvisible(true);
            AttributeInstance scale = m.getAttribute(Attribute.SCALE);
            if (scale != null) {
                scale.setBaseValue(rig.height() / type.baseHeight);
            }
            // Before the difficulty scaling that the spawn event runs right after this.
            this.difficulty.raiseFloor(m, type.health, type.damage);
        });
        Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
            if (mob.isValid()) {
                this.visuals.setLocalizedName(mob, type.label, type.label);
            }
        }, 2L);
        this.attach(type, mob);
        return mob;
    }

    private void attachIfCustom(Entity e) {
        if (e instanceof ItemDisplay d && d.getPersistentDataContainer().has(PART_KEY)) {
            d.remove(); // a stray part from before a crash - its mob rebuilds its own
            return;
        }
        if (!(e instanceof Mob mob) || this.live.containsKey(mob.getUniqueId())) {
            return;
        }
        String id = mob.getPersistentDataContainer().get(MOB_KEY, PersistentDataType.STRING);
        Type type = id == null ? null : Type.of(id);
        if (type != null && !mob.isDead()) {
            this.attach(type, mob);
        }
    }

    private void attach(Type type, Mob mob) {
        Rig rig = this.rigs.get(type);
        Live l = new Live(type, mob);
        if (betterModel() && BetterModelBackend.attach(mob, type.id)) {
            mob.setInvisible(false); // BetterModel hides the base itself - and hides the model too on an invisible mob
            l.modeled = true;
            l.last = mob.getLocation();
            this.live.put(mob.getUniqueId(), l);
            return;
        }
        mob.setInvisible(true);
        for (Key model : rig.parts()) {
            ItemDisplay d = mob.getWorld().spawn(mob.getLocation(), ItemDisplay.class, disp -> {
                ItemStack item = new ItemStack(Material.PAPER);
                item.setData(DataComponentTypes.ITEM_MODEL, model);
                disp.setItemStack(item);
                disp.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                disp.setPersistent(false);
                disp.setInterpolationDuration(2);
                disp.setTeleportDuration(1);
                disp.setViewRange(2.0f);
                disp.getPersistentDataContainer().set(PART_KEY, PersistentDataType.STRING, type.id);
            });
            mob.addPassenger(d);
            l.parts.add(d);
        }
        l.last = mob.getLocation();
        this.live.put(mob.getUniqueId(), l);
        this.pose(l, rig.animations().get("idle"), 0);
    }

    // ---- Animating ----

    private void tick() {
        for (Iterator<Live> it = this.live.values().iterator(); it.hasNext(); ) {
            Live l = it.next();
            if (!l.mob.isValid() || l.mob.isDead()) {
                l.parts.forEach(Entity::remove);
                it.remove();
                continue;
            }
            if (l.modeled) {
                continue;
            }
            Rig rig = this.rigs.get(l.type);
            Location now = l.mob.getLocation();
            double moved = Math.hypot(now.getX() - l.last.getX(), now.getZ() - l.last.getZ());
            l.last = now;
            l.tick++;
            Animation attack = rig.animations().get("attack");
            if (l.attackTick >= 0 && attack != null) {
                this.pose(l, attack, l.attackTick);
                if (++l.attackTick >= attack.frames().length) {
                    l.attackTick = -1;
                }
                continue;
            }
            Animation a = moved > 0.02 ? rig.animations().get("walk") : rig.animations().get("idle");
            this.pose(l, a, l.tick);
        }
    }

    /** Puts every part on frame {@code tick} of {@code a}, turned to the mob's body yaw and moved from the passenger seat down to the mob's feet. */
    private void pose(Live l, Animation a, int tick) {
        if (a == null) {
            return;
        }
        float[][] frame = a.frames()[a.loop() ? Math.floorMod(tick, a.frames().length) : Math.min(tick, a.frames().length - 1)];
        float yaw = (float) Math.toRadians(-l.mob.getBodyYaw());
        for (int i = 0; i < l.parts.size() && i < frame.length; i++) {
            ItemDisplay d = l.parts.get(i);
            float seat = (float) (d.getLocation().getY() - l.mob.getLocation().getY());
            Matrix4f m = new Matrix4f().translation(0, -seat, 0).rotateY(yaw).mul(new Matrix4f().set(frame[i]));
            d.setRotation(0, 0);
            d.setInterpolationDelay(0);
            d.setTransformationMatrix(m);
        }
    }

    /** The mob landed a hit: play its attack once from the start. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void hit(EntityDamageByEntityEvent e) {
        Live l = this.live.get(e.getDamager().getUniqueId());
        if (l != null && l.modeled) {
            BetterModelBackend.attack(l.mob, l.type.id);
        } else if (l != null) {
            l.attackTick = 0;
        }
    }

    @EventHandler
    public void death(EntityDeathEvent e) {
        Live l = this.live.remove(e.getEntity().getUniqueId());
        if (l != null) {
            l.parts.forEach(Entity::remove);
            e.getDrops().clear(); // no loot defined yet - and never the base mob's own (a Ravager's saddle)
        }
    }

    @EventHandler
    public void loaded(EntitiesLoadEvent e) {
        for (Entity en : e.getEntities()) {
            this.attachIfCustom(en);
        }
    }

    @EventHandler
    public void unloaded(EntitiesUnloadEvent e) {
        for (Entity en : e.getEntities()) {
            Live l = this.live.remove(en.getUniqueId());
            if (l != null) {
                l.parts.forEach(Entity::remove);
            }
        }
    }

    // ---- Spawn egg ----

    public ItemStack egg(Type type) {
        ItemStack item = new ItemStack(Material.RAVAGER_SPAWN_EGG);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(type.label + " Spawn Egg", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("Right click a block to summon it.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Health " + (int) type.health + " - Damage " + (int) type.damage, NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)));
        meta.getPersistentDataContainer().set(EGG_KEY, PersistentDataType.STRING, type.id);
        item.setItemMeta(meta);
        return item;
    }

    public List<ItemStack> eggs() {
        List<ItemStack> out = new ArrayList<>();
        for (Type t : Type.values()) {
            out.add(this.egg(t));
        }
        return out;
    }

    private static Type eggType(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        String id = item.getItemMeta().getPersistentDataContainer().get(EGG_KEY, PersistentDataType.STRING);
        return id == null ? null : Type.of(id);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void useEgg(PlayerInteractEvent e) {
        Type type = eggType(e.getItem());
        if (type == null) {
            return;
        }
        e.setCancelled(true); // never vanilla's own Ravager, nor a spawner swap
        Block block = e.getClickedBlock();
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || block == null || e.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Location at = block.getRelative(e.getBlockFace()).getLocation().add(0.5, 0, 0.5);
        at.setYaw(e.getPlayer().getLocation().getYaw() + 180);
        this.spawn(type, at);
        Player p = e.getPlayer();
        if (p.getGameMode() != GameMode.CREATIVE) {
            e.getItem().setAmount(e.getItem().getAmount() - 1);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void dispense(BlockDispenseEvent e) {
        if (eggType(e.getItem()) != null) {
            e.setCancelled(true);
        }
    }

    // ---- Command ----

    /** {@code /rpgmob <id>}: summons it where the sender looks (or stands). */
    public boolean command(org.bukkit.command.CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Only players can summon custom mobs.");
            return true;
        }
        Type type = args.length > 0 ? Type.of(args[0]) : null;
        if (type == null) {
            StringBuilder ids = new StringBuilder();
            for (Type t : Type.values()) {
                ids.append(ids.length() > 0 ? ", " : "").append(t.id);
            }
            p.sendMessage(Component.text("Usage: /rpgmob <" + ids + ">", NamedTextColor.RED));
            return true;
        }
        Block target = p.getTargetBlockExact(30);
        Location at = target != null ? target.getLocation().add(0.5, 1, 0.5) : p.getLocation();
        at.setYaw(p.getLocation().getYaw() + 180);
        this.spawn(type, at);
        p.sendMessage(Component.text("Summoned " + type.label + ".", NamedTextColor.GREEN));
        return true;
    }

    public List<String> complete(String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (Type t : Type.values()) {
                if (t.id.startsWith(args[0].toLowerCase())) {
                    out.add(t.id);
                }
            }
        }
        return out;
    }

    /** Whether {@code e} is one of this service's mobs - for systems that want to treat them apart. */
    public static boolean isCustomMob(LivingEntity e) {
        return e.getPersistentDataContainer().has(MOB_KEY, PersistentDataType.STRING);
    }
}
