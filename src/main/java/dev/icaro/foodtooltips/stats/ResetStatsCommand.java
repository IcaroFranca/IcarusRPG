package dev.icaro.foodtooltips.stats;

import dev.icaro.foodtooltips.bestiary.BestiaryProgressService;
import dev.icaro.foodtooltips.economy.EconomyService;
import dev.icaro.foodtooltips.global.GlobalLevelService;
import dev.icaro.foodtooltips.i18n.Language;
import dev.icaro.foodtooltips.skills.ArmorDefenseService;
import dev.icaro.foodtooltips.skills.CombatSkillService;
import dev.icaro.foodtooltips.skills.GeneralSkillService;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;

/**
 * {@code /resetstats <player>} — wipes every stat this plugin has ever stored on a
 * player back to a fresh character: Mana/Vitality, Combat level+XP, General skills,
 * Combat Ability tree ranks, Combat Valor, Coins, Global Level (+ checkpoints and
 * migration flag), Bestiary kills/milestones — all of it.
 *
 * <p>Deliberately blunt rather than picking individual systems apart: removes every
 * {@link PersistentDataContainer} key under the {@code "foodtooltips"} namespace,
 * the fixed literal namespace every player-stat key in this plugin uses (see e.g.
 * {@link CombatSkillService}, {@link PlayerStatsService}, {@link GlobalLevelService},
 * {@link BestiaryProgressService}) — as opposed to item metadata (Item Tier, Sword
 * Damage, Builder's Wand/Destroyer's Hand settings), which lives on the item's own
 * {@code ItemMeta} under a per-plugin-instance namespace, never on the player, so
 * it's untouched by this. Covers every current *and future* player stat key
 * automatically, rather than needing a hand-maintained list kept in sync by hand
 * every time a new stat is added anywhere in the plugin.
 *
 * <p><b>Not</b> touched: inventory/items, vanilla XP/level, position, gamemode.
 */
public final class ResetStatsCommand implements TabExecutor {
    private final PlayerStatsService stats;
    private final CombatSkillService combat;
    private final ArmorDefenseService armor;
    private final GlobalLevelService global;
    private final BestiaryProgressService bestiary;
    private final EconomyService economy;
    private final GeneralSkillService general;

    public ResetStatsCommand(PlayerStatsService stats, CombatSkillService combat, ArmorDefenseService armor,
                              GlobalLevelService global, BestiaryProgressService bestiary, EconomyService economy,
                              GeneralSkillService general) {
        this.stats = stats;
        this.combat = combat;
        this.armor = armor;
        this.global = global;
        this.bestiary = bestiary;
        this.economy = economy;
        this.general = general;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Language language = sender instanceof Player p ? Language.of(p) : Language.EN;
        if (args.length != 1) {
            this.message(sender, language.choose("Uso: ", "Usage: ") + "/resetstats <player>", NamedTextColor.RED);
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            target = Bukkit.getPlayer(args[0]);
        }
        if (target == null) {
            this.message(sender, language.choose("Jogador não encontrado ou offline.", "Player not found or offline."), NamedTextColor.RED);
            return true;
        }
        this.reset(target);
        this.message(sender, language.choose("Status resetados: ", "Stats reset: ") + target.getName(), NamedTextColor.GREEN);
        if (sender != target) {
            Language targetLanguage = Language.of(target);
            this.message(target, targetLanguage.choose("Seus status foram resetados pra os valores iniciais.", "Your stats were reset to their initial values."), NamedTextColor.GOLD);
        }
        return true;
    }

    private void reset(Player target) {
        PersistentDataContainer d = target.getPersistentDataContainer();
        for (NamespacedKey key : new ArrayList<>(d.getKeys())) {
            if (key.getNamespace().equals("foodtooltips")) {
                d.remove(key);
            }
        }
        // Re-derive every attribute-backed bonus (they're transient modifiers keyed
        // off the PDC data just wiped, so they don't clear themselves) - same pipeline
        // CombatListener#reapplyHealthStack and the join/onEnable cycle already use.
        this.stats.init(target);
        this.stats.applyBaseHealth(target);
        this.combat.applyAttackSpeed(target);
        this.stats.applySwingRange(target);
        this.armor.neutralizeVanillaArmor(target);
        this.armor.applyDefenseTooltip(target);
        this.bestiary.applyBonusHealth(target);
        this.global.applyHealth(target);
        this.general.applyBonusHealth(target);
        if (!target.isDead()) {
            AttributeInstance a = target.getAttribute(Attribute.MAX_HEALTH);
            target.setHealth(a == null ? 20.0 : a.getValue());
        }
        this.economy.updateBoard(target);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .toList();
        }
        return List.of();
    }

    private void message(CommandSender sender, String text, NamedTextColor color) {
        sender.sendMessage(Component.text(text, (TextColor) color));
    }
}
