package com.tilehotkeys;

import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Provides;
import com.tilehotkeys.TileProjectionCache.Marker;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.WorldEntity;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.WorldViewLoaded;
import net.runelite.api.events.WorldViewUnloaded;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.Keybind;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.ProfileChanged;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;

@Slf4j
@PluginDescriptor(
    name = "Tile Hotkeys",
    description = "Group imported ground markers into tile packs and switch them with configurable hotkeys.",
    tags = {"tile", "ground marker", "tile pack", "hotkey", "overlay"}
)
public class TileHotkeysPlugin extends Plugin implements KeyListener
{
    static final String CONFIG_GROUP = "tileHotkeys";
    private static final String GROUPS_KEY = "groups";
    private static final Type GROUP_LIST_TYPE = new TypeToken<List<TileHotkeyGroup>>() { }.getType();
    private static final Type TILE_LIST_TYPE = new TypeToken<List<HotkeyTile>>() { }.getType();

    @Inject private Client client;
    @Inject private ConfigManager configManager;
    @Inject private TileHotkeysConfig config;
    @Inject private Gson gson;
    @Inject private KeyManager keyManager;
    @Inject private ClientToolbar clientToolbar;
    @Inject private OverlayManager overlayManager;
    @Inject private TileHotkeysOverlay overlay;

    private final List<TileHotkeyGroup> groups = new ArrayList<>();
    private final TileProjectionCache projections = new TileProjectionCache();
    private volatile List<Marker> activeMarkers = List.of();
    private final Set<Integer> pressedKeys = new HashSet<>();
    private volatile TileHotkeysPanel panel;
    private volatile NavigationButton navigationButton;

