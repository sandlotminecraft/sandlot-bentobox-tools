package com.sandlotminecraft.sbt.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sandlotminecraft.sbt.util.AuditLog;

import world.bentobox.bentobox.api.addons.Addon;
import world.bentobox.bentobox.api.commands.CompositeCommand;

/**
 * Covers the two T-03 acceptance criteria at the label-choice + fallback-log layer:
 *   AC #1: no pre-registered "island" subcommand on the parent  → label "island", no fallback log.
 *   AC #2: synthetic pre-registered "island" subcommand         → label "sbt",    fallback log emitted.
 *
 * In-server help-text verification ("/bsbadmin island help" / "/bsbadmin sbt help")
 * is deferred to FCP-1 per the feature-spec DoD escape valve.
 */
class IslandGroupCommandTest {

    // BentoBox classes static-init Bukkit.getMinecraftVersion(); install a Server mock
    // before any Addon class loads. Same workaround as SandlotBentoBoxToolsTest.
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

    private Addon addon;
    private CompositeCommand parent;

    @BeforeEach
    void setup() {
        addon = mock(Addon.class);
        doAnswer(inv -> null).when(addon).log(anyString());
        parent = mock(CompositeCommand.class);
        // chooseLabel now emits via AuditLog; init it to the same mock addon so the existing
        // verify(addon).log(...) assertions still fire through the static AuditLog.logger.
        AuditLog.init(addon);
    }

    @Test
    void chooseLabel_returnsIsland_andDoesNotLog_whenNoCollision() {
        when(parent.getSubCommand("island")).thenReturn(Optional.empty());

        String label = IslandGroupCommand.chooseLabel(addon, parent);

        assertEquals("island", label);
        verify(addon, never()).log(anyString());
    }

    @Test
    void chooseLabel_returnsSbt_andLogsFallback_whenCollision() {
        CompositeCommand collidingChild = mock(CompositeCommand.class);
        when(parent.getSubCommand("island")).thenReturn(Optional.of(collidingChild));

        String label = IslandGroupCommand.chooseLabel(addon, parent);

        assertEquals("sbt", label);
        verify(addon).log("[sbt] op=load result=ok label=sbt reason=island-label-claimed");
    }
}
