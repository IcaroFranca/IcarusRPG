package rtest;

import com.mojang.authlib.GameProfile;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.papermc.paper.datacomponent.DataComponentTypes;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.net.InetSocketAddress;
import java.util.*;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.Connection;
import net.minecraft.network.HashedStack;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Opens IcarusRPG's menus with a fake player - /skills and every button two levels deep, plus the
 * spawner menu - and writes each distinct screen (exact title string, size, every item with its
 * item_model) to menus.jsonl for the offline renderer/checker.
 */
public class MenuDump extends JavaPlugin {
    Player p; ServerPlayer sp; World w; Block spawner;
    Writer out;
    final Set<String> seen = new HashSet<>();
    final Deque<List<Integer>> queue = new ArrayDeque<>();
    int captured;
    void log(String s) { getLogger().info(s); }

    static String esc(String s) {
        StringBuilder b = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (c == '"' || c == '\\') b.append('\\').append(c);
            else if (c < 32 || c > 126) b.append(String.format("\\u%04x", (int) c));
            else b.append(c);
        }
        return b.toString();
    }

    void click(int slot) {
        var menu = sp.containerMenu;
        sp.connection.handleContainerClick(new ServerboundContainerClickPacket(menu.containerId, menu.getStateId(), (short) slot, (byte) 0, ContainerInput.PICKUP, new Int2ObjectOpenHashMap<>(), HashedStack.EMPTY));
    }

    static boolean filler(ItemStack i) {
        if (i == null || i.isEmpty()) return true;
        if (i.getType() != Material.GRAY_STAINED_GLASS_PANE) return false;
        var name = i.getItemMeta().displayName();
        return name == null || PlainTextComponentSerializer.plainText().serialize(name).isBlank();
    }

    /** Writes the open screen; returns its non-filler slots if it's a new one, else null. */
    List<Integer> capture(String path) {
        var view = p.getOpenInventory();
        Inventory top = view.getTopInventory();
        if (top.getType() != org.bukkit.event.inventory.InventoryType.CHEST) return null;
        StringBuilder items = new StringBuilder();
        StringBuilder sig = new StringBuilder(view.getTitle()).append('|').append(top.getSize());
        List<Integer> buttons = new ArrayList<>();
        for (int s = 0; s < top.getSize(); s++) {
            ItemStack i = top.getItem(s);
            if (i == null || i.isEmpty()) continue;
            Key model = i.getData(DataComponentTypes.ITEM_MODEL);
            String name = i.getItemMeta().displayName() == null ? "" : PlainTextComponentSerializer.plainText().serialize(i.getItemMeta().displayName());
            if (items.length() > 0) items.append(',');
            items.append("{\"slot\":").append(s).append(",\"material\":\"").append(i.getType().getKey().getKey())
                    .append("\",\"model\":").append(model == null ? "null" : "\"" + model.asString() + "\"")
                    .append(",\"filler\":").append(filler(i)).append(",\"name\":\"").append(esc(name)).append("\"}");
            sig.append(s).append(i.getType()).append(';');
            if (!filler(i)) buttons.add(s);
        }
        String plainTitle = view.getTitle();
        if (!seen.add(sig.toString().replaceAll("[0-9]", ""))) return null;
        try {
            out.write("{\"path\":\"" + esc(path) + "\",\"title\":\"" + esc(plainTitle) + "\",\"size\":" + top.getSize() + ",\"items\":[" + items + "]}\n");
            out.flush();
        } catch (IOException e) { throw new RuntimeException(e); }
        captured++;
        return buttons;
    }

    List<Runnable> steps = new ArrayList<>(); int idx = 0;
    void step(int delay, Runnable r) { steps.add(() -> Bukkit.getScheduler().runTaskLater(this, () -> { try { r.run(); } catch (Throwable t) { getLogger().severe("step failed: " + t); t.printStackTrace(); } next(); }, delay)); }
    void next() { if (idx < steps.size()) steps.get(idx++).run(); else finish(); }
    void finish() { log("captured " + captured + " menus"); try { out.close(); } catch (IOException ignored) {} Bukkit.shutdown(); }

    /** Replays a click path from /skills, captures where it lands, and queues its buttons one level deeper. */
    void explore(List<Integer> path, int depth) {
        step(2, () -> { p.closeInventory(); p.setItemOnCursor(null); p.performCommand("skills"); });
        for (int slot : path) step(4, () -> click(slot));
        step(4, () -> {
            List<Integer> buttons = capture(path.isEmpty() ? "skills" : "skills>" + path);
            if (buttons != null && depth < 2) {
                for (int b : buttons) {
                    List<Integer> next = new ArrayList<>(path);
                    next.add(b);
                    explore(next, depth + 1);
                }
            }
        });
    }

    @Override public void onEnable() { Bukkit.getScheduler().runTaskLater(this, this::run, 40); }

    void run() {
        try { getDataFolder().mkdirs(); out = new FileWriter(new java.io.File(getDataFolder(), "menus.jsonl")); } catch (IOException e) { throw new RuntimeException(e); }
        MinecraftServer server = ((CraftServer) Bukkit.getServer()).getServer();
        w = Bukkit.getWorlds().get(0);
        GameProfile gp = new GameProfile(UUID.randomUUID(), "Tester");
        sp = new ServerPlayer(server, ((CraftWorld) w).getHandle(), gp, ClientInformation.createDefault());
        Connection conn = new Connection(PacketFlow.SERVERBOUND);
        conn.channel = new EmbeddedChannel(new ChannelInboundHandlerAdapter());
        conn.address = new InetSocketAddress("127.0.0.1", 0);
        server.getPlayerList().placeNewPlayer(conn, sp, new CommonListenerCookie(gp, 0, ClientInformation.createDefault(), false, null, Set.of(), new io.papermc.paper.util.KeepAlive()));
        p = sp.getBukkitEntity();
        p.setGameMode(GameMode.SURVIVAL);
        p.setOp(true);
        try { var loaded = net.minecraft.server.network.ServerGamePacketListenerImpl.class.getDeclaredMethod("markClientLoaded"); loaded.setAccessible(true); loaded.invoke(sp.connection); } catch (Exception ex) { log("markClientLoaded: " + ex); }

        int gx = 300, gz = 0, gy = w.getHighestBlockYAt(gx, gz) + 1;
        step(5, () -> {
            for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) { w.getBlockAt(gx + x, gy - 1, gz + z).setType(Material.STONE); for (int y = 0; y < 4; y++) w.getBlockAt(gx + x, gy + y, gz + z).setType(Material.AIR); }
            spawner = w.getBlockAt(gx, gy, gz);
            spawner.setType(Material.SPAWNER);
            CreatureSpawner cs = (CreatureSpawner) spawner.getState(); cs.setSpawnedType(EntityType.ZOMBIE); cs.update();
            p.teleport(new Location(w, gx - 2.5, gy, gz + 0.5, -90, 0));
            try { var f = net.minecraft.server.network.ServerGamePacketListenerImpl.class.getDeclaredField("awaitingPositionFromClient"); f.setAccessible(true); f.set(sp.connection, null); } catch (Exception ex) { log("await: " + ex); }
        });
        step(4, () -> {
            sp.connection.handleUseItemOn(new ServerboundUseItemOnPacket(InteractionHand.MAIN_HAND,
                    new BlockHitResult(new Vec3(gx + 0.5, gy + 0.5, gz + 0.5), Direction.WEST, new BlockPos(gx, gy, gz), false), 0));
        });
        step(4, () -> capture("spawner"));
        step(2, () -> click(51));
        step(4, () -> capture("spawner>color"));
        explore(new ArrayList<>(), 0);
        next();
    }
}
