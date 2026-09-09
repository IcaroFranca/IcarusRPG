package dev.icaro.foodtooltips.skills;

import org.bukkit.Material;

/**
 * Every combat ability, active or passive. Unlocking/upgrading a node is
 * gated by Blood Points, the tree's prerequisite chain (see {@link
 * CombatTreeNode}), and a minimum Combat level per tree tier (see {@link
 * CombatAbilityService#levelRequirement}) — handled by {@link
 * CombatAbilityService}.
 */
public enum CombatAbility {
    RUTHLESS_STRIKES(Material.FLINT, "Golpes Implacáveis", "Ruthless Strikes"),
    SWORD_THROW(Material.IRON_SWORD, "Arremesso de Espada", "Sword Throw"),
    BLOOD_LUST(Material.REDSTONE, "Sede de Sangue", "Blood Lust"),
    BERSERKER(Material.NETHERITE_AXE, "Berserker", "Berserker"),
    SOUL_HARVEST(Material.ECHO_SHARD, "Colheita de Almas", "Soul Harvest"),
    CRITICAL_MASTERY(Material.NETHERITE_SWORD, "Maestria Crítica", "Critical Mastery"),
    SECOND_WIND(Material.TOTEM_OF_UNDYING, "Segundo Fôlego", "Second Wind");

    private final Material icon;
    private final String pt;
    private final String en;

    CombatAbility(Material icon, String pt, String en) {
        this.icon = icon;
        this.pt = pt;
        this.en = en;
    }

    public Material icon() {
        return this.icon;
    }

    public String name(boolean portuguese) {
        return portuguese ? this.pt : this.en;
    }
}
