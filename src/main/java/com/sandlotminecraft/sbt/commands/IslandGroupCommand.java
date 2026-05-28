package com.sandlotminecraft.sbt.commands;

import java.util.List;

import com.sandlotminecraft.sbt.util.AuditLog;

import world.bentobox.bentobox.api.addons.Addon;
import world.bentobox.bentobox.api.commands.CompositeCommand;
import world.bentobox.bentobox.api.user.User;

/**
 * Top-level group command registered under BSkyBlock's bsbadmin parent.
 * <p>
 * Per architecture.md §5.4: if the parent already has a subcommand named
 * "island" (upstream label collision), this command registers itself as "sbt"
 * and nests an {@link IslandSubgroupCommand} under it labeled "island". The
 * locale keys remain {@code sbt.island.*} either way.
 */
public class IslandGroupCommand extends CompositeCommand {

    public IslandGroupCommand(Addon addon, CompositeCommand parent) {
        super(addon, parent, chooseLabel(addon, parent));
        if ("sbt".equals(this.getLabel())) {
            new IslandSubgroupCommand(addon, this);
        }
    }

    /**
     * Returns "island" when free, or "sbt" when the parent already owns the "island" label.
     * In the fallback case, emits the {@code label=sbt reason=island-label-claimed} log line
     * as a side effect — kept here (rather than in the constructor body) because Java 17
     * disallows statements before {@code super(...)}.
     */
    static String chooseLabel(Addon addon, CompositeCommand parent) {
        if (parent.getSubCommand("island").isPresent()) {
            AuditLog.result("load", null, "ok", "label", "sbt", "reason", "island-label-claimed");
            return "sbt";
        }
        return "island";
    }

    @Override
    public void setup() {
        setOnlyPlayer(false);
        setPermission("bskyblock.admin");
        setDescription("sbt.island.description");
        setParametersHelp("sbt.island.parameters");
    }

    @Override
    public boolean execute(User user, String label, List<String> args) {
        showHelp(this, user);
        return true;
    }

    /**
     * Nested fallback group: registers as "island" under an IslandGroupCommand
     * that itself labeled "sbt". Only constructed when label collision fires.
     */
    public static class IslandSubgroupCommand extends CompositeCommand {

        public IslandSubgroupCommand(Addon addon, IslandGroupCommand parent) {
            super(addon, parent, "island");
        }

        @Override
        public void setup() {
            setOnlyPlayer(false);
            setPermission("bskyblock.admin");
            setDescription("sbt.island.description");
            setParametersHelp("sbt.island.parameters");
        }

        @Override
        public boolean execute(User user, String label, List<String> args) {
            showHelp(this, user);
            return true;
        }
    }
}
