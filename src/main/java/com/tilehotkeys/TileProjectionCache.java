package com.tilehotkeys;

import com.tilehotkeys.TileHotkeysPlugin.RenderTile;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import lombok.Value;
import net.runelite.api.Scene;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;

final class TileProjectionCache
{
    private final Map<WorldView, ViewProjection> views = new IdentityHashMap<>();

    synchronized List<RenderTile> project(WorldView view, List<Marker> markers)
    {
        Scene scene = view.getScene();
        int baseX = view.getBaseX();
        int baseY = view.getBaseY();
        boolean instance = view.isInstance();
        ViewProjection projection = views.get(view);
        if (projection == null || !projection.matches(scene, baseX, baseY, instance))
        {
            projection = new ViewProjection(scene, baseX, baseY, instance);
            views.put(view, projection);
        }
        return projection.project(view, markers);
    }

    synchronized void invalidate(WorldView view)
    {
        views.remove(view);
    }

    synchronized void clear()
    {
        views.clear();
    }

    @Value
    static class Marker
    {
        WorldPoint point;
        Color color;
        String label;
    }

    private static final class ViewProjection
    {
        private final Scene scene;
        private final int baseX;
        private final int baseY;
        private final boolean instance;
        // Include empty projections so markers outside this scene are scanned only once.
        private final Map<WorldPoint, Collection<WorldPoint>> points = new HashMap<>();
        private List<Marker> previousMarkers = List.of();
        private List<RenderTile> previousTiles = List.of();

        private ViewProjection(Scene scene, int baseX, int baseY, boolean instance)
        {
            this.scene = scene;
            this.baseX = baseX;
            this.baseY = baseY;
            this.instance = instance;
        }

        private boolean matches(Scene scene, int baseX, int baseY, boolean instance)
        {
            return this.scene == scene && this.baseX == baseX && this.baseY == baseY && this.instance == instance;
        }

        private List<RenderTile> project(WorldView view, List<Marker> markers)
        {
            if (markers != previousMarkers)
            {
                List<RenderTile> tiles = new ArrayList<>();
                for (Marker marker : markers)
                {
                    Collection<WorldPoint> projected = points.computeIfAbsent(marker.point,
                        point -> WorldPoint.toLocalInstance(view, point));
                    for (WorldPoint point : projected)
                    {
                        tiles.add(new RenderTile(view, point, marker.color, marker.label));
                    }
                }
                previousTiles = List.copyOf(tiles);
                previousMarkers = markers;
            }
            return previousTiles;
        }
    }
}
