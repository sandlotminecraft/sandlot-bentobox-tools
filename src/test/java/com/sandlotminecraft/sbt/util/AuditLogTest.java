package com.sandlotminecraft.sbt.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import world.bentobox.bentobox.api.addons.Addon;

/**
 * Covers the four T-05 acceptance criteria for {@link AuditLog}.
 */
class AuditLogTest {

    // Same Bukkit-stub workaround as the other test classes — BentoBox Addon transitively
    // loads Util whose static-init calls Bukkit.getMinecraftVersion().
    static {
        try {
            if (Bukkit.getServer() == null) {
                Server server = Mockito.mock(Server.class);
                Mockito.when(server.getBukkitVersion()).thenReturn("1.21.1-R0.1-SNAPSHOT");
                Mockito.when(server.getMinecraftVersion()).thenReturn("1.21.1");
                java.lang.reflect.Field serverField = Bukkit.class.getDeclaredField("server");
                serverField.setAccessible(true);
                serverField.set(null, server);
            }
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private Addon addon;
    private final UUID uuid = UUID.fromString("af19e1ea-adae-453b-85f5-4b8866d11d77");

    @BeforeEach
    void setup() {
        addon = mock(Addon.class);
        AuditLog.init(addon);
    }

    @Test
    void op_emitsBaseLine_whenNoExtraKv() {
        AuditLog.op("undelete", uuid);

        verify(addon).log("[sbt] op=undelete target=" + uuid);
    }

    @Test
    void op_emitsKvPairsInOrder() {
        // AC #1: AuditLog.op("undelete", uuid, "deletable", "true", "lock", "0")
        //     →  [sbt] op=undelete target=<uuid> deletable=true lock=0
        AuditLog.op("undelete", uuid, "deletable", "true", "lock", "0");

        verify(addon).log("[sbt] op=undelete target=" + uuid + " deletable=true lock=0");
    }

    @Test
    void op_quotesValueContainingSpace() {
        // AC #2: a value containing a space is wrapped in double quotes.
        AuditLog.op("repair", uuid, "name", "Ice Dragon");

        verify(addon).log("[sbt] op=repair target=" + uuid + " name=\"Ice Dragon\"");
    }

    @Test
    void result_putsOutcomeBeforeTarget() {
        // AC #3: result line begins  [sbt] op=<verb> result=<outcome> target=<uuid> ...
        AuditLog.result("regen", uuid, "refused", "reason", "nether-disabled");

        verify(addon).log("[sbt] op=regen result=refused target=" + uuid + " reason=nether-disabled");
    }

    /* --- Defensive-input coverage: not in the spec's "4 tests" mandate but cheap to add for
     *     the IllegalArgumentException branch in formatLine. Lifts the formatLine branch
     *     coverage to 100%. --- */

    @Test
    void op_throws_whenKvPairsIsOddLength() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> AuditLog.op("undelete", uuid, "deletable"));
        assertEquals("kvPairs must be even-length, got 1", ex.getMessage());
    }

    @Test
    void op_rendersNullValueAsLiteralNull() {
        AuditLog.op("undelete", uuid, "previousOwner", null);

        verify(addon).log("[sbt] op=undelete target=" + uuid + " previousOwner=null");
    }

    @Test
    void op_escapesEmbeddedDoubleQuotes() {
        // §5.1: "Values containing spaces are quoted with double-quotes; embedded quotes are escaped."
        AuditLog.op("raw", uuid, "fragment", "say \"hi\"");

        verify(addon).log("[sbt] op=raw target=" + uuid + " fragment=\"say \\\"hi\\\"\"");
    }

    @Test
    void op_throwsIllegalState_whenInitNotCalled() {
        AuditLog.init(null);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> AuditLog.op("undelete", uuid));
        assertEquals("AuditLog.init(addon) must be called before any emit", ex.getMessage());
    }
}
