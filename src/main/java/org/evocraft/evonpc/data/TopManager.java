package org.evocraft.evonpc.data;
import org.evocraft.evocore.data.EconomyManager;
import org.evocraft.evocore.data.PlayerStatsManager;

import java.util.*;
import java.util.stream.Collectors;

public class TopManager {
    private static final int TOP_ENTRIES_PER_PAGE = 12;

    public static List<Map.Entry<String, Double>> getTopPage(String category, int page) {
        int safePage = Math.max(1, page);
        String cat = category == null ? "" : category.toLowerCase(Locale.ROOT);
        if (cat.equals("money")) cat = "bani";
        else if (cat.equals("deaths")) cat = "decese";
        else if (cat.equals("hours")) cat = "ore";
        else if (cat.equals("claims")) cat = "claimuri";

        Map<String, Double> rawData;

        if (cat.equals("bani")) {
            rawData = new HashMap<>();
            Map<UUID, Double> balances = EconomyManager.get().getAllAccounts();
            if (balances != null) {
                balances.forEach((uuid, bal) -> {
                    String name = PlayerStatsManager.get().getNameByUUID(uuid);
                    if (name != null && !name.isEmpty() && !name.equals("Necunoscut") && !name.equals("Unknown")
                            && !name.startsWith("?") && !name.startsWith("§")
                            && bal != null && Double.isFinite(bal) && bal > 0) {
                        rawData.put(name, bal);
                    }
                });
            }
        }
        else if (cat.equals("kills") || cat.equals("decese") || cat.equals("ore") || cat.equals("claimuri")) {
            rawData = PlayerStatsManager.get().getTopData(cat);
        } else {
            rawData = new HashMap<>();
        }

        return rawData.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .skip((long) (safePage - 1) * TOP_ENTRIES_PER_PAGE)
                .limit(TOP_ENTRIES_PER_PAGE)
                .collect(Collectors.toList());
    }
}