    @Provides
    TileHotkeysConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(TileHotkeysConfig.class);
    }

    @Override
    protected synchronized void startUp()
    {
        loadGroups();
        panel = new TileHotkeysPanel(this);
        BufferedImage icon = ImageUtil.loadImageResource(getClass(), "icon.png");
        navigationButton = NavigationButton.builder()
            .tooltip("Tile Hotkeys")
            .icon(icon)
            .priority(6)
            .panel(panel)
            .build();
        updateSidebarButton();
        overlayManager.add(overlay);
        keyManager.registerKeyListener(this);
    }

    @Override
    protected synchronized void shutDown()
    {
        keyManager.unregisterKeyListener(this);
        overlayManager.remove(overlay);
        if (navigationButton != null) clientToolbar.removeNavigation(navigationButton);
        groups.clear();
        activeMarkers = List.of();
        projections.clear();
        panel = null;
        navigationButton = null;
        pressedKeys.clear();
    }

    private synchronized void loadGroups()
    {
        groups.clear();
        String json = configManager.getConfiguration(CONFIG_GROUP, GROUPS_KEY);
        if (!Strings.isNullOrEmpty(json))
        {
            try
            {
                List<TileHotkeyGroup> saved = gson.fromJson(json, GROUP_LIST_TYPE);
                if (saved != null) groups.addAll(saved);
            }
            catch (JsonSyntaxException ex)
            {
                log.warn("Unable to load saved tile hotkey groups", ex);
            }
        }
        publishActiveMarkers();
    }

    void saveGroups()
    {
        if (groups.isEmpty()) configManager.unsetConfiguration(CONFIG_GROUP, GROUPS_KEY);
        else configManager.setConfiguration(CONFIG_GROUP, GROUPS_KEY, gson.toJson(groups));
        refreshPanel();
    }

    synchronized List<TileHotkeyGroup> getGroups() { return List.copyOf(groups); }

    synchronized TileHotkeyGroup addGroup(String name)
    {
        TileHotkeyGroup group = new TileHotkeyGroup(name);
        groups.add(group);
        saveGroups();
        return group;
    }

    synchronized boolean renameGroup(TileHotkeyGroup group, String name)
    {
        String trimmed = name.trim();
        if (!groups.contains(group) || trimmed.isEmpty() || trimmed.equals(group.name)) return false;
        group.name = trimmed;
        saveGroups();
        return true;
    }

    synchronized TileHotkeyPack addSpacer(TileHotkeyGroup group)
    {
        if (!groups.contains(group) || group.getMode() != TileHotkeyGroup.Mode.CYCLE) return null;
        TileHotkeyPack spacer = new TileHotkeyPack("Spacer");
        group.packs.add(spacer);
        publishActiveMarkers();
        saveGroups();
        return spacer;
    }

    synchronized void removeGroup(TileHotkeyGroup group)
    {
        if (groups.remove(group))
        {
            publishActiveMarkers();
            saveGroups();
        }
    }

    synchronized void setGroupMode(TileHotkeyGroup group, TileHotkeyGroup.Mode newMode)
    {
        if (!groups.contains(group) || group.getMode() == newMode) return;
        group.resetMode(newMode);
        publishActiveMarkers();
        saveGroups();
    }

    synchronized void setGroupHotkey(TileHotkeyGroup group, Keybind keybind)
    {
        if (!groups.contains(group)) return;
        if (group.keyCode != keybind.getKeyCode() || group.modifiers != keybind.getModifiers())
        {
            if (group.releaseHotkey()) publishActiveMarkers();
        }
        group.keyCode = keybind.getKeyCode();
        group.modifiers = keybind.getModifiers();
        saveGroups();
    }

    synchronized TileHotkeyPack importPackFromClipboard(TileHotkeyGroup group, String name)
    {
        if (!groups.contains(group)) return null;
        try
        {
            String clipboard = (String) Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
            List<HotkeyTile> imported = gson.fromJson(clipboard, TILE_LIST_TYPE);
            if (imported == null || imported.isEmpty()) return null;
            TileHotkeyPack pack = new TileHotkeyPack(name);
            for (HotkeyTile tile : imported)
            {
                if (tile != null && !pack.tiles.contains(tile)) pack.tiles.add(tile);
            }
            if (pack.tiles.isEmpty()) return null;
            group.packs.add(pack);
            publishActiveMarkers();
            saveGroups();
            return pack;
        }
        catch (IOException | UnsupportedFlavorException | JsonSyntaxException | ClassCastException ex)
        {
            log.debug("Unable to import tile pack from clipboard", ex);
            return null;
        }
    }

    synchronized TileHotkeyGroup importGroupFromClipboard()
    {
        try
        {
            String clipboard = (String) Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
            TileHotkeyGroup imported = gson.fromJson(clipboard, TileHotkeyGroup.class);
            if (imported == null || Strings.isNullOrEmpty(imported.name) || imported.packs == null) return null;
            imported.packs.removeIf(pack -> pack == null || pack.tiles == null);
            for (TileHotkeyPack pack : imported.packs) pack.tiles.removeIf(tile -> tile == null);
            imported.resetMode(imported.getMode());
            groups.add(imported);
            publishActiveMarkers();
            saveGroups();
            return imported;
        }
        catch (IOException | UnsupportedFlavorException | JsonSyntaxException | ClassCastException ex)
        {
            log.debug("Unable to import tile hotkey group from clipboard", ex);
            return null;
        }
    }

    synchronized int exportPack(TileHotkeyGroup group, TileHotkeyPack pack)
    {
        if (!groups.contains(group) || !group.packs.contains(pack) || pack.tiles.isEmpty()) return 0;
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(gson.toJson(pack.tiles)), null);
        return pack.tiles.size();
    }

    synchronized boolean exportGroup(TileHotkeyGroup group)
    {
        if (!groups.contains(group)) return false;
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(gson.toJson(group)), null);
        return true;
    }

    synchronized void removePack(TileHotkeyGroup group, TileHotkeyPack pack)
    {
        if (!groups.contains(group)) return;
        int index = group.packs.indexOf(pack);
        if (index < 0) return;
        group.packs.remove(index);
        if (group.activePackIndex == index) group.activePackIndex = -1;
        else if (group.activePackIndex > index) group.activePackIndex--;
        publishActiveMarkers();
        saveGroups();
    }

    synchronized boolean renamePack(TileHotkeyGroup group, TileHotkeyPack pack, String name)
    {
        if (!groups.contains(group) || !group.packs.contains(pack) || pack.isSpacer()) return false;
        String trimmed = name.trim();
        if (trimmed.isEmpty() || trimmed.equals(pack.name)) return false;
        pack.name = trimmed;
        saveGroups();
        return true;
    }

    synchronized void movePack(TileHotkeyGroup group, TileHotkeyPack pack, int toIndex)
    {
        if (!groups.contains(group)) return;
        int fromIndex = group.packs.indexOf(pack);
        if (fromIndex < 0 || toIndex < 0 || toIndex >= group.packs.size() || fromIndex == toIndex) return;

        group.packs.remove(fromIndex);
        group.packs.add(toIndex, pack);
        group.activePackIndex = adjustMovedIndex(group.activePackIndex, fromIndex, toIndex);
        publishActiveMarkers();
        saveGroups();
    }

    private static int adjustMovedIndex(int index, int fromIndex, int toIndex)
    {
        if (index == fromIndex) return toIndex;
        if (fromIndex < toIndex && index > fromIndex && index <= toIndex) return index - 1;
        if (toIndex < fromIndex && index >= toIndex && index < fromIndex) return index + 1;
        return index;
    }

    private void refreshPanel()
    {
        TileHotkeysPanel currentPanel = panel;
        if (currentPanel != null) SwingUtilities.invokeLater(currentPanel::refresh);
    }

    private void updateSidebarButton()
    {
        NavigationButton button = navigationButton;
        if (button == null) return;
        if (config.hideSidebar())
        {
            clientToolbar.removeNavigation(button);
        }
        else
        {
            clientToolbar.addNavigation(button);
        }
    }

    @Override
    public void keyTyped(KeyEvent event) { }

    @Override
    public synchronized void keyPressed(KeyEvent event)
    {
        int keyCode = event.getExtendedKeyCode();
        if (keyCode == KeyEvent.VK_UNDEFINED || !pressedKeys.add(keyCode))
        {
            return;
        }

        boolean matched = false;
        boolean save = false;
        for (TileHotkeyGroup group : groups)
        {
            if (!group.getHotkey().matches(event)) continue;
            group.pressHotkey();
            matched = true;
            save |= !group.getMode().isHoldMode();
        }
        if (matched) publishActiveMarkers();
        if (save)
        {
            saveGroups();
        }
        else if (matched)
        {
            refreshPanel();
        }
    }

    @Override
    public synchronized void keyReleased(KeyEvent event)
    {
        int keyCode = event.getExtendedKeyCode();
        pressedKeys.remove(keyCode);
        Integer modifier = Keybind.getModifierForKeyCode(keyCode);
        boolean changed = false;
        for (TileHotkeyGroup group : groups)
        {
            if (group.keyCode == keyCode || (modifier != null && (group.modifiers & modifier) != 0))
            {
                changed |= group.releaseHotkey();
            }
        }
        if (changed)
        {
            publishActiveMarkers();
            refreshPanel();
        }
    }

    @Override
    public synchronized void focusLost()
    {
        pressedKeys.clear();
        boolean changed = false;
        for (TileHotkeyGroup group : groups)
        {
            changed |= group.releaseHotkey();
        }
        if (changed)
        {
            publishActiveMarkers();
            refreshPanel();
        }
    }

    Collection<RenderTile> getActiveTiles()
    {
        List<Marker> markers = activeMarkers;
        if (markers.isEmpty()) return List.of();
        WorldView top = client.getTopLevelWorldView();
        if (top == null) return List.of();
        List<RenderTile> topTiles = projections.project(top, markers);
        List<RenderTile> combined = null;
        for (WorldEntity entity : top.worldEntities())
        {
            List<RenderTile> tiles = projections.project(entity.getWorldView(), markers);
            if (!tiles.isEmpty())
            {
                if (combined == null) combined = new ArrayList<>(topTiles);
                combined.addAll(tiles);
            }
        }
        return combined == null ? topTiles : List.copyOf(combined);
    }

    // Build under the model lock and publish in one write. Rendering never reads groups or packs.
    private void publishActiveMarkers()
    {
        List<Marker> markers = new ArrayList<>();
        for (TileHotkeyGroup group : groups)
        {
            if (group.getMode() != TileHotkeyGroup.Mode.CYCLE)
            {
                if (group.isGroupVisible())
                {
                    for (TileHotkeyPack pack : group.packs) addPackMarkers(markers, pack);
                }
            }
            else if (group.activePackIndex >= 0 && group.activePackIndex < group.packs.size())
            {
                addPackMarkers(markers, group.packs.get(group.activePackIndex));
            }
        }
        if (!activeMarkers.equals(markers)) activeMarkers = List.copyOf(markers);
    }

    private static void addPackMarkers(List<Marker> markers, TileHotkeyPack pack)
    {
        for (HotkeyTile tile : pack.tiles)
        {
            WorldPoint point = WorldPoint.fromRegion(tile.regionId, tile.regionX, tile.regionY, tile.z);
            markers.add(new Marker(point, tile.color, tile.label));
        }
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event)
    {
        if (CONFIG_GROUP.equals(event.getGroup()) && TileHotkeysConfig.HIDE_SIDEBAR.equals(event.getKey()))
        {
            updateSidebarButton();
        }
    }

    @Subscribe
    public void onProfileChanged(ProfileChanged event)
    {
        loadGroups();
        projections.clear();
        updateSidebarButton();
        refreshPanel();
    }

    @Subscribe
    public void onWorldViewLoaded(WorldViewLoaded event)
    {
        projections.invalidate(event.getWorldView());
        refreshPanel();
    }

    @Subscribe
    public void onWorldViewUnloaded(WorldViewUnloaded event)
    {
        projections.invalidate(event.getWorldView());
    }

    static final class RenderTile
    {
        final WorldView worldView;
        final WorldPoint point;
        final java.awt.Color color;
        final String label;
        RenderTile(WorldView worldView, WorldPoint point, java.awt.Color color, String label)
        {
            this.worldView = worldView;
            this.point = point;
            this.color = color;
            this.label = label;
        }
    }
}

