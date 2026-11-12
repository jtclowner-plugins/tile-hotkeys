package com.tilehotkeys;

import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.List;
import net.runelite.client.config.Keybind;

final class TileHotkeyGroup
{
    enum Mode
    {
        TOGGLE("Toggle group"),
        CYCLE("Cycle through"),
        SHOW("Show while held"),
        HIDE("Hide while held");

        private final String label;

        Mode(String label)
        {
            this.label = label;
        }

        boolean isHoldMode()
        {
            return this == SHOW || this == HIDE;
        }

        @Override
        public String toString()
        {
            return label;
        }
    }

    String name;
    int keyCode;
    int modifiers;
    @SerializedName(value = "activePackIndex", alternate = {"activeSet"})
    int activePackIndex = -1;
    private Mode mode = Mode.TOGGLE;
    boolean enabled;
    private transient volatile boolean hotkeyHeld;
    @SerializedName(value = "packs", alternate = {"sets"})
    List<TileHotkeyPack> packs = new ArrayList<>();

    TileHotkeyGroup() { }
    TileHotkeyGroup(String name) { this.name = name; }

    Keybind getHotkey()
    {
        return new Keybind(keyCode, modifiers);
    }

    Mode getMode()
    {
        return mode == null ? Mode.TOGGLE : mode;
    }

    void resetMode(Mode mode)
    {
        this.mode = mode;
        activePackIndex = -1;
        enabled = false;
        hotkeyHeld = false;
    }

    boolean isGroupVisible()
    {
        switch (getMode())
        {
            case SHOW:
                return hotkeyHeld;
            case HIDE:
                return !hotkeyHeld;
            case TOGGLE:
                return enabled;
            default:
                return false;
        }
    }

    boolean releaseHotkey()
    {
        if (!hotkeyHeld) return false;
        hotkeyHeld = false;
        return true;
    }

    void pressHotkey()
    {
        switch (getMode())
        {
            case CYCLE:
                if (!packs.isEmpty())
                {
                    activePackIndex = (activePackIndex + 1) % packs.size();
                }
                break;
            case TOGGLE:
                enabled = !enabled;
                break;
            case SHOW:
            case HIDE:
                hotkeyHeld = true;
                break;
        }
    }
}

