package com.tilehotkeys;

import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Provides;
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
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.WorldEntity;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.WorldViewLoaded;
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
    private final Set<Integer> pressedKeys = ConcurrentHashMap.newKeySet();
    private TileHotkeysPanel panel;
    private NavigationButton navigationButton;

    @Provides
    TileHotkeysConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(TileHotkeysConfig.class);
    }

    @Override
    protected void startUp()
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
    protected void shutDown()
    {
        keyManager.unregisterKeyListener(this);
        overlayManager.remove(overlay);
        if (navigationButton != null) clientToolbar.removeNavigation(navigationButton);
        groups.clear();
        panel = null;
        navigationButton = null;
        pressedKeys.clear();
    }

    private void loadGroups()
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
    }

    void saveGroups()
    {
        if (groups.isEmpty()) configManager.unsetConfiguration(CONFIG_GROUP, GROUPS_KEY);
        else configManager.setConfiguration(CONFIG_GROUP, GROUPS_KEY, gson.toJson(groups));
        refreshPanel();
    }

    List<TileHotkeyGroup> getGroups() { return Collections.unmodifiableList(groups); }

    TileHotkeyGroup addGroup(String name)
    {
        TileHotkeyGroup group = new TileHotkeyGroup(name);
        groups.add(group);
        saveGroups();
        return group;
    }

    boolean renameGroup(TileHotkeyGroup group, String name)
    {
        String trimmed = name.trim();
        if (!groups.contains(group) || trimmed.isEmpty() || trimmed.equals(group.name)) return false;
        group.name = trimmed;
        saveGroups();
        return true;
    }

    TileHotkeyPack addSpacer(TileHotkeyGroup group)
    {
        if (!groups.contains(group) || group.getMode() != TileHotkeyGroup.Mode.CYCLE) return null;
        TileHotkeyPack spacer = new TileHotkeyPack("Spacer");
        group.packs.add(spacer);
        saveGroups();
        return spacer;
    }

    void removeGroup(TileHotkeyGroup group)
    {
        if (groups.remove(group)) saveGroups();
    }

    void setGroupMode(int index, TileHotkeyGroup.Mode newMode)
    {
        if (index < 0 || index >= groups.size()) return;
        TileHotkeyGroup group = groups.get(index);
        if (group.getMode() == newMode) return;
        group.resetMode(newMode);
        saveGroups();
    }

    void setGroupHotkey(int index, Keybind keybind)
    {
        if (index < 0 || index >= groups.size()) return;
        TileHotkeyGroup group = groups.get(index);
        if (group.keyCode != keybind.getKeyCode() || group.modifiers != keybind.getModifiers())
        {
            group.releaseHotkey();
        }
        group.keyCode = keybind.getKeyCode();
        group.modifiers = keybind.getModifiers();
        saveGroups();
    }

    TileHotkeyPack importPackFromClipboard(TileHotkeyGroup group, String name)
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
            saveGroups();
            return pack;
        }
        catch (IOException | UnsupportedFlavorException | JsonSyntaxException | ClassCastException ex)
        {
            log.debug("Unable to import tile pack from clipboard", ex);
            return null;
        }
    }

    TileHotkeyGroup importGroupFromClipboard()
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
            saveGroups();
            return imported;
        }
        catch (IOException | UnsupportedFlavorException | JsonSyntaxException | ClassCastException ex)
        {
            log.debug("Unable to import tile hotkey group from clipboard", ex);
            return null;
        }
    }

    int exportPack(TileHotkeyGroup group, TileHotkeyPack pack)
    {
        if (!groups.contains(group) || !group.packs.contains(pack) || pack.tiles.isEmpty()) return 0;
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(gson.toJson(pack.tiles)), null);
        return pack.tiles.size();
    }

    boolean exportGroup(TileHotkeyGroup group)
    {
        if (!groups.contains(group)) return false;
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(gson.toJson(group)), null);
        return true;
    }

    void removePack(TileHotkeyGroup group, TileHotkeyPack pack)
    {
        if (!groups.contains(group)) return;
        int index = group.packs.indexOf(pack);
        if (index < 0) return;
        group.packs.remove(index);
        if (group.activePackIndex == index) group.activePackIndex = -1;
        else if (group.activePackIndex > index) group.activePackIndex--;
        saveGroups();
    }

    boolean renamePack(TileHotkeyGroup group, TileHotkeyPack pack, String name)
    {
        if (!groups.contains(group) || !group.packs.contains(pack) || pack.isSpacer()) return false;
        String trimmed = name.trim();
        if (trimmed.isEmpty() || trimmed.equals(pack.name)) return false;
        pack.name = trimmed;
        saveGroups();
        return true;
    }

    void movePack(int groupIndex, int fromIndex, int toIndex)
    {
        if (groupIndex < 0 || groupIndex >= groups.size()) return;
        TileHotkeyGroup group = groups.get(groupIndex);
        if (fromIndex < 0 || fromIndex >= group.packs.size()
            || toIndex < 0 || toIndex >= group.packs.size() || fromIndex == toIndex) return;

        TileHotkeyPack pack = group.packs.remove(fromIndex);
        group.packs.add(toIndex, pack);
        group.activePackIndex = adjustMovedIndex(group.activePackIndex, fromIndex, toIndex);
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
        if (panel != null) SwingUtilities.invokeLater(panel::refresh);
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
    public void keyPressed(KeyEvent event)
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
    public void keyReleased(KeyEvent event)
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
        if (changed) refreshPanel();
    }

    @Override
    public void focusLost()
    {
        pressedKeys.clear();
        boolean changed = false;
        for (TileHotkeyGroup group : groups)
        {
            changed |= group.releaseHotkey();
        }
        if (changed) refreshPanel();
    }

    Collection<RenderTile> getActiveTiles()
    {
        List<RenderTile> result = new ArrayList<>();
        WorldView top = client.getTopLevelWorldView();
        if (top == null) return result;
        addActiveTiles(result, top);
        for (WorldEntity entity : top.worldEntities()) addActiveTiles(result, entity.getWorldView());
        return result;
    }

    private void addActiveTiles(List<RenderTile> result, WorldView view)
    {
        for (TileHotkeyGroup group : groups)
        {
            if (group.getMode() != TileHotkeyGroup.Mode.CYCLE)
            {
                if (group.isGroupVisible())
                {
                    for (TileHotkeyPack pack : group.packs) addPackTiles(result, view, pack);
                }
            }
            else if (group.activePackIndex >= 0 && group.activePackIndex < group.packs.size())
            {
                addPackTiles(result, view, group.packs.get(group.activePackIndex));
            }
        }
    }

    private void addPackTiles(List<RenderTile> result, WorldView view, TileHotkeyPack pack)
    {
        for (HotkeyTile tile : pack.tiles)
        {
            WorldPoint point = WorldPoint.fromRegion(tile.regionId, tile.regionX, tile.regionY, tile.z);
            for (WorldPoint local : WorldPoint.toLocalInstance(view, point))
                result.add(new RenderTile(view, local, tile.color));
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
        updateSidebarButton();
        refreshPanel();
    }

    @Subscribe
    public void onWorldViewLoaded(WorldViewLoaded event) { refreshPanel(); }

    static final class RenderTile
    {
        final WorldView worldView;
        final WorldPoint point;
        final java.awt.Color color;
        RenderTile(WorldView worldView, WorldPoint point, java.awt.Color color)
        {
            this.worldView = worldView;
            this.point = point;
            this.color = color;
        }
    }
}

