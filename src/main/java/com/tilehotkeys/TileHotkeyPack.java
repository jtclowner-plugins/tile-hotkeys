package com.tilehotkeys;

import java.util.ArrayList;
import java.util.List;

final class TileHotkeyPack
{
    String name;
    List<HotkeyTile> tiles = new ArrayList<>();

    TileHotkeyPack() { }
    TileHotkeyPack(String name) { this.name = name; }

    boolean isSpacer()
    {
        return tiles.isEmpty();
    }
}

