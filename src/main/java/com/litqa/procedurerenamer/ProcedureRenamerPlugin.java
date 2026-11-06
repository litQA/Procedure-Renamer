package com.litqa.procedurerenamer;

import com.litqa.procedurerenamer.element.PluginElementTypes;
import com.litqa.procedurerenamer.util.ConfigSync;
import net.mcreator.plugin.JavaPlugin;
import net.mcreator.plugin.Plugin;
import net.mcreator.plugin.events.PreGeneratorsLoadingEvent;
import net.mcreator.plugin.events.workspace.MCreatorLoadedEvent;
import net.mcreator.plugin.events.workspace.WorkspaceBuildStartedEvent;
import net.mcreator.plugin.events.workspace.WorkspaceSavedEvent;

/**
 * Java plugin entry point for Procedure Renamer.
 */
public class ProcedureRenamerPlugin extends JavaPlugin {

    public ProcedureRenamerPlugin(Plugin plugin) {
        super(plugin);
        ConfigSync.setPluginJar(plugin.getFile());

        addListener(PreGeneratorsLoadingEvent.class, event -> PluginElementTypes.load());
        addListener(MCreatorLoadedEvent.class,
                event -> ConfigSync.sync(event.getMCreator().getWorkspace()));
        addListener(WorkspaceSavedEvent.AfterSaving.class,
                event -> ConfigSync.sync(event.getWorkspace()));
        addListener(WorkspaceBuildStartedEvent.class,
                event -> ConfigSync.sync(event.getMCreator().getWorkspace()));

        System.out.println("[Procedure Renamer] Plugin loaded");
    }
}
