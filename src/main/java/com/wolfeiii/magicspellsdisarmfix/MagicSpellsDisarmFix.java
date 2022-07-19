package com.wolfeiii.magicspellsdisarmfix;


import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public final class MagicSpellsDisarmFix extends JavaPlugin {

    private static MagicSpellsDisarmFix core;

    @Override
    public void onEnable() {
        // Plugin startup logic
        core = this;
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }

    public static MagicSpellsDisarmFix getCore() {
        return core;
    }
}
