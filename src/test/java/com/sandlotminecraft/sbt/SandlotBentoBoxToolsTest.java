package com.sandlotminecraft.sbt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import world.bentobox.bentobox.BentoBox;
import world.bentobox.bentobox.api.addons.Addon;
import world.bentobox.bentobox.api.addons.Addon.State;
import world.bentobox.bentobox.api.addons.GameModeAddon;
import world.bentobox.bentobox.api.commands.CompositeCommand;
import world.bentobox.bentobox.managers.AddonsManager;

/**
 * Verifies the three onEnable paths declared in feature spec T-02 acceptance criteria:
 * happy path (BSkyBlock present + admin command present), BSkyBlock-absent, and
 * admin-command-absent. We capture the [sbt] log lines via the Addon#log overload that
 * delegates to java.util.logging.Logger, mocking that logger to record emitted messages.
 */
class SandlotBentoBoxToolsTest {

    // BentoBox's Util class static-initializes Bukkit.getMinecraftVersion(); installing a
    // minimal Server mock before any Addon class loads keeps the class init path off NPE.
    // Bukkit.setServer is bypassed via reflection because Paper's overload pulls in
    // ServerBuildInfo (jar-manifest lookup) that isn't satisfied in test context.
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

    private SandlotBentoBoxTools addon;
    private BentoBox plugin;
    private AddonsManager addonsManager;
    private List<String> emittedLogLines;

    @BeforeEach
    void setup() {
        plugin = mock(BentoBox.class);
        addonsManager = mock(AddonsManager.class);
        when(plugin.getAddonsManager()).thenReturn(addonsManager);

        emittedLogLines = new ArrayList<>();

        addon = Mockito.spy(new SandlotBentoBoxTools());
        // Short-circuit Addon's plugin accessor and logging helpers without booting BentoBox.
        doAnswer(inv -> plugin).when(addon).getPlugin();
        doAnswer(inv -> {
            emittedLogLines.add("INFO " + inv.getArgument(0, String.class));
            return null;
        }).when(addon).log(anyString());
        doAnswer(inv -> {
            emittedLogLines.add("ERROR " + inv.getArgument(0, String.class));
            return null;
        }).when(addon).logError(anyString());
        doNothing().when(addon).setState(any(Addon.State.class));
    }

    @Test
    void onEnable_logsOk_whenBskyblockAndAdminCommandPresent() {
        GameModeAddon bsb = mock(GameModeAddon.class);
        CompositeCommand admin = mock(CompositeCommand.class);
        when(addonsManager.getAddonByName("BSkyBlock")).thenReturn(Optional.of(bsb));
        when(bsb.getAdminCommand()).thenReturn(Optional.of(admin));

        addon.onEnable();

        assertEquals(List.of("INFO [sbt] op=load result=ok"), emittedLogLines);
        verify(addon, times(0)).setState(Addon.State.DISABLED);
        assertEquals(bsb, addon.getBsbAddon());
        assertEquals(admin, addon.getBsbAdminCommand());
    }

    @Test
    void onEnable_disables_whenBskyblockAbsent() {
        when(addonsManager.getAddonByName("BSkyBlock")).thenReturn(Optional.empty());

        addon.onEnable();

        assertEquals(
                List.of(
                        "ERROR BSkyBlock GameModeAddon not present; sandlot-bentobox-tools is inert.",
                        "INFO [sbt] op=load result=fail reason=bskyblock-absent"),
                emittedLogLines);
        verify(addon).setState(Addon.State.DISABLED);
    }

    @Test
    void onEnable_disables_whenAdminCommandAbsent() {
        GameModeAddon bsb = mock(GameModeAddon.class);
        when(addonsManager.getAddonByName("BSkyBlock")).thenReturn(Optional.of(bsb));
        when(bsb.getAdminCommand()).thenReturn(Optional.empty());

        addon.onEnable();

        assertEquals(
                List.of(
                        "ERROR BSkyBlock admin command not registered; sandlot-bentobox-tools is inert.",
                        "INFO [sbt] op=load result=fail reason=admin-command-absent"),
                emittedLogLines);
        verify(addon).setState(Addon.State.DISABLED);
    }
}
