package dev.icaro.foodtooltips.crafting;

import io.papermc.paper.event.server.ServerResourcesReloadedEvent;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.Recipe;
import org.bukkit.plugin.Plugin;

/**
 * Puts this plugin's own recipes back after a data reload. Minecraft rebuilds its whole recipe
 * list from the datapacks whenever the server's data is reloaded ({@code /minecraft:reload},
 * {@code /reload}, or any plugin calling {@link Bukkit#reloadData()}), and every recipe a plugin
 * added through {@link Bukkit#addRecipe} is simply gone afterwards - confirmed on a real Paper
 * 26.2 server: before the reload all 155 of this plugin's Collections recipes resolved, after it
 * none did. Every Collections milestone then fell back to its plain glass-pane icon ("os
 * collections estão mostrando só o vidro ao invés dos itens") and the items stopped being
 * craftable at all.
 *
 * <p>{@link #snapshot} remembers every registered recipe in this plugin's own namespaces once
 * startup has finished registering them, in registration order; {@link #reloaded} re-adds any
 * of them that went missing, in that same order - order matters, since Paper takes the first
 * recipe that matches a grid ({@code item.CombatCollectionsItemsService#registerZombiesHeartRecipes}
 * relies on being registered last).
 */
public final class RecipeKeeper implements Listener {
    /** Every namespace this plugin's own recipe keys use - the hardcoded legacy one plus the plugin's own (see {@code RecipeBookMenuService#LEGACY_RECIPE_NAMESPACE}). */
    private final Set<String> namespaces;
    private final Plugin plugin;
    private final Map<NamespacedKey, Recipe> recipes = new LinkedHashMap<>();

    public RecipeKeeper(Plugin plugin) {
        this.plugin = plugin;
        this.namespaces = Set.of("foodtooltips", new NamespacedKey(plugin, "probe").getNamespace());
    }

    /** Remembers this plugin's currently registered recipes - call once every service has registered its own (a tick after onEnable). */
    public void snapshot() {
        this.recipes.clear();
        Iterator<Recipe> it = Bukkit.recipeIterator();
        while (it.hasNext()) {
            Recipe recipe = it.next();
            if (recipe instanceof Keyed keyed && this.namespaces.contains(keyed.getKey().getNamespace())) {
                this.recipes.put(keyed.getKey(), recipe);
            }
        }
        this.plugin.getLogger().info("Tracking " + this.recipes.size() + " recipes to restore after data reloads.");
    }

    @EventHandler
    public void reloaded(ServerResourcesReloadedEvent e) {
        int restored = 0;
        for (Map.Entry<NamespacedKey, Recipe> entry : this.recipes.entrySet()) {
            if (Bukkit.getRecipe(entry.getKey()) == null && Bukkit.addRecipe(entry.getValue(), false)) {
                restored++;
            }
        }
        if (restored > 0) {
            Bukkit.updateRecipes();
            this.plugin.getLogger().info("Data reload removed " + restored + " recipes - restored them.");
        }
    }
}
