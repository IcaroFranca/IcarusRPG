package dev.icaro.foodtooltips;

import dev.icaro.foodtooltips.bestiary.BestiaryListener;
import dev.icaro.foodtooltips.bestiary.BestiaryMenuService;
import dev.icaro.foodtooltips.bestiary.BestiaryProgressService;
import dev.icaro.foodtooltips.biome.BiomeWandListener;
import dev.icaro.foodtooltips.biome.BiomeWandService;
import dev.icaro.foodtooltips.builder.BuilderWandListener;
import dev.icaro.foodtooltips.builder.BuilderWandService;
import dev.icaro.foodtooltips.combat.CombatListener;
import dev.icaro.foodtooltips.combat.MobVisualService;
import dev.icaro.foodtooltips.destroyer.DestroyerHandListener;
import dev.icaro.foodtooltips.destroyer.DestroyerHandService;
import dev.icaro.foodtooltips.economy.EconomyService;
import dev.icaro.foodtooltips.food.FoodTooltipListener;
import dev.icaro.foodtooltips.food.FoodTooltipService;
import dev.icaro.foodtooltips.global.GlobalLevelCommand;
import dev.icaro.foodtooltips.global.GlobalLevelService;
import dev.icaro.foodtooltips.global.GlobalPlayerListener;
import dev.icaro.foodtooltips.global.GlobalPresentationService;
import dev.icaro.foodtooltips.global.LevelBadgeRenderer;
import dev.icaro.foodtooltips.global.LevelColorCommand;
import dev.icaro.foodtooltips.global.LevelColorMenuService;
import dev.icaro.foodtooltips.global.LevelColorService;
import dev.icaro.foodtooltips.i18n.Language;
import dev.icaro.foodtooltips.island.IslandMobListener;
import dev.icaro.foodtooltips.island.IslandMobService;
import dev.icaro.foodtooltips.item.DurabilityListener;
import dev.icaro.foodtooltips.item.DurabilityService;
import dev.icaro.foodtooltips.item.ItemTierListener;
import dev.icaro.foodtooltips.item.ItemTierService;
import dev.icaro.foodtooltips.item.SwordDamageListener;
import dev.icaro.foodtooltips.item.SwordDamageService;
import dev.icaro.foodtooltips.item.ToolDamageListener;
import dev.icaro.foodtooltips.item.ToolDamageService;
import dev.icaro.foodtooltips.item.legendary.DemonKingStormListener;
import dev.icaro.foodtooltips.item.legendary.LegendaryItemsListener;
import dev.icaro.foodtooltips.item.legendary.LegendaryItemsMenuService;
import dev.icaro.foodtooltips.item.legendary.LegendaryWeaponService;
import dev.icaro.foodtooltips.mining.GemService;
import dev.icaro.foodtooltips.mining.MiningMenuListener;
import dev.icaro.foodtooltips.mining.MiningMenuService;
import dev.icaro.foodtooltips.skills.ArmorDefenseListener;
import dev.icaro.foodtooltips.skills.ArmorDefenseService;
import dev.icaro.foodtooltips.skills.BedrockSwordThrowListener;
import dev.icaro.foodtooltips.skills.CombatAbilityService;
import dev.icaro.foodtooltips.skills.CombatSkillService;
import dev.icaro.foodtooltips.skills.CombatTreeListener;
import dev.icaro.foodtooltips.skills.CombatTreeMenuService;
import dev.icaro.foodtooltips.skills.CombatValorService;
import dev.icaro.foodtooltips.skills.GeneralSkillListener;
import dev.icaro.foodtooltips.skills.GeneralSkillService;
import dev.icaro.foodtooltips.skills.SetSkillLevelCommand;
import dev.icaro.foodtooltips.skills.SkillProgressBarService;
import dev.icaro.foodtooltips.skills.SkillsListener;
import dev.icaro.foodtooltips.skills.SkillsMenuService;
import dev.icaro.foodtooltips.skills.SkillsStarListener;
import dev.icaro.foodtooltips.skills.SkillsStarService;
import dev.icaro.foodtooltips.skills.SwordThrowListener;
import dev.icaro.foodtooltips.stats.PlayerStats;
import dev.icaro.foodtooltips.stats.PlayerStatsService;
import dev.icaro.foodtooltips.stats.ResetStatsCommand;
import dev.icaro.foodtooltips.stats.StatsHudService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class FoodTooltipsPlugin
extends JavaPlugin {
    private MobVisualService visuals;
    private SkillProgressBarService progressBar;

    public void onEnable() {
        this.saveDefaultConfig();
        this.mergeConfigDefaults();
        this.cleanupLegacyCitizensNpcs();
        if (this.getConfig().getInt("combat.max-level", 50) < 200) {
            this.getConfig().set("combat.max-level", 200);
            this.saveConfig();
        }
        PlayerStatsService stats = new PlayerStatsService((Plugin)this);
        CombatSkillService combat = new CombatSkillService((Plugin)this);
        GeneralSkillService general = new GeneralSkillService();
        stats.general(general);
        CombatValorService valor = new CombatValorService((Plugin)this);
        ArmorDefenseService armor = new ArmorDefenseService();
        armor.general(general);
        ItemTierService tiers = new ItemTierService((Plugin)this);
        DurabilityService durability = new DurabilityService((Plugin)this);
        SwordDamageService swordDamage = new SwordDamageService((Plugin)this, combat);
        ToolDamageService toolDamage = new ToolDamageService((Plugin)this, combat);
        BuilderWandService builderWand = new BuilderWandService((Plugin)this, tiers);
        DestroyerHandService destroyerHand = new DestroyerHandService((Plugin)this, tiers);
        BiomeWandService biomeWand = new BiomeWandService((Plugin)this, tiers);
        CombatAbilityService abilities = new CombatAbilityService((Plugin)this, combat, stats, valor);
        stats.abilities(abilities);
        EconomyService economy = new EconomyService((Plugin)this, abilities);
        BestiaryProgressService bestiaryProgress = new BestiaryProgressService((Plugin)this);
        GemService gems = new GemService((Plugin)this);
        MiningMenuService mining = new MiningMenuService(gems);
        GlobalLevelService global = new GlobalLevelService((Plugin)this, combat, general, bestiaryProgress);
        stats.global(global);
        SkillsMenuService menus = new SkillsMenuService(combat, general, stats, abilities, mining, global, armor, bestiaryProgress);
        SkillsStarService skillsStar = new SkillsStarService((Plugin)this);
        LegendaryWeaponService legendary = new LegendaryWeaponService((Plugin)this, stats, tiers);
        stats.legendary(legendary);
        LegendaryItemsMenuService legendaryItemsMenu = new LegendaryItemsMenuService(legendary);
        IslandMobService islandMobs = new IslandMobService((Plugin)this, legendary);
        BestiaryMenuService bestiary = new BestiaryMenuService((Plugin)this, bestiaryProgress, economy, valor);
        this.progressBar = new SkillProgressBarService((Plugin)this);
        this.visuals = new MobVisualService((Plugin)this);
        LevelColorService levelColors = new LevelColorService((Plugin)this, global);
        LevelBadgeRenderer badgeRenderer = new LevelBadgeRenderer(this.getConfig().getInt("global-level.badge-animation-smoothness", 4));
        GlobalPresentationService presentation = new GlobalPresentationService((Plugin)this, global, levelColors, badgeRenderer);
        LevelColorMenuService levelColorMenu = new LevelColorMenuService((Plugin)this, global, levelColors, presentation, menus::openMain);
        menus.levelColors(levelColorMenu);
        CombatTreeMenuService treeMenu = new CombatTreeMenuService(combat, abilities, valor, menus::openMain);
        menus.tree(treeMenu);
        global.onChange(p -> {
            presentation.refresh((Player)p);
            presentation.refreshAll();
        });
        levelColors.onChange(p -> presentation.refreshAll());
        PluginManager pm = this.getServer().getPluginManager();
        pm.registerEvents((Listener)new GlobalPlayerListener(global), (Plugin)this);
        pm.registerEvents((Listener)presentation, (Plugin)this);
        pm.registerEvents((Listener)new SkillsListener(menus), (Plugin)this);
        pm.registerEvents((Listener)new SkillsStarListener((Plugin)this, skillsStar, menus), (Plugin)this);
        pm.registerEvents((Listener)new CombatTreeListener(treeMenu), (Plugin)this);
        pm.registerEvents((Listener)new GeneralSkillListener((Plugin)this, general, this.progressBar, global), (Plugin)this);
        pm.registerEvents((Listener)gems, (Plugin)this);
        pm.registerEvents((Listener)new MiningMenuListener(mining, menus, gems), (Plugin)this);
        pm.registerEvents((Listener)new BestiaryListener(bestiary), (Plugin)this);
        CombatListener combatListener = new CombatListener((Plugin)this, combat, this.visuals, bestiaryProgress, this.progressBar, abilities, economy, global, stats, valor, armor, general, legendary);
        pm.registerEvents((Listener)combatListener, (Plugin)this);
        pm.registerEvents((Listener)new LegendaryItemsListener(legendaryItemsMenu), (Plugin)this);
        pm.registerEvents((Listener)new DemonKingStormListener((Plugin)this, stats, abilities), (Plugin)this);
        pm.registerEvents((Listener)new ArmorDefenseListener(armor), (Plugin)this);
        pm.registerEvents((Listener)new ItemTierListener(tiers), (Plugin)this);
        pm.registerEvents((Listener)new DurabilityListener(durability), (Plugin)this);
        pm.registerEvents((Listener)new SwordDamageListener(swordDamage), (Plugin)this);
        pm.registerEvents((Listener)new ToolDamageListener(toolDamage), (Plugin)this);
        pm.registerEvents((Listener)new BuilderWandListener(builderWand), (Plugin)this);
        pm.registerEvents((Listener)new DestroyerHandListener(destroyerHand), (Plugin)this);
        pm.registerEvents((Listener)new BiomeWandListener(biomeWand), (Plugin)this);
        pm.registerEvents((Listener)new IslandMobListener(islandMobs), (Plugin)this);
        // Delayed so world-management plugins (e.g. Multiverse) have a chance to finish
        // loading the island's world first if it isn't loaded at server-start time yet.
        Bukkit.getScheduler().runTaskLater((Plugin)this, () -> islandMobs.spawnPopulation(), 40L);
        SwordThrowListener swordThrow = new SwordThrowListener((Plugin)this, abilities);
        pm.registerEvents((Listener)swordThrow, (Plugin)this);
        pm.registerEvents((Listener)new BedrockSwordThrowListener(swordThrow), (Plugin)this);
        FoodTooltipListener foodListener = new FoodTooltipListener((Plugin)this, new FoodTooltipService());
        pm.registerEvents((Listener)foodListener, (Plugin)this);
        SetSkillLevelCommand setSkill = new SetSkillLevelCommand(combat, general, global);
        this.getCommand("setskilllevel").setExecutor((CommandExecutor)setSkill);
        this.getCommand("setskilllevel").setTabCompleter((TabCompleter)setSkill);
        ResetStatsCommand resetStats = new ResetStatsCommand(stats, combat, armor, global, bestiaryProgress, economy, general);
        this.getCommand("resetstats").setExecutor((CommandExecutor)resetStats);
        this.getCommand("resetstats").setTabCompleter((TabCompleter)resetStats);
        GlobalLevelCommand globalCommand = new GlobalLevelCommand(global);
        this.getCommand("nivelglobal").setExecutor((CommandExecutor)globalCommand);
        this.getCommand("nivelglobal").setTabCompleter((TabCompleter)globalCommand);
        this.getCommand("globalxp").setExecutor((CommandExecutor)globalCommand);
        this.getCommand("globalxp").setTabCompleter((TabCompleter)globalCommand);
        LevelColorCommand levelColorCommand = new LevelColorCommand(levelColors, levelColorMenu);
        this.getCommand("levelcolor").setExecutor((CommandExecutor)levelColorCommand);
        this.getCommand("levelcolor").setTabCompleter((TabCompleter)levelColorCommand);
        this.getCommand("coins").setExecutor((s, c, l, a) -> {
            if (a.length == 0 && s instanceof Player) {
                Player p = (Player)s;
                s.sendMessage((Component)Component.text((String)(this.text(s, "Saldo: ", "Balance: ") + economy.format(economy.balance(p)) + " \u26c3"), (TextColor)NamedTextColor.GOLD));
                return true;
            }
            if (a.length != 3 || !s.hasPermission("foodtooltips.admin")) {
                s.sendMessage((Component)Component.text((String)(this.text(s, "Uso: ", "Usage: ") + "/coins <player> <set|give> <amount>"), (TextColor)NamedTextColor.RED));
                return true;
            }
            Player target = Bukkit.getPlayerExact((String)a[0]);
            if (target == null) {
                s.sendMessage((Component)Component.text((String)this.text(s, "Jogador n\u00e3o encontrado.", "Player not found."), (TextColor)NamedTextColor.RED));
                return true;
            }
            try {
                long amount = Long.parseLong(a[2]);
                if (a[1].equalsIgnoreCase("set")) {
                    economy.setBalance(target, amount);
                } else if (a[1].equalsIgnoreCase("give")) {
                    economy.deposit(target, amount);
                } else {
                    throw new IllegalArgumentException();
                }
                s.sendMessage((Component)Component.text((String)(this.text(s, "Saldo de ", "Balance of ") + target.getName() + ": " + economy.format(economy.balance(target)) + " \u26c3"), (TextColor)NamedTextColor.GREEN));
            }
            catch (IllegalArgumentException ex) {
                s.sendMessage((Component)Component.text((String)(this.text(s, "Uso: ", "Usage: ") + "/coins <player> <set|give> <amount>"), (TextColor)NamedTextColor.RED));
            }
            return true;
        });
        this.getCommand("builderwand").setExecutor((s, c, l, a) -> {
            Player target;
            if (a.length >= 1) {
                target = Bukkit.getPlayerExact((String)a[0]);
                if (target == null) {
                    s.sendMessage((Component)Component.text((String)this.text(s, "Jogador não encontrado ou offline.", "Player not found or offline."), (TextColor)NamedTextColor.RED));
                    return true;
                }
            } else if (s instanceof Player) {
                target = (Player)s;
            } else {
                s.sendMessage((Component)Component.text((String)"Usage: /builderwand [player]"));
                return true;
            }
            target.getInventory().addItem(builderWand.create(Language.of(target)));
            s.sendMessage((Component)Component.text((String)(this.text(s, "Varinha do Construtor entregue a ", "Builder's Wand given to ") + target.getName() + "."), (TextColor)NamedTextColor.GREEN));
            return true;
        });
        this.getCommand("destroyerhand").setExecutor((s, c, l, a) -> {
            Player target;
            if (a.length >= 1) {
                target = Bukkit.getPlayerExact((String)a[0]);
                if (target == null) {
                    s.sendMessage((Component)Component.text((String)this.text(s, "Jogador não encontrado ou offline.", "Player not found or offline."), (TextColor)NamedTextColor.RED));
                    return true;
                }
            } else if (s instanceof Player) {
                target = (Player)s;
            } else {
                s.sendMessage((Component)Component.text((String)"Usage: /destroyerhand [player]"));
                return true;
            }
            target.getInventory().addItem(destroyerHand.create(Language.of(target)));
            s.sendMessage((Component)Component.text((String)(this.text(s, "Mão do Destruidor entregue a ", "Destroyer's Hand given to ") + target.getName() + "."), (TextColor)NamedTextColor.GREEN));
            return true;
        });
        this.getCommand("biomewand").setExecutor((s, c, l, a) -> {
            Player target;
            if (a.length >= 1) {
                target = Bukkit.getPlayerExact((String)a[0]);
                if (target == null) {
                    s.sendMessage((Component)Component.text((String)this.text(s, "Jogador não encontrado ou offline.", "Player not found or offline."), (TextColor)NamedTextColor.RED));
                    return true;
                }
            } else if (s instanceof Player) {
                target = (Player)s;
            } else {
                s.sendMessage((Component)Component.text((String)"Usage: /biomewand [player]"));
                return true;
            }
            target.getInventory().addItem(biomeWand.create(Language.of(target)));
            s.sendMessage((Component)Component.text((String)(this.text(s, "Varinha de Biomas entregue a ", "Biome's Wand given to ") + target.getName() + "."), (TextColor)NamedTextColor.GREEN));
            return true;
        });
        this.getCommand("islandmobs").setExecutor((s, c, l, a) -> {
            int spawned = islandMobs.spawnPopulation();
            s.sendMessage((Component)Component.text((String)(this.text(s, "Mobs da ilha reiniciados: ", "Island mobs respawned: ") + spawned), (TextColor)NamedTextColor.GREEN));
            return true;
        });
        this.getCommand("skills").setExecutor((s, c, l, a) -> {
            if (s instanceof Player) {
                Player p = (Player)s;
                menus.openMain(p);
            } else {
                s.sendMessage((Component)Component.text((String)"Only players."));
            }
            return true;
        });
        this.getCommand("rpgitems").setExecutor((s, c, l, a) -> {
            if (s instanceof Player) {
                Player p = (Player)s;
                legendaryItemsMenu.open(p);
            } else {
                s.sendMessage((Component)Component.text((String)"Only players."));
            }
            return true;
        });
        this.getCommand("bestiary").setExecutor((s, c, l, a) -> {
            if (s instanceof Player) {
                Player p = (Player)s;
                bestiary.openCategories(p);
            } else {
                s.sendMessage((Component)Component.text((String)"Only players."));
            }
            return true;
        });
        this.getCommand("rpgstats").setExecutor((s, c, l, a) -> {
            if (s instanceof Player) {
                Player p = (Player)s;
                PlayerStats x = stats.stats(p);
                s.sendMessage((Component)Component.text((String)(this.text(s, "Vida ", "Health ") + Math.round(x.health()) + "/" + Math.round(x.maxHealth()) + ", Mana " + Math.round(x.mana()) + "/" + Math.round(x.maxMana()) + ", Strength " + x.strength() + ", " + this.text(s, "milestones do besti\u00e1rio ", "bestiary milestones ") + bestiaryProgress.totalMilestones(p))));
            }
            return true;
        });
        StatsHudService hud = new StatsHudService(this.getConfig().getString("hud.spacing", "     "));
        long ticks = Math.max(1L, this.getConfig().getLong("hud.update-ticks", 5L));
        double manaRegen = this.getConfig().getDouble("stats.mana-regeneration-per-second", 2.0) * (double)ticks / 20.0;
        double vitalityRegen = this.getConfig().getDouble("stats.vitality-regeneration-per-second", 4.0) * (double)ticks / 20.0;
        double naturalHealthRegenPerSecond = this.getConfig().getDouble("stats.natural-health-regen-per-second", 0.5);
        this.getServer().getScheduler().runTaskTimer((Plugin)this, () -> this.getServer().getOnlinePlayers().forEach(p -> {
            stats.regen((Player)p, manaRegen);
            stats.regenVitality((Player)p, vitalityRegen);
            double healthRegenMultiplier = stats.stats((Player)p).healthRegen() / 100.0;
            stats.regenHealth((Player)p, naturalHealthRegenPerSecond * healthRegenMultiplier * (double)ticks / 20.0);
            armor.neutralizeVanillaArmor((Player)p);
            armor.applyDefenseTooltip((Player)p);
            tiers.applyItemTiers((Player)p);
            durability.applyDurability((Player)p);
            swordDamage.neutralizeBaseAttackDamage((Player)p);
            swordDamage.applySwordDamage((Player)p);
            toolDamage.applyToolDamage((Player)p);
            legendary.refreshStrengthLore((Player)p);
            hud.show((Player)p, stats.stats((Player)p), armor.defense((Player)p));
        }), 1L, ticks);
        this.getServer().getScheduler().runTaskTimer((Plugin)this, this.visuals::tick, 1L, Math.max(1L, this.getConfig().getLong("mob-visuals.update-ticks", 3L)));
        for (World w : this.getServer().getWorlds()) {
            for (LivingEntity e : w.getLivingEntities()) {
                combatListener.scaleMobHealth(e);
                armor.neutralizeVanillaArmor(e);
                this.visuals.track(e);
            }
        }
        this.getServer().getOnlinePlayers().forEach(p -> {
            // Captured before applyBaseHealth/applyBonusHealth/migrate touch Max Health -
            // each can momentarily drop it below the player's real current Health before
            // the next call's bonus reattaches, and vanilla auto-clamps Health down the
            // instant that happens (irreversible once the bonuses return). Restoring from
            // this snapshot afterwards is what actually preserves it across a /reload with
            // players already online - same fix as CombatListener#reapplyHealthStack.
            double healthBefore = ((Player)p).getHealth();
            stats.applyBaseHealth((Player)p);
            combat.applyAttackSpeed((Player)p);
            stats.applySwingRange((Player)p);
            armor.neutralizeVanillaArmor((Player)p);
            armor.applyDefenseTooltip((Player)p);
            tiers.applyItemTiers((Player)p);
            durability.applyDurability((Player)p);
            swordDamage.neutralizeBaseAttackDamage((Player)p);
            swordDamage.applySwordDamage((Player)p);
            toolDamage.applyToolDamage((Player)p);
            legendary.refreshStrengthLore((Player)p);
            bestiaryProgress.applyBonusHealth((Player)p);
            general.applyBonusHealth((Player)p);
            skillsStar.ensure((Player)p);
            global.migrate((Player)p);
            if (!((Player)p).isDead()) {
                ((Player)p).setHealth(Math.min(healthBefore, stats.stats((Player)p).maxHealth()));
            }
            foodListener.refresh((Player)p);
            this.visuals.track((LivingEntity)p);
            economy.updateBoard((Player)p);
        });
        Bukkit.getScheduler().runTask((Plugin)this, presentation::refreshAll);
    }

    public void onDisable() {
        if (this.visuals != null) {
            this.visuals.shutdown();
        }
        if (this.progressBar != null) {
            this.progressBar.shutdown();
        }
    }

    /**
     * saveDefaultConfig() only ever writes config.yml if it doesn't exist yet on disk -
     * a server upgrading from an older jar keeps its existing file untouched, so any
     * section a newer version adds (like island-mobs) is simply missing there and every
     * getInt/getString call on it silently falls back to 0/empty instead of the real
     * bundled default. Layering the jar's own config.yml in as Bukkit "defaults" and
     * copying only the keys genuinely absent from the live file (never touching ones
     * already set) fixes that for this and every future config addition, not just this one.
     */
    private void mergeConfigDefaults() {
        try (java.io.InputStream in = this.getResource("config.yml")) {
            if (in == null) {
                return;
            }
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
            this.getConfig().addDefaults(defaults);
            this.getConfig().options().copyDefaults(true);
            this.saveConfig();
        } catch (java.io.IOException ex) {
            this.getLogger().warning("Could not merge new config.yml defaults: " + ex.getMessage());
        }
    }

    /**
     * One-time cleanup for the very first Sentinela da Ilha implementation, which spawned
     * Citizens NPCs into Citizens' own default (persistent) registry - Citizens saves those
     * to its own data files and keeps re-spawning them on every server start forever,
     * completely independent of this plugin, even after the feature moved to plain vanilla
     * Zombies. Safe to leave in permanently: it only ever matches that literal legacy name,
     * and does nothing at all once no such NPC is left (or Citizens isn't installed).
     */
    private void cleanupLegacyCitizensNpcs() {
        if (this.getServer().getPluginManager().getPlugin("Citizens") == null) {
            return;
        }
        try {
            java.util.List<net.citizensnpcs.api.npc.NPC> toRemove = new java.util.ArrayList<>();
            for (net.citizensnpcs.api.npc.NPC npc : net.citizensnpcs.api.CitizensAPI.getNPCRegistry()) {
                if ("Sentinela da Ilha".equals(npc.getName())) {
                    toRemove.add(npc);
                }
            }
            for (net.citizensnpcs.api.npc.NPC npc : toRemove) {
                npc.destroy();
            }
            int removed = toRemove.size();
            if (removed > 0) {
                this.getLogger().info("Removed " + removed + " leftover Citizens NPC(s) from the old Sentinela da Ilha implementation.");
            }
        } catch (Throwable t) {
            this.getLogger().warning("Could not clean up legacy Citizens NPCs: " + t.getMessage());
        }
    }

    private String text(CommandSender sender, String pt, String en) {
        String string;
        if (sender instanceof Player) {
            Player p = (Player)sender;
            string = Language.of(p).choose(pt, en);
        } else {
            string = en;
        }
        return string;
    }
}

