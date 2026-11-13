package com.tilehotkeys;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Stroke;
import java.util.Collection;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

final class TileHotkeysOverlay extends Overlay
{
    private static final Color TILE_FILL = new Color(0, 0, 0, 50);
    private static final Stroke TILE_BORDER = new BasicStroke(2);

    private final Client client;
    private final TileHotkeysPlugin plugin;

    @Inject
    TileHotkeysOverlay(Client client, TileHotkeysPlugin plugin)
    {
        this.client = client;
        this.plugin = plugin;
        setPosition(OverlayPosition.DYNAMIC);
        setPriority(PRIORITY_LOW);
        setLayer(OverlayLayer.ABOVE_SCENE);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        Collection<TileHotkeysPlugin.RenderTile> tiles = plugin.getActiveTiles();
        for (TileHotkeysPlugin.RenderTile tile : tiles)
        {
            WorldView view = tile.worldView;
            WorldPoint point = tile.point;
            if (point.getPlane() != view.getPlane()) continue;
            LocalPoint local = LocalPoint.fromWorld(view, point);
            if (local == null) continue;
            Color color = tile.color == null ? Color.YELLOW : tile.color;
            Polygon polygon = Perspective.getCanvasTilePoly(client, local);
            if (polygon != null)
            {
                OverlayUtil.renderPolygon(graphics, polygon, color, TILE_FILL, TILE_BORDER);
            }
            if (tile.label != null && !tile.label.isBlank())
            {
                Point textLocation = Perspective.getCanvasTextLocation(client, graphics, local, tile.label, 0);
                if (textLocation != null)
                {
                    OverlayUtil.renderTextLocation(graphics, textLocation, tile.label, color);
                }
            }
        }
        return null;
    }
}
