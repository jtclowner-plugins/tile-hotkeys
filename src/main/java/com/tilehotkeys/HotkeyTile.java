package com.tilehotkeys;

import java.awt.Color;

/** Ground Marker compatible tile coordinate record. */
final class HotkeyTile
{
    int regionId;
    int regionX;
    int regionY;
    int z;
    Color color;
    String label;

    HotkeyTile() { }

    HotkeyTile(int regionId, int regionX, int regionY, int z, Color color, String label)
    {
        this.regionId = regionId;
        this.regionX = regionX;
        this.regionY = regionY;
        this.z = z;
        this.color = color;
        this.label = label;
    }

    @Override
    public boolean equals(Object other)
    {
        if (!(other instanceof HotkeyTile)) return false;
        HotkeyTile tile = (HotkeyTile) other;
        return regionId == tile.regionId && regionX == tile.regionX && regionY == tile.regionY && z == tile.z;
    }

    @Override
    public int hashCode()
    {
        int result = regionId;
        result = 31 * result + regionX;
        result = 31 * result + regionY;
        return 31 * result + z;
    }
}
