package dev.icaro.foodtooltips;

import dev.icaro.foodtooltips.bestiary.BestiaryListener;
import dev.icaro.foodtooltips.bestiary.BestiaryMenuService;
import dev.icaro.foodtooltips.bestiary.BestiaryProgressService;
import dev.icaro.foodtooltips.collections.CollectionsItemsMenuListener;
import dev.icaro.foodtooltips.collections.CollectionsItemsMenuService;
import dev.icaro.foodtooltips.collections.CollectionsListener;
import dev.icaro.foodtooltips.collections.CollectionsMenuService;
import dev.icaro.foodtooltips.collections.CollectionsProgressService;
import dev.icaro.foodtooltips.collections.CollectionsRecipeGateListener;
import dev.icaro.foodtooltips.collections.CollectionsService;
import dev.icaro.foodtooltips.biome.BiomeWandListener;
import dev.icaro.foodtooltips.biome.BiomeWandService;
import dev.icaro.foodtooltips.builder.BuilderWandListener;
import dev.icaro.foodtooltips.builder.BuilderWandService;
import dev.icaro.foodtooltips.combat.AnimalSeparationService;
import dev.icaro.foodtooltips.combat.CombatListener;
import dev.icaro.foodtooltips.combat.ElementalDamageListener;
import dev.icaro.foodtooltips.combat.MinerVariantService;
import dev.icaro.foodtooltips.combat.MobDifficultyService;
import dev.icaro.foodtooltips.combat.MobVisualService;
import dev.icaro.foodtooltips.crafting.CraftingMenuListener;
import dev.icaro.foodtooltips.crafting.CraftingMenuService;
import dev.icaro.foodtooltips.crafting.RecipeBookListener;
import dev.icaro.foodtooltips.crafting.RecipeBookMenuService;
import dev.icaro.foodtooltips.trash.TrashMenuListener;
import dev.icaro.foodtooltips.trash.TrashMenuService;
import dev.icaro.foodtooltips.destroyer.DestroyerHandListener;
import dev.icaro.foodtooltips.destroyer.DestroyerHandService;
import dev.icaro.foodtooltips.enchant.ArmorEnchantEffectListener;
import dev.icaro.foodtooltips.enchant.BowEnchantEffectListener;
import dev.icaro.foodtooltips.enchant.CustomEnchantEffectListener;
import dev.icaro.foodtooltips.enchant.SpawnerTouchListener;
import dev.icaro.foodtooltips.enchant.MeleeEnchantEffectListener;
import dev.icaro.foodtooltips.enchant.AnvilMenuListener;
import dev.icaro.foodtooltips.enchant.AnvilMenuService;
import dev.icaro.foodtooltips.enchant.EnchantMenuListener;
import dev.icaro.foodtooltips.enchant.EnchantMenuService;
import dev.icaro.foodtooltips.enchant.EnchantMilestoneService;
import dev.icaro.foodtooltips.potion.PotionMilestoneService;
import dev.icaro.foodtooltips.potion.PotionGuideMenuService;
import dev.icaro.foodtooltips.potion.PotionGuideMenuListener;
import dev.icaro.foodtooltips.brewing.BrewingMenuListener;
import dev.icaro.foodtooltips.brewing.BrewingMenuService;
import dev.icaro.foodtooltips.prisma.PrismaPumpListener;
import dev.icaro.foodtooltips.prisma.PrismaPumpService;
import dev.icaro.foodtooltips.sponge.MegaSpongeListener;
import dev.icaro.foodtooltips.sponge.MegaSpongeService;
import dev.icaro.foodtooltips.enchant.EnchantService;
import dev.icaro.foodtooltips.enchant.GrindstoneMenuListener;
import dev.icaro.foodtooltips.enchant.GrindstoneMenuService;
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
import dev.icaro.foodtooltips.item.DurabilityListener;
import dev.icaro.foodtooltips.item.DurabilityService;
import dev.icaro.foodtooltips.item.ItemTierListener;
import dev.icaro.foodtooltips.item.ItemTierService;
import dev.icaro.foodtooltips.item.FarmingCollectionsItemsService;
import dev.icaro.foodtooltips.item.MushroomArmorService;
import dev.icaro.foodtooltips.item.MushroomSoupFlightService;
import dev.icaro.foodtooltips.item.FarmCrystalService;
import dev.icaro.foodtooltips.item.CactusArmorService;
import dev.icaro.foodtooltips.item.ChocolateArmorService;
import dev.icaro.foodtooltips.item.GrowthArmorService;
import dev.icaro.foodtooltips.item.CowHatService;
import dev.icaro.foodtooltips.item.ArcheryPotionService;
import dev.icaro.foodtooltips.item.ManaPotionService;
import dev.icaro.foodtooltips.item.RabbitArmorService;
import dev.icaro.foodtooltips.item.SpeedsterArmorService;
import dev.icaro.foodtooltips.item.EnchantedCarrotStickService;
import dev.icaro.foodtooltips.item.BrewingStandFuelService;
import dev.icaro.foodtooltips.item.LapisArmorService;
import dev.icaro.foodtooltips.item.LeafletArmorService;
import dev.icaro.foodtooltips.item.ForagingCollectionsItemsService;
import dev.icaro.foodtooltips.item.SculptorsAxeService;
import dev.icaro.foodtooltips.item.SculptorsAxeListener;
import dev.icaro.foodtooltips.item.SpruceAxeService;
import dev.icaro.foodtooltips.item.SpruceAxeListener;
import dev.icaro.foodtooltips.item.BedrockSpruceAxeThrowListener;
import dev.icaro.foodtooltips.item.SavannaBowService;
import dev.icaro.foodtooltips.item.MushroomGrowthService;
import dev.icaro.foodtooltips.item.TreecapitatorService;
import dev.icaro.foodtooltips.item.TreecapitatorListener;
import dev.icaro.foodtooltips.item.BedrockTreecapitatorThrowListener;
import dev.icaro.foodtooltips.item.WoodcuttingCrystalService;
import dev.icaro.foodtooltips.item.LapisExperienceService;
import dev.icaro.foodtooltips.item.SwordDamageListener;
import dev.icaro.foodtooltips.item.SwordDamageService;
import dev.icaro.foodtooltips.item.ToolDamageListener;
import dev.icaro.foodtooltips.item.PolearmDamageService;
import dev.icaro.foodtooltips.item.ToolDamageService;
import dev.icaro.foodtooltips.item.legendary.DemonKingStormListener;
import dev.icaro.foodtooltips.item.legendary.LegendaryItemsListener;
import dev.icaro.foodtooltips.item.legendary.LegendaryItemsMenuService;
import dev.icaro.foodtooltips.item.legendary.LegendaryWeaponService;
import dev.icaro.foodtooltips.mining.GemService;
import dev.icaro.foodtooltips.mining.MiningMenuListener;
import dev.icaro.foodtooltips.mining.MiningMenuService;
import dev.icaro.foodtooltips.placeholder.IcarusPlaceholders;
import dev.icaro.foodtooltips.reforge.ReforgeListener;
import dev.icaro.foodtooltips.reforge.ReforgeMenuService;
import dev.icaro.foodtooltips.reforge.ReforgeService;
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
import dev.icaro.foodtooltips.skills.PassiveAbilityListener;
import dev.icaro.foodtooltips.skills.PassiveAbilityMenuService;
import dev.icaro.foodtooltips.skills.PassiveAbilityService;
import dev.icaro.foodtooltips.skills.PlayerStatsViewListener;
import dev.icaro.foodtooltips.skills.QuiverListener;
import dev.icaro.foodtooltips.skills.QuiverService;
import dev.icaro.foodtooltips.skills.WardrobeListener;
import dev.icaro.foodtooltips.skills.WardrobeService;
import dev.icaro.foodtooltips.skills.PotionBagListener;
import dev.icaro.foodtooltips.skills.PotionBagService;
import dev.icaro.foodtooltips.skills.PersonalStorageListener;
import dev.icaro.foodtooltips.skills.PersonalStorageService;
import dev.icaro.foodtooltips.skills.SetSkillLevelCommand;
import dev.icaro.foodtooltips.skills.SkillProgressBarService;
import dev.icaro.foodtooltips.skills.SkillsListener;
import dev.icaro.foodtooltips.skills.SkillsMenuService;
import dev.icaro.foodtooltips.skills.SkillType;
import dev.icaro.foodtooltips.skills.SkillsStarListener;
import dev.icaro.foodtooltips.skills.SkillsStarService;
import dev.icaro.foodtooltips.skills.SwordThrowListener;
import dev.icaro.foodtooltips.stats.PlayerStats;
import dev.icaro.foodtooltips.stats.PlayerStatsService;
import dev.icaro.foodtooltips.stats.ResetStatsCommand;
import dev.icaro.foodtooltips.stats.StatsHudService;
import dev.icaro.foodtooltips.travel.TravelMenuService;
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
    private QuiverService quiver;
    private WardrobeService wardrobe;
    private PotionBagService potionBag;
    private PersonalStorageService storage;

    public void onEnable() {
        this.saveDefaultConfig();
        this.mergeConfigDefaults();
        this.cleanupLegacyCitizensNpcs();
        PlayerStatsService stats = new PlayerStatsService((Plugin)this);
        CombatSkillService combat = new CombatSkillService((Plugin)this);
        GeneralSkillService general = new GeneralSkillService();
        stats.general(general);
        CombatValorService valor = new CombatValorService((Plugin)this);
        ArmorDefenseService armor = new ArmorDefenseService();
        armor.general(general);
        ItemTierService tiers = new ItemTierService((Plugin)this);
        ReforgeService reforgeService = new ReforgeService((Plugin)this, tiers, combat);
        stats.reforge(reforgeService);
        armor.reforge(reforgeService);
        LapisArmorService lapisArmor = new LapisArmorService((Plugin)this, tiers);
        general.armorMiningSpeedBonus(lapisArmor::equippedMiningSpeedBonus);
        general.armorMiningFortuneBonus(lapisArmor::equippedMiningFortuneBonus);
        general.armorXpOrbBonus(lapisArmor::equippedXpOrbBonus);
        lapisArmor.registerRecipes();
        LapisExperienceService lapisExperience = new LapisExperienceService((Plugin)this, tiers);
        lapisExperience.registerRecipes();
        FarmingCollectionsItemsService farmingCollectionsItems = new FarmingCollectionsItemsService((Plugin)this, tiers, general);
        farmingCollectionsItems.registerRecipes();
        general.armorFarmingFortuneBonus(farmingCollectionsItems::farmingFortuneBonus);
        armor.farmerBootsBonus(farmingCollectionsItems::farmerBootsDefenseBonus);
        MushroomSoupFlightService mushroomSoupFlight = new MushroomSoupFlightService();
        CowHatService cowHat = new CowHatService();
        ArcheryPotionService archeryPotion = new ArcheryPotionService();
        ManaPotionService manaPotion = new ManaPotionService();
        RabbitArmorService rabbitArmor = new RabbitArmorService();
        ChocolateArmorService chocolateArmor = new ChocolateArmorService();
        GrowthArmorService growthArmor = new GrowthArmorService();
        SpeedsterArmorService speedsterArmor = new SpeedsterArmorService();
        EnchantedCarrotStickService enchantedCarrotStick = new EnchantedCarrotStickService();
        FarmCrystalService farmCrystal = new FarmCrystalService((Plugin)this);
        farmCrystal.start();
        BrewingStandFuelService brewingStandFuel = new BrewingStandFuelService((Plugin)this);
        brewingStandFuel.start();
        MushroomArmorService mushroomArmor = new MushroomArmorService();
        mushroomArmor.reforge(reforgeService);
        DurabilityService durability = new DurabilityService((Plugin)this);
        SwordDamageService swordDamage = new SwordDamageService((Plugin)this, combat, reforgeService);
        ToolDamageService toolDamage = new ToolDamageService((Plugin)this, combat);
        PolearmDamageService polearmDamage = new PolearmDamageService((Plugin)this, combat);
        PrismaPumpService prismaPump = new PrismaPumpService((Plugin)this);
        MegaSpongeService megaSponge = new MegaSpongeService((Plugin)this);
        BuilderWandService builderWand = new BuilderWandService((Plugin)this, tiers, prismaPump);
        DestroyerHandService destroyerHand = new DestroyerHandService((Plugin)this, tiers);
        BiomeWandService biomeWand = new BiomeWandService((Plugin)this, tiers);
        CombatAbilityService abilities = new CombatAbilityService((Plugin)this, combat, stats, valor);
        stats.abilities(abilities);
        BestiaryProgressService bestiaryProgress = new BestiaryProgressService((Plugin)this);
        GemService gems = new GemService((Plugin)this);
        MiningMenuService mining = new MiningMenuService(gems);
        GlobalLevelService global = new GlobalLevelService((Plugin)this, combat, general, bestiaryProgress);
        stats.global(global);
        SkillsMenuService menus = new SkillsMenuService(combat, general, stats, abilities, mining, global, armor, bestiaryProgress);
        menus.reforge(reforgeService);
        CollectionsProgressService collectionsProgress = new CollectionsProgressService();
        biomeWand.collectionsProgress(collectionsProgress);
        CollectionsService collectionsService = new CollectionsService(collectionsProgress, global);
        CollectionsMenuService collectionsMenu = new CollectionsMenuService(collectionsProgress, global, menus::openMain);
        menus.collections(collectionsMenu);
        SkillsStarService skillsStar = new SkillsStarService((Plugin)this);
        this.quiver = new QuiverService((Plugin)this, combat, skillsStar, menus::openMain);
        menus.quiver(this.quiver);
        this.wardrobe = new WardrobeService((Plugin)this, collectionsProgress);
        menus.wardrobe(this.wardrobe);
        this.potionBag = new PotionBagService((Plugin)this, collectionsProgress, menus::openMain);
        menus.potionBag(this.potionBag);
        LeafletArmorService leafletArmor = new LeafletArmorService((Plugin)this);
        general.armorForagingFortuneBonus(leafletArmor::equippedForagingFortuneBonus);
        SculptorsAxeService sculptorsAxe = new SculptorsAxeService();
        SpruceAxeService spruceAxe = new SpruceAxeService();
        WoodcuttingCrystalService woodcuttingCrystal = new WoodcuttingCrystalService((Plugin)this);
        woodcuttingCrystal.start();
        SavannaBowService savannaBow = new SavannaBowService();
        TreecapitatorService treecapitator = new TreecapitatorService();
        ForagingCollectionsItemsService foragingItems = new ForagingCollectionsItemsService((Plugin)this, biomeWand, sculptorsAxe, spruceAxe, woodcuttingCrystal, savannaBow, treecapitator);
        foragingItems.registerRecipes();
        this.storage = new PersonalStorageService((Plugin)this, collectionsProgress);
        menus.storage(this.storage);
        PassiveAbilityService passives = new PassiveAbilityService();
        PassiveAbilityMenuService passiveAbilityMenu = new PassiveAbilityMenuService(passives, global, menus::openMain);
        menus.passiveAbilities(passiveAbilityMenu);
        LegendaryWeaponService legendary = new LegendaryWeaponService((Plugin)this, stats, tiers, combat, reforgeService);
        reforgeService.legendary(legendary);
        stats.legendary(legendary);
        LegendaryItemsMenuService legendaryItemsMenu = new LegendaryItemsMenuService(legendary);
        this.visuals = new MobVisualService((Plugin)this);
        MobDifficultyService difficulty = new MobDifficultyService((Plugin)this);
        BestiaryMenuService bestiary = new BestiaryMenuService(bestiaryProgress, valor, global);
        this.progressBar = new SkillProgressBarService((Plugin)this);
        LevelColorService levelColors = new LevelColorService((Plugin)this, global);
        LevelBadgeRenderer badgeRenderer = new LevelBadgeRenderer(this.getConfig().getInt("global-level.badge-animation-smoothness", 4));
        GlobalPresentationService presentation = new GlobalPresentationService((Plugin)this, global, levelColors, badgeRenderer);
        mushroomSoupFlight.onScoreboardReplaced(p -> presentation.refreshAll());
        LevelColorMenuService levelColorMenu = new LevelColorMenuService((Plugin)this, global, levelColors, presentation, menus::openMain);
        menus.levelColors(levelColorMenu);
        CombatTreeMenuService treeMenu = new CombatTreeMenuService(combat, abilities, valor, menus::openMain);
        menus.tree(treeMenu);
        TravelMenuService travelMenu = new TravelMenuService((Plugin)this, menus::openMain, collectionsProgress);
        menus.travel(travelMenu);
        CraftingMenuService craftingMenu = new CraftingMenuService(menus::openMain);
        menus.crafting(craftingMenu);
        RecipeBookMenuService recipeBook = new RecipeBookMenuService((Plugin)this, menus::openMain);
        menus.recipeBook(recipeBook);
        collectionsMenu.recipeBook(recipeBook);
        recipeBook.requirementCheck((viewer, recipeKey) -> collectionsService.findGatingMilestone(recipeKey)
                .map(milestone -> new RecipeBookMenuService.Requirement(
                        collectionsService.hasUnlockedRecipe(viewer, recipeKey), milestone.rewardPt(), milestone.rewardEn()))
                .orElse(null));
        // Lapis Lazuli Armor/Experience Bottles have no Collections milestone at all (ungated,
        // per LapisArmorService/LapisExperienceService's own recipe registration) so
        // findGatingCategory can never place them - the player's own explicit call ("aquelas
        // de Lapis podem ficar em mining") is hardcoded here rather than invented from nothing.
        java.util.Set<String> lapisRecipeKeys = java.util.Set.of("lapis_lazuli_helmet", "lapis_lazuli_chestplate",
                "lapis_lazuli_leggings", "lapis_lazuli_boots", "lapis_lazuli_experience_bottle");
        recipeBook.categoryResolver(recipeKey -> collectionsService.findGatingCategory(recipeKey)
                .map(category -> switch (category) {
                    case COMBAT -> RecipeBookMenuService.RecipeCategory.COMBAT;
                    case MINING -> RecipeBookMenuService.RecipeCategory.MINING;
                    case FARMING -> RecipeBookMenuService.RecipeCategory.FARMING;
                    case FORAGING -> RecipeBookMenuService.RecipeCategory.FORAGING;
                    case FISHING -> RecipeBookMenuService.RecipeCategory.FISHING;
                })
                .orElseGet(() -> lapisRecipeKeys.contains(recipeKey.getKey())
                        ? RecipeBookMenuService.RecipeCategory.MINING
                        : RecipeBookMenuService.RecipeCategory.OTHER));
        TrashMenuService trashMenu = new TrashMenuService((Plugin)this, menus::openMain);
        menus.trash(trashMenu);
        EnchantService enchants = new EnchantService((Plugin)this);
        menus.enchants(enchants);
        MinerVariantService minerVariants = new MinerVariantService((Plugin)this, enchants, difficulty, this.visuals, tiers);
        legendaryItemsMenu.minerArmor(minerVariants::createArmorSet);
        legendaryItemsMenu.lapisArmor(lapisArmor::createArmorSet);
        legendaryItemsMenu.grandBottle(lapisExperience::grandBottleGift);
        legendaryItemsMenu.titanicBottle(lapisExperience::titanicBottleGift);
        CollectionsItemsMenuService collectionsItemsMenu = new CollectionsItemsMenuService(legendaryItemsMenu::open);
        legendaryItemsMenu.collectionsItems(p -> collectionsItemsMenu.open(p, 0));
        EnchantMilestoneService enchantMilestones = new EnchantMilestoneService();
        EnchantMenuService enchantMenu = new EnchantMenuService((Plugin)this, enchants, general, this.progressBar, global, enchantMilestones,
                (p) -> menus.openGeneral((Player)p, SkillType.ENCHANTING, 0));
        menus.enchantMenu(enchantMenu);
        enchantMenu.collections(collectionsService);
        PotionMilestoneService potionMilestones = new PotionMilestoneService();
        PotionGuideMenuService potionGuide = new PotionGuideMenuService(global, potionMilestones,
                (p) -> menus.openGeneral((Player)p, SkillType.ALCHEMY, 0));
        menus.potionGuide(potionGuide);
        GrindstoneMenuService grindstoneMenu = new GrindstoneMenuService((Plugin)this, enchants);
        AnvilMenuService anvilMenu = new AnvilMenuService((Plugin)this, enchants);
        ReforgeMenuService reforgeMenu = new ReforgeMenuService((Plugin)this, reforgeService);
        ArmorEnchantEffectListener armorEnchants = new ArmorEnchantEffectListener(enchants);
        armor.protectionBonus(armorEnchants::protectionDefenseBonus);
        // Independent bonus sources composed multiplicatively - a piece can only ever be one
        // or the other (see each service's own doc), but nothing stops mixing pieces from
        // both sets across slots, so this stays correct (2x, 3x or 6x) either way.
        armor.defenseMultiplier(e -> (minerVariants.minerArmorBonusActive(e) ? 2.0 : 1.0) * (mushroomArmor.bonusActive(e) ? 3.0 : 1.0));
        global.onChange(p -> {
            presentation.refresh((Player)p);
            presentation.refreshAll();
        });
        levelColors.onChange(p -> presentation.refreshAll());
        PluginManager pm = this.getServer().getPluginManager();
        pm.registerEvents((Listener)this.visuals, (Plugin)this);
        pm.registerEvents((Listener)new GlobalPlayerListener(global), (Plugin)this);
        pm.registerEvents((Listener)presentation, (Plugin)this);
        pm.registerEvents((Listener)new SkillsListener(menus), (Plugin)this);
        pm.registerEvents((Listener)new PlayerStatsViewListener(menus), (Plugin)this);
        pm.registerEvents((Listener)new CraftingMenuListener(craftingMenu, (Plugin)this), (Plugin)this);
        pm.registerEvents((Listener)new RecipeBookListener(recipeBook), (Plugin)this);
        pm.registerEvents((Listener)new TrashMenuListener(trashMenu), (Plugin)this);
        pm.registerEvents((Listener)new EnchantMenuListener(enchantMenu, (Plugin)this), (Plugin)this);
        pm.registerEvents((Listener)new PotionGuideMenuListener(potionGuide), (Plugin)this);
        pm.registerEvents((Listener)new GrindstoneMenuListener(grindstoneMenu), (Plugin)this);
        pm.registerEvents((Listener)new AnvilMenuListener(anvilMenu), (Plugin)this);
        if (pm.getPlugin("Citizens") != null) {
            pm.registerEvents((Listener)new ReforgeListener(reforgeMenu), (Plugin)this);
        }
        CustomEnchantEffectListener customEnchants = new CustomEnchantEffectListener((Plugin)this, enchants, this.visuals);
        pm.registerEvents((Listener)customEnchants, (Plugin)this);
        pm.registerEvents((Listener)new MeleeEnchantEffectListener((Plugin)this, enchants, this.visuals, abilities), (Plugin)this);
        pm.registerEvents((Listener)lapisExperience, (Plugin)this);
        pm.registerEvents((Listener)new BowEnchantEffectListener((Plugin)this, enchants), (Plugin)this);
        pm.registerEvents((Listener)new SpawnerTouchListener(enchants), (Plugin)this);
        pm.registerEvents((Listener)new SkillsStarListener((Plugin)this, skillsStar, menus), (Plugin)this);
        pm.registerEvents((Listener)new CombatTreeListener(treeMenu), (Plugin)this);
        GeneralSkillListener generalSkillListener = new GeneralSkillListener((Plugin)this, general, this.progressBar, global, enchants, passives, collectionsService);
        pm.registerEvents((Listener)generalSkillListener, (Plugin)this);
        BrewingMenuService brewingMenu = new BrewingMenuService(brewingStandFuel, generalSkillListener, potionGuide);
        pm.registerEvents((Listener)new BrewingMenuListener((Plugin)this, brewingMenu), (Plugin)this);
        pm.registerEvents((Listener)new CollectionsListener(collectionsMenu, collectionsService), (Plugin)this);
        pm.registerEvents((Listener)new CollectionsRecipeGateListener(collectionsService), (Plugin)this);
        pm.registerEvents((Listener)new QuiverListener(this.quiver), (Plugin)this);
        pm.registerEvents((Listener)new WardrobeListener(this.wardrobe, menus::openMain), (Plugin)this);
        pm.registerEvents((Listener)new PotionBagListener(this.potionBag), (Plugin)this);
        pm.registerEvents((Listener)new PersonalStorageListener(this.storage), (Plugin)this);
        pm.registerEvents((Listener)mushroomSoupFlight, (Plugin)this);
        pm.registerEvents((Listener)cowHat, (Plugin)this);
        pm.registerEvents((Listener)archeryPotion, (Plugin)this);
        pm.registerEvents((Listener)manaPotion, (Plugin)this);
        pm.registerEvents((Listener)farmCrystal, (Plugin)this);
        pm.registerEvents((Listener)new CactusArmorService(), (Plugin)this);
        pm.registerEvents((Listener)new MushroomGrowthService(), (Plugin)this);
        pm.registerEvents((Listener)growthArmor, (Plugin)this);
        pm.registerEvents((Listener)brewingStandFuel, (Plugin)this);
        pm.registerEvents((Listener)new PassiveAbilityListener(passiveAbilityMenu), (Plugin)this);
        pm.registerEvents((Listener)gems, (Plugin)this);
        pm.registerEvents((Listener)new MiningMenuListener(mining, menus, gems), (Plugin)this);
        pm.registerEvents((Listener)new BestiaryListener(bestiary), (Plugin)this);
        CombatListener combatListener = new CombatListener((Plugin)this, combat, this.visuals, bestiaryProgress, this.progressBar, abilities, global, stats, valor, armor, general, legendary, enchants, difficulty, passives, reforgeService);
        combatListener.archeryPotionPercent(archeryPotion::bonusPercent);
        // ArmorDefenseListener#defense and armorEnchants' protection() both reduce
        // incoming damage at EventPriority.HIGHEST on EntityDamageEvent, same as
        // CombatListener#secondWind - Bukkit runs same-priority handlers in
        // registration order, and Second Wind needs to judge lethality using the
        // FINAL, already-mitigated damage, not the raw pre-mitigation number. So
        // these two must be registered before combatListener. (CombatListener#damage,
        // the attacker-side damage calculation, is a separate EventPriority.HIGH
        // handler and always runs before any HIGHEST handler regardless of
        // registration order - see its own priority comment.)
        pm.registerEvents((Listener)new ArmorDefenseListener(armor), (Plugin)this);
        pm.registerEvents((Listener)armorEnchants, (Plugin)this);
        pm.registerEvents((Listener)combatListener, (Plugin)this);
        pm.registerEvents((Listener)minerVariants, (Plugin)this);
        pm.registerEvents((Listener)new ElementalDamageListener(this.visuals), (Plugin)this);
        pm.registerEvents((Listener)new LegendaryItemsListener(legendaryItemsMenu), (Plugin)this);
        pm.registerEvents((Listener)new CollectionsItemsMenuListener(collectionsItemsMenu), (Plugin)this);
        pm.registerEvents((Listener)new DemonKingStormListener((Plugin)this, stats, abilities), (Plugin)this);
        pm.registerEvents((Listener)new ItemTierListener(tiers), (Plugin)this);
        pm.registerEvents((Listener)new DurabilityListener(durability), (Plugin)this);
        pm.registerEvents((Listener)new SwordDamageListener(swordDamage), (Plugin)this);
        pm.registerEvents((Listener)new ToolDamageListener(toolDamage), (Plugin)this);
        pm.registerEvents((Listener)new BuilderWandListener(builderWand), (Plugin)this);
        pm.registerEvents((Listener)new DestroyerHandListener(destroyerHand), (Plugin)this);
        pm.registerEvents((Listener)new BiomeWandListener(biomeWand), (Plugin)this);
        pm.registerEvents((Listener)new PrismaPumpListener(prismaPump), (Plugin)this);
        pm.registerEvents((Listener)new MegaSpongeListener(megaSponge), (Plugin)this);
        pm.registerEvents((Listener)new SculptorsAxeListener(sculptorsAxe), (Plugin)this);
        SpruceAxeListener spruceAxeThrow = new SpruceAxeListener((Plugin)this, spruceAxe);
        pm.registerEvents((Listener)spruceAxeThrow, (Plugin)this);
        pm.registerEvents((Listener)new BedrockSpruceAxeThrowListener(spruceAxeThrow), (Plugin)this);
        pm.registerEvents((Listener)savannaBow, (Plugin)this);
        TreecapitatorListener treecapitatorThrow = new TreecapitatorListener((Plugin)this, treecapitator);
        pm.registerEvents((Listener)treecapitatorThrow, (Plugin)this);
        pm.registerEvents((Listener)new BedrockTreecapitatorThrowListener(treecapitatorThrow), (Plugin)this);
        pm.registerEvents((Listener)woodcuttingCrystal, (Plugin)this);
        GeyserSkullExport.export((Plugin)this, gems, global);
        SwordThrowListener swordThrow = new SwordThrowListener((Plugin)this, abilities);
        pm.registerEvents((Listener)swordThrow, (Plugin)this);
        pm.registerEvents((Listener)new BedrockSwordThrowListener(swordThrow), (Plugin)this);
        FoodTooltipListener foodListener = new FoodTooltipListener((Plugin)this, new FoodTooltipService(), tiers, enchants);
        pm.registerEvents((Listener)foodListener, (Plugin)this);
        SetSkillLevelCommand setSkill = new SetSkillLevelCommand(combat, general, global);
        this.getCommand("setskilllevel").setExecutor((CommandExecutor)setSkill);
        this.getCommand("setskilllevel").setTabCompleter((TabCompleter)setSkill);
        ResetStatsCommand resetStats = new ResetStatsCommand(stats, combat, armor, global, bestiaryProgress, general);
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
        this.getCommand("prismapump").setExecutor((s, c, l, a) -> {
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
                s.sendMessage((Component)Component.text((String)"Usage: /prismapump [player]"));
                return true;
            }
            target.getInventory().addItem(prismaPump.create(Language.of(target)));
            s.sendMessage((Component)Component.text((String)(this.text(s, "Prismapump entregue a ", "Prismapump given to ") + target.getName() + "."), (TextColor)NamedTextColor.GREEN));
            return true;
        });
        this.getCommand("megasponge").setExecutor((s, c, l, a) -> {
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
                s.sendMessage((Component)Component.text((String)"Usage: /megasponge [player]"));
                return true;
            }
            target.getInventory().addItem(megaSponge.create(Language.of(target)));
            s.sendMessage((Component)Component.text((String)(this.text(s, "Mega Sponge entregue a ", "Mega Sponge given to ") + target.getName() + "."), (TextColor)NamedTextColor.GREEN));
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
        double baseManaRegenPerSecond = this.getConfig().getDouble("stats.mana-regeneration-per-second", 2.0);
        double vitalityRegen = this.getConfig().getDouble("stats.vitality-regeneration-per-second", 4.0) * (double)ticks / 20.0;
        double naturalHealthRegenPerSecond = this.getConfig().getDouble("stats.natural-health-regen-per-second", 0.5);
        this.getServer().getScheduler().runTaskTimer((Plugin)this, () -> this.getServer().getOnlinePlayers().forEach(p -> {
            double manaRegen = (baseManaRegenPerSecond + manaPotion.regenBonusPerSecond((Player)p)) * (double)ticks / 20.0;
            stats.regen((Player)p, manaRegen);
            stats.regenVitality((Player)p, vitalityRegen);
            double healthRegenMultiplier = stats.stats((Player)p).healthRegen() / 100.0;
            stats.regenHealth((Player)p, naturalHealthRegenPerSecond * healthRegenMultiplier * (double)ticks / 20.0);
            armor.neutralizeVanillaArmor((Player)p);
            armor.applyDefenseTooltip((Player)p);
            armorEnchants.applyRespiration((Player)p);
            armorEnchants.applyGrowthHealth((Player)p);
            customEnchants.applyBowDamageTooltip((Player)p);
            tiers.applyItemTiers((Player)p);
            enchants.applyToInventory((Player)p);
            durability.applyDurability((Player)p);
            swordDamage.applySwordDamage((Player)p);
            toolDamage.applyToolDamage((Player)p);
            polearmDamage.applyPolearmDamage((Player)p);
            general.applyMiningSpeedAttribute((Player)p);
            legendary.refreshStrengthLore((Player)p);
            legendary.refreshAttackSpeedLore((Player)p);
            reforgeService.applyArmorAttackSpeedModifiers((Player)p);
            mushroomArmor.applyToInventory((Player)p);
            farmingCollectionsItems.applyFarmerBootsSpeed((Player)p);
            farmingCollectionsItems.applyLanternHelmetHealth((Player)p);
            rabbitArmor.applyToInventory((Player)p);
            chocolateArmor.applyToInventory((Player)p);
            growthArmor.applyToInventory((Player)p);
            speedsterArmor.applyFullSetSpeed((Player)p);
            enchantedCarrotStick.applyMountSpeed((Player)p);
            mushroomSoupFlight.tick((Player)p, ticks);
            archeryPotion.tick((Player)p, ticks);
            manaPotion.tick((Player)p, ticks);
            lapisArmor.applyToInventory((Player)p);
            lapisExperience.applyToInventory((Player)p);
            builderWand.tickPreview((Player)p);
            brewingMenu.tick((Player)p);
            // Last metadata writer: validates the real PROFILE component after every
            // other item service, so none can accidentally restore the Base64 profile
            // on Java (or the skin patch on Bedrock) until the next sweep.
            minerVariants.applyToInventory((Player)p);
            this.quiver.topUp((Player)p);
            hud.show((Player)p, stats.stats((Player)p), armor.defense((Player)p));
        }), 1L, ticks);
        this.getServer().getScheduler().runTaskTimer((Plugin)this, this.visuals::tick, 1L, Math.max(1L, this.getConfig().getLong("mob-visuals.update-ticks", 3L)));
        AnimalSeparationService animalSeparation = new AnimalSeparationService();
        this.getServer().getScheduler().runTaskTimer((Plugin)this, animalSeparation::separateAll, 1L, 10L);
        for (World w : this.getServer().getWorlds()) {
            for (LivingEntity e : w.getLivingEntities()) {
                difficulty.scale(e);
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
            swordDamage.applySwordDamage((Player)p);
            toolDamage.applyToolDamage((Player)p);
            polearmDamage.applyPolearmDamage((Player)p);
            general.applyMiningSpeedAttribute((Player)p);
            legendary.refreshStrengthLore((Player)p);
            legendary.refreshAttackSpeedLore((Player)p);
            reforgeService.applyArmorAttackSpeedModifiers((Player)p);
            mushroomArmor.applyToInventory((Player)p);
            farmingCollectionsItems.applyFarmerBootsSpeed((Player)p);
            bestiaryProgress.applyBonusHealth((Player)p);
            general.applyBonusHealth((Player)p);
            skillsStar.ensure((Player)p);
            global.migrate((Player)p);
            collectionsService.syncDiscoveredRecipes((Player)p);
            if (!((Player)p).isDead()) {
                ((Player)p).setHealth(Math.min(healthBefore, stats.stats((Player)p).maxHealth()));
            }
            foodListener.refresh((Player)p);
            this.visuals.track((LivingEntity)p);
        });
        Bukkit.getScheduler().runTask((Plugin)this, presentation::refreshAll);
        if (this.getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new IcarusPlaceholders(global).register();
        }
    }

    public void onDisable() {
        if (this.visuals != null) {
            this.visuals.shutdown();
        }
        if (this.progressBar != null) {
            this.progressBar.shutdown();
        }
        if (this.quiver != null) {
            this.quiver.saveAll();
            this.wardrobe.saveAll();
            this.potionBag.saveAll();
            this.storage.saveAll();
        }
    }

    /**
     * saveDefaultConfig() only ever writes config.yml if it doesn't exist yet on disk -
     * a server upgrading from an older jar keeps its existing file untouched, so any
     * section a newer version adds is simply missing there and every
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

