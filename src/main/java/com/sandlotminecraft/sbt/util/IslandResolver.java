package com.sandlotminecraft.sbt.util;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;

import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.bentobox.managers.IslandsManager;

/**
 * Resolves an operator-supplied target string to an {@link Island}.
 *
 * <p>BentoBox stores each Island.uniqueId as {@code <gamemode-name> + <36-char-UUID>}
 * (verified at BentoBox 3.16.2 IslandsManager.java#L269). The resolver accepts three id
 * forms — the full prefixed uniqueId, the raw 36-char UUID portion alone, or an 8-char
 * UUID-portion prefix — plus a player-name fallthrough. See feature.md §5.1 for the
 * full dispatch contract.
 */
public final class IslandResolver {

    private static final int UUID_LENGTH = 36;
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final Pattern PREFIX_PATTERN = Pattern.compile("[0-9a-fA-F]{8}");

    private IslandResolver() {
        // utility class
    }

    public static Island resolve(IslandsManager mgr, World world, String arg) throws ResolverException {
        // Case 1: full prefixed uniqueId (operator pasted Island.getUniqueId() literally).
        if (arg.length() > UUID_LENGTH && isUuidPattern(arg.substring(arg.length() - UUID_LENGTH))) {
            Optional<Island> island = mgr.getIslandById(arg);
            if (island.isPresent()) {
                return island.get();
            }
            throw new ResolverException("no island with uniqueId " + arg);
        }

        // Case 2: raw 36-char UUID — scan for the island whose uniqueId.endsWith(arg).
        if (arg.length() == UUID_LENGTH && isUuidPattern(arg)) {
            List<Island> matches = mgr.getIslands().stream()
                    .filter(i -> i.getUniqueId().endsWith(arg))
                    .collect(Collectors.toList());
            if (matches.isEmpty()) {
                throw new ResolverException("no island with UUID " + arg);
            }
            if (matches.size() > 1) {
                throw new ResolverException("ambiguous UUID " + arg + " — matched " + matches.size()
                        + " islands across gamemodes: " + shortList(matches));
            }
            return matches.get(0);
        }

        // Case 3: 8-char hex prefix scan over each island's UUID portion.
        if (arg.length() == 8 && PREFIX_PATTERN.matcher(arg).matches()) {
            String prefixLc = arg.toLowerCase();
            List<Island> matches = mgr.getIslands().stream()
                    .filter(i -> uuidPortion(i.getUniqueId()).startsWith(prefixLc))
                    .collect(Collectors.toList());
            if (matches.isEmpty()) {
                throw new ResolverException("no island with id-prefix " + arg);
            }
            if (matches.size() > 1) {
                throw new ResolverException("ambiguous prefix " + arg + " — matched " + matches.size()
                        + " islands: " + shortList(matches));
            }
            return matches.get(0);
        }

        // Case 4: player name.
        OfflinePlayer player = Bukkit.getOfflinePlayer(arg);
        if (!player.hasPlayedBefore() && !player.isOnline()) {
            throw new ResolverException("no such player " + arg);
        }
        List<Island> islands = mgr.getIslands(world, player.getUniqueId());
        if (islands.isEmpty()) {
            throw new ResolverException("player " + arg + " has no island in world " + world.getName());
        }
        return islands.get(0);
    }

    private static boolean isUuidPattern(String s) {
        return UUID_PATTERN.matcher(s).matches();
    }

    private static String uuidPortion(String uniqueId) {
        // BentoBox guarantees uniqueId is "<gamemode-name>" + UUID.randomUUID().toString(),
        // so the trailing 36 chars are always the UUID. Trust the API; no defensive guards.
        return uniqueId.substring(uniqueId.length() - UUID_LENGTH).toLowerCase();
    }

    private static String shortList(List<Island> islands) {
        return islands.stream()
                .map(i -> uuidPortion(i.getUniqueId()).substring(0, 8))
                .limit(5)
                .collect(Collectors.joining(", "));
    }

    public static class ResolverException extends Exception {
        private static final long serialVersionUID = 1L;

        public ResolverException(String message) {
            super(message);
        }
    }
}
