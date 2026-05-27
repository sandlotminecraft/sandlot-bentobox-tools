package com.sandlotminecraft.sbt.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.sandlotminecraft.sbt.util.IslandResolver.ResolverException;

import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.bentobox.managers.IslandsManager;

/**
 * Covers the seven T-04 acceptance criteria for {@link IslandResolver#resolve}.
 *
 * <p>Each test maps 1:1 to an AC in feature.md §7 T-04. The AC #7 test (player unknown OR
 * known-with-no-island) covers both sub-cases in a single method to honor the spec's
 * "7 unit tests, one per AC" count while keeping line coverage on the player-name branch high.
 */
class IslandResolverTest {

    // Same Bukkit-stub workaround as the other test classes: BentoBox Util static-init
    // calls Bukkit.getMinecraftVersion(). Reflective field-set bypasses Paper's
    // ServerBuildInfo lookup that breaks Bukkit.setServer in test JVMs.
    static {
        try {
            if (Bukkit.getServer() == null) {
                Server server = mock(Server.class);
                when(server.getBukkitVersion()).thenReturn("1.21.1-R0.1-SNAPSHOT");
                when(server.getMinecraftVersion()).thenReturn("1.21.1");
                java.lang.reflect.Field serverField = Bukkit.class.getDeclaredField("server");
                serverField.setAccessible(true);
                serverField.set(null, server);
            }
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private IslandsManager mgr;
    private World world;
    private Island af19Island;
    private final String af19Uuid = "af19e1ea-adae-453b-85f5-4b8866d11d77";
    private final String af19FullId = "BSkyBlock" + af19Uuid;

    @BeforeEach
    void setup() {
        mgr = mock(IslandsManager.class);
        world = mock(World.class);
        when(world.getName()).thenReturn("skyblock");
        af19Island = mock(Island.class);
        when(af19Island.getUniqueId()).thenReturn(af19FullId);
    }

    @Test
    void resolve_returnsIsland_whenFullPrefixedUniqueIdMatches() throws ResolverException {
        when(mgr.getIslandById(af19FullId)).thenReturn(Optional.of(af19Island));

        Island result = IslandResolver.resolve(mgr, world, af19FullId);

        assertSame(af19Island, result);
    }

    @Test
    void resolve_throws_whenFullPrefixedUniqueIdMisses() {
        String missingFullId = "BSkyBlockcafebabe-cafe-cafe-cafe-cafecafecafe";
        when(mgr.getIslandById(missingFullId)).thenReturn(Optional.empty());

        ResolverException ex = assertThrows(ResolverException.class,
                () -> IslandResolver.resolve(mgr, world, missingFullId));
        assertEquals("no island with uniqueId " + missingFullId, ex.getMessage());
    }

    @Test
    void resolve_returnsIsland_whenRawUuidSuffixMatches() throws ResolverException {
        when(mgr.getIslands()).thenReturn(Collections.singletonList(af19Island));

        Island result = IslandResolver.resolve(mgr, world, af19Uuid);

        assertSame(af19Island, result);
    }

    @Test
    void resolve_returnsIsland_when8CharPrefixMatchesExactlyOneUuidPortion() throws ResolverException {
        Island otherIsland = mock(Island.class);
        when(otherIsland.getUniqueId()).thenReturn("BSkyBlockff00ff00-ff00-ff00-ff00-ff00ff00ff00");
        when(mgr.getIslands()).thenReturn(List.of(af19Island, otherIsland));

        Island result = IslandResolver.resolve(mgr, world, "af19e1ea");

        assertSame(af19Island, result);
    }

    @Test
    void resolve_throws_when8CharPrefixMatchesMultipleIslands() {
        Island colliderA = mock(Island.class);
        when(colliderA.getUniqueId()).thenReturn("BSkyBlockabcd1234-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        Island colliderB = mock(Island.class);
        when(colliderB.getUniqueId()).thenReturn("BSkyBlockabcd1234-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        when(mgr.getIslands()).thenReturn(List.of(colliderA, colliderB));

        ResolverException ex = assertThrows(ResolverException.class,
                () -> IslandResolver.resolve(mgr, world, "abcd1234"));
        assertEquals("ambiguous prefix abcd1234 — matched 2 islands: abcd1234, abcd1234", ex.getMessage());
    }

    @Test
    void resolve_returnsIsland_whenPlayerNameHasPrimaryIsland() throws ResolverException {
        UUID playerUuid = UUID.randomUUID();
        OfflinePlayer player = mock(OfflinePlayer.class);
        when(player.hasPlayedBefore()).thenReturn(true);
        when(player.getUniqueId()).thenReturn(playerUuid);
        when(mgr.getIslands(world, playerUuid)).thenReturn(List.of(af19Island));

        try (MockedStatic<Bukkit> bukkitMock = mockStatic(Bukkit.class)) {
            bukkitMock.when(() -> Bukkit.getOfflinePlayer("Ice_Dragon_Ice")).thenReturn(player);

            Island result = IslandResolver.resolve(mgr, world, "Ice_Dragon_Ice");

            assertSame(af19Island, result);
        }
    }

    /* --- Coverage-completion tests for the reachable error branches not enumerated as ACs.
     *     Spec requires ≥95% line coverage; these reach the no-match / ambiguous-suffix paths
     *     in Case 2 and the no-match path in Case 3 that the AC tests don't exercise. --- */

    @Test
    void resolve_throws_whenRawUuidMatchesNoIsland() {
        when(mgr.getIslands()).thenReturn(Collections.emptyList());

        ResolverException ex = assertThrows(ResolverException.class,
                () -> IslandResolver.resolve(mgr, world, af19Uuid));
        assertEquals("no island with UUID " + af19Uuid, ex.getMessage());
    }

    @Test
    void resolve_throws_whenRawUuidMatchesMultipleIslandsAcrossGamemodes() {
        Island bskyVariant = mock(Island.class);
        when(bskyVariant.getUniqueId()).thenReturn("BSkyBlock" + af19Uuid);
        Island acidVariant = mock(Island.class);
        when(acidVariant.getUniqueId()).thenReturn("AcidIsland" + af19Uuid);
        when(mgr.getIslands()).thenReturn(List.of(bskyVariant, acidVariant));

        ResolverException ex = assertThrows(ResolverException.class,
                () -> IslandResolver.resolve(mgr, world, af19Uuid));
        assertEquals(
                "ambiguous UUID " + af19Uuid + " — matched 2 islands across gamemodes: af19e1ea, af19e1ea",
                ex.getMessage());
    }

    @Test
    void resolve_throws_when8CharPrefixMatchesNoIsland() {
        when(mgr.getIslands()).thenReturn(Collections.emptyList());

        ResolverException ex = assertThrows(ResolverException.class,
                () -> IslandResolver.resolve(mgr, world, "deadbeef"));
        assertEquals("no island with id-prefix deadbeef", ex.getMessage());
    }

    @Test
    void resolve_throws_whenPlayerIsUnknownOrHasNoIsland() {
        // Sub-case A: never-seen player.
        OfflinePlayer ghost = mock(OfflinePlayer.class);
        when(ghost.hasPlayedBefore()).thenReturn(false);
        when(ghost.isOnline()).thenReturn(false);

        // Sub-case B: known player with no island in this world.
        UUID knownUuid = UUID.randomUUID();
        OfflinePlayer landless = mock(OfflinePlayer.class);
        when(landless.hasPlayedBefore()).thenReturn(true);
        when(landless.getUniqueId()).thenReturn(knownUuid);
        when(mgr.getIslands(world, knownUuid)).thenReturn(Collections.emptyList());

        try (MockedStatic<Bukkit> bukkitMock = mockStatic(Bukkit.class)) {
            bukkitMock.when(() -> Bukkit.getOfflinePlayer("Nobody")).thenReturn(ghost);
            bukkitMock.when(() -> Bukkit.getOfflinePlayer("Landless")).thenReturn(landless);

            ResolverException unknownEx = assertThrows(ResolverException.class,
                    () -> IslandResolver.resolve(mgr, world, "Nobody"));
            assertEquals("no such player Nobody", unknownEx.getMessage());

            ResolverException landlessEx = assertThrows(ResolverException.class,
                    () -> IslandResolver.resolve(mgr, world, "Landless"));
            assertEquals("player Landless has no island in world skyblock", landlessEx.getMessage());
        }
    }
}
