package dev.icaro.foodtooltips.util;

import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

/**
 * The shared "boxed" chat announcement every level-up/milestone message in this plugin uses
 * (Global Level, per-skill level-ups, Bestiary/Collections milestones, Enchanting level-ups) -
 * a top/bottom dashed border with the title and reward lines centered (see {@link ChatCenter})
 * and breathing room (a blank line) between the title and the body, and again before the
 * closing border, per the player's own "centralizado, bonito, espaçado" spec. Pulled out into
 * one place instead of five near-identical copies so all of them stay visually consistent and
 * only need updating once.
 */
public final class AnnouncementMessage {
    private static final Component BORDER = Component.text("━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_GRAY);

    private AnnouncementMessage() {
    }

    /** Sends the full box: border, title, a blank line, every line in {@code lines} (each centered on its own), another blank line, border. */
    public static void send(Player p, Component title, List<Component> lines) {
        p.sendMessage(ChatCenter.center(BORDER));
        p.sendMessage(ChatCenter.center(title));
        p.sendMessage(Component.empty());
        for (Component line : lines) {
            p.sendMessage(ChatCenter.center(line));
        }
        p.sendMessage(Component.empty());
        p.sendMessage(ChatCenter.center(BORDER));
    }
}
