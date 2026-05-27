package com.sandlotminecraft.sbt;

import java.util.Optional;

import world.bentobox.bentobox.api.addons.Addon;
import world.bentobox.bentobox.api.addons.GameModeAddon;
import world.bentobox.bentobox.api.commands.CompositeCommand;

public class SandlotBentoBoxTools extends Addon {

    private GameModeAddon bsbAddon;
    private CompositeCommand bsbAdminCommand;

    @Override
    public void onEnable() {
        Optional<Addon> opt = getPlugin().getAddonsManager().getAddonByName("BSkyBlock");
        if (opt.isEmpty() || !(opt.get() instanceof GameModeAddon gma)) {
            logError("BSkyBlock GameModeAddon not present; sandlot-bentobox-tools is inert.");
            log("[sbt] op=load result=fail reason=bskyblock-absent");
            setState(State.DISABLED);
            return;
        }
        bsbAddon = gma;
        Optional<CompositeCommand> adminCmd = gma.getAdminCommand();
        if (adminCmd.isEmpty()) {
            logError("BSkyBlock admin command not registered; sandlot-bentobox-tools is inert.");
            log("[sbt] op=load result=fail reason=admin-command-absent");
            setState(State.DISABLED);
            return;
        }
        bsbAdminCommand = adminCmd.get();
        // T-03: new IslandGroupCommand(this, bsbAdminCommand) self-registers here.
        log("[sbt] op=load result=ok");
    }

    @Override
    public void onDisable() {
        // No resources to release in v1.
    }

    public GameModeAddon getBsbAddon() {
        return bsbAddon;
    }

    public CompositeCommand getBsbAdminCommand() {
        return bsbAdminCommand;
    }
}
