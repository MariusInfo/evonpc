package org.evocraft.evonpc.npc;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.evocraft.evonpc.EvoNPC;
import org.evocraft.evocore.database.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.evocraft.evocore.util.EvoCurrencyFormatter;

@Mod.EventBusSubscriber(modid = EvoNPC.MODID)
public class TopManager {

    private static int tickCounter = 0;
    private static final int UPDATE_INTERVAL_TICKS = 20 * 60 * 5;
    private static final AtomicBoolean UPDATING = new AtomicBoolean(false);

    public static final Map<String, Map<Integer, TopEntry>> topData = new ConcurrentHashMap<>();

    public static class TopEntry {
        public String uuid;
        public String name;
        public String displayValue;
        public TopEntry(String u, String n, String v) { uuid = u; name = n; displayValue = v; }
    }

    public static void forceUpdateNow(MinecraftServer server) {
        if (server == null) return;
        tickCounter = 0;
        updateDatabaseAsync(server);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        tickCounter++;
        if (tickCounter >= UPDATE_INTERVAL_TICKS || tickCounter == 20) {
            if (tickCounter >= UPDATE_INTERVAL_TICKS) tickCounter = 0;

            MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                updateDatabaseAsync(server);
            }
        }
    }

    private static void updateDatabaseAsync(MinecraftServer server) {
        if (!UPDATING.compareAndSet(false, true)) return;
        Thread t = new Thread(() -> {
            try {
                synchronized (DatabaseManager.get()) {
                    try {
                        Connection conn = DatabaseManager.get().getConnection();
                        if (conn == null || conn.isClosed()) return;

                        String filter = " AND uuid != '00000000-0000-0000-0000-000000000000' " +
                                "AND name IS NOT NULL AND name != '' AND name != 'Necunoscut' AND name != 'Unknown' " +
                                "AND name NOT LIKE '?%' AND name NOT LIKE '§%' ";

                        fetchCategory(conn, "money", "SELECT uuid, name, balance AS val FROM player_stats WHERE balance > 0" + filter + "ORDER BY balance DESC LIMIT 3", false, " Evo");
                        fetchCategory(conn, "kills", "SELECT uuid, name, kills AS val FROM player_stats WHERE kills > 0" + filter + "ORDER BY kills DESC LIMIT 3", false, " Kills");
                        fetchCategory(conn, "deaths", "SELECT uuid, name, deaths AS val FROM player_stats WHERE deaths > 0" + filter + "ORDER BY deaths DESC LIMIT 3", false, " Deaths");
                        fetchCategory(conn, "hours", "SELECT uuid, name, playtime_sec AS val FROM player_stats WHERE playtime_sec > 0" + filter + "ORDER BY playtime_sec DESC LIMIT 3", true, " Hours");
                        fetchCategory(conn, "claims", "SELECT uuid, name, claims AS val FROM player_stats WHERE claims > 0" + filter + "ORDER BY claims DESC LIMIT 3", false, " Protections");

                        // Backward compatibility aliases
                        Map<Integer, TopEntry> moneyMap = topData.get("money");
                        if (moneyMap != null) topData.put("bani", moneyMap);
                        Map<Integer, TopEntry> hoursMap = topData.get("hours");
                        if (hoursMap != null) topData.put("ore", hoursMap);
                        Map<Integer, TopEntry> deathsMap = topData.get("deaths");
                        if (deathsMap != null) topData.put("decese", deathsMap);

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                server.execute(() -> updateNPCsInWorld(server));
            } finally {
                UPDATING.set(false);
            }
        }, "EvoNPC Leaderboard Poller");
        t.setDaemon(true);
        t.start();
    }

    private static void fetchCategory(Connection conn, String category, String query, boolean isTime, String suffix) {
        try (PreparedStatement stmt = conn.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {

            Map<Integer, TopEntry> categoryMap = new HashMap<>();
            int rank = 1;
            while (rs.next()) {
                String uuid = rs.getString("uuid");
                String name = rs.getString("name");
                double val = rs.getDouble("val");
                if (!Double.isFinite(val) || val < 0.0) val = 0.0;

                String displayVal;
                if (isTime) {
                    displayVal = String.format(Locale.US, "%.1f", val / 3600.0) + suffix;
                } else if ("money".equals(category)) {
                    displayVal = EvoCurrencyFormatter.format(val) + suffix;
                } else {
                    displayVal = ((long) val) + suffix;
                }

                if (name != null && !name.isEmpty() && !name.equals("Necunoscut") && !name.equals("Unknown")) {
                    categoryMap.put(rank, new TopEntry(uuid != null ? uuid : "", name, displayVal));
                    rank++;
                }
            }
            topData.put(category, Collections.unmodifiableMap(categoryMap));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void updateNPCsInWorld(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            for (net.minecraft.world.entity.Entity entity : level.getAllEntities()) {
                if (entity instanceof TopNPC npc) {
                    String cat = npc.getCategory();
                    int rank = npc.getRank();

                    Map<Integer, TopEntry> catMap = topData.get(cat);
                    TopEntry entry = catMap != null ? catMap.get(rank) : null;
                    if (entry != null) {
                        npc.setPlayerName(entry.name);
                        npc.setDisplayValue(entry.displayValue);
                        npc.setUUIDStr(entry.uuid);
                    } else {
                        npc.setPlayerName("Coming soon...");
                        npc.setDisplayValue("0");
                        npc.setUUIDStr("");
                    }
                }
            }
        }
    }
}
