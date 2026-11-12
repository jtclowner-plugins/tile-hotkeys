package com.tilehotkeys;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(TileHotkeysPlugin.CONFIG_GROUP)
public interface TileHotkeysConfig extends Config
{
    String HIDE_SIDEBAR = "hideSidebar";

    @ConfigItem(
        keyName = HIDE_SIDEBAR,
        name = "Hide sidebar button",
        description = "Hide the Tile Hotkeys sidebar button while keeping hotkeys and tile markers active."
    )
    default boolean hideSidebar()
    {
        return false;
    }
}
