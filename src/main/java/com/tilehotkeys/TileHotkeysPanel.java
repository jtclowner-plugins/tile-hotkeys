package com.tilehotkeys;

import com.tilehotkeys.TileHotkeyGroup.Mode;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import net.runelite.client.config.Keybind;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

final class TileHotkeysPanel extends PluginPanel
{
    private static final Color SELECTED_PACK = new Color(88, 63, 32);
    private static final Color ROW_TEXT = ColorScheme.TEXT_COLOR;
    private static final Color MUTED_TEXT = ColorScheme.LIGHT_GRAY_COLOR;

    private final TileHotkeysPlugin plugin;
    private final JPanel groupList = column();
    private final JPanel groupButtons = new JPanel(new GridLayout(1, 2, 5, 0));
    private final Map<TileHotkeyGroup, GroupCard> cards = new LinkedHashMap<>();
    private final JLabel empty = new JLabel("No tile pack groups yet", SwingConstants.CENTER);
    private final JLabel status = new JLabel(" ");
    private TileHotkeyPack selectedPack;

    TileHotkeysPanel(TileHotkeysPlugin plugin)
    {
        super(false);
        this.plugin = plugin;
        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JLabel title = new JLabel("Tile Hotkeys", SwingConstants.CENTER);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
        title.setForeground(ColorScheme.BRAND_ORANGE);
        title.setBorder(new EmptyBorder(12, 8, 10, 8));
        header.add(title, BorderLayout.NORTH);

        JLabel section = new JLabel("Tile pack groups");
        section.setFont(section.getFont().deriveFont(Font.BOLD));
        section.setForeground(MUTED_TEXT);
        section.setBorder(new EmptyBorder(0, 8, 8, 8));
        header.add(section, BorderLayout.SOUTH);
        add(header, BorderLayout.NORTH);

        groupList.setBorder(new EmptyBorder(0, 8, 0, 8));
        empty.setForeground(MUTED_TEXT);
        empty.setBorder(new EmptyBorder(22, 0, 14, 0));
        empty.setAlignmentX(Component.LEFT_ALIGNMENT);
        empty.setMaximumSize(new Dimension(Integer.MAX_VALUE, empty.getPreferredSize().height));
        groupList.add(empty);
        JPanel listViewport = new JPanel(new BorderLayout())
        {
            @Override
            public Dimension getPreferredSize()
            {
                return new Dimension(0, super.getPreferredSize().height);
            }
        };
        listViewport.setOpaque(false);
        listViewport.add(groupList, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(listViewport,
            ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        add(scroll, BorderLayout.CENTER);

        JButton newGroup = new JButton("New group");
        newGroup.addActionListener(event ->
        {
            String name = askName("New group", "Group name");
            if (name != null)
            {
                reveal(plugin.addGroup(name), null);
            }
        });
        JButton importGroup = new JButton("Import group");
        importGroup.setToolTipText("Import a Tile Hotkeys group backup from the clipboard");
        importGroup.addActionListener(event ->
        {
            TileHotkeyGroup group = plugin.importGroupFromClipboard();
            if (group != null)
            {
                setStatus("Group imported.");
                reveal(group, null);
            }
            else
            {
                showImportError("Tile Hotkeys group backup");
            }
        });

        status.setForeground(MUTED_TEXT);
        status.setBorder(new EmptyBorder(5, 8, 7, 8));
        status.setVisible(false);
        add(status, BorderLayout.SOUTH);
        groupButtons.setOpaque(false);
        groupButtons.setAlignmentX(Component.LEFT_ALIGNMENT);
        groupButtons.setBorder(new EmptyBorder(0, 0, 8, 0));
        groupButtons.add(newGroup);
        groupButtons.add(importGroup);
        groupList.add(groupButtons);

        refresh();
    }

    void refresh()
    {
        List<TileHotkeyGroup> groups = new ArrayList<>(plugin.getGroups());
        if (selectedPack != null && groups.stream().noneMatch(group -> group.packs.contains(selectedPack)))
        {
            selectedPack = null;
        }
        if (!new ArrayList<>(cards.keySet()).equals(groups))
        {
            Map<TileHotkeyGroup, GroupCard> previous = new LinkedHashMap<>(cards);
            cards.clear();
            groupList.removeAll();
            for (int i = 0; i < groups.size(); i++)
            {
                TileHotkeyGroup group = groups.get(i);
                GroupCard card = previous.get(group);
                if (card == null)
                {
                    card = new GroupCard(group, i == 0);
                }
                cards.put(group, card);
                groupList.add(card);
                groupList.add(Box.createVerticalStrut(6));
            }
            if (groups.isEmpty())
            {
                groupList.add(empty);
            }
            groupList.add(groupButtons);
        }

        for (GroupCard card : cards.values())
        {
            card.update();
        }
        groupList.revalidate();
        groupList.repaint();
    }

    private void reveal(TileHotkeyGroup group, TileHotkeyPack pack)
    {
        refresh();
        GroupCard card = cards.get(group);
        if (card == null)
        {
            return;
        }
        card.expanded = true;
        card.update();
        int packIndex = pack == null ? -1 : group.packs.indexOf(pack);
        JComponent row = packIndex >= 0 ? card.packRows.get(packIndex).row : card.header;
        SwingUtilities.invokeLater(() ->
        {
            if (SwingUtilities.isDescendingFrom(row, groupList))
            {
                groupList.scrollRectToVisible(SwingUtilities.convertRectangle(
                    row, new Rectangle(row.getSize()), groupList));
            }
        });
    }

    private void selectPack(TileHotkeyPack pack)
    {
        selectedPack = pack;
        refresh();
    }

    private void showAddPackMenu(TileHotkeyGroup group, JButton link)
    {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem importPack = new JMenuItem("Import pack from clipboard");
        importPack.addActionListener(event -> importPack(group));
        menu.add(importPack);
        if (group.getMode() == Mode.CYCLE)
        {
            JMenuItem spacer = new JMenuItem("Add spacer");
            spacer.addActionListener(event ->
            {
                TileHotkeyPack entry = plugin.addSpacer(group);
                if (entry != null)
                {
                    selectedPack = entry;
                    reveal(group, entry);
                }
            });
            menu.add(spacer);
        }
        menu.show(link, 0, link.getHeight());
    }

    private void importPack(TileHotkeyGroup group)
    {
        String name = askName("Import pack", "Pack name");
        if (name == null || !plugin.getGroups().contains(group))
        {
            return;
        }
        TileHotkeyPack pack = plugin.importPackFromClipboard(group, name);
        if (pack == null)
        {
            showImportError("Ground Markers tile list");
            return;
        }
        selectedPack = pack;
        setStatus("Pack imported.");
        reveal(group, pack);
    }

    private void export(TileHotkeyGroup group, TileHotkeyPack pack)
    {
        try
        {
            if (pack != null)
            {
                int count = plugin.exportPack(group, pack);
                setStatus(count == 0 ? "This pack has no tiles." : "Pack copied to clipboard.");
            }
            else if (plugin.exportGroup(group))
            {
                setStatus("Group copied to clipboard.");
            }
        }
        catch (IllegalStateException ex)
        {
            setStatus("Clipboard busy. Try again.");
        }
    }

    private void setStatus(String text)
    {
        status.setText(text);
        status.setToolTipText(text);
        status.setVisible(true);
    }

    private String askName(String title, String message)
    {
        return askName(title, message, "");
    }

    private String askName(String title, String message, String initialName)
    {
        String name = (String) JOptionPane.showInputDialog(this, message, title, JOptionPane.PLAIN_MESSAGE,
            null, null, initialName);
        return name == null || name.trim().isEmpty() ? null : name.trim();
    }

    private void showImportError(String format)
    {
        JOptionPane.showMessageDialog(this, "The clipboard doesn't contain a valid " + format + ".",
            "Import failed", JOptionPane.WARNING_MESSAGE);
    }

    private boolean confirmDelete(String message)
    {
        return JOptionPane.showConfirmDialog(this, message, "Delete", JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION;
    }

    private final class GroupCard extends JPanel
    {
        private final TileHotkeyGroup group;
        private final JPanel header = row();
        private final JButton disclosure = iconButton(Glyph.EXPAND, "Expand group");
        private final JButton groupName = textButton();
        private final JPanel body = column();
        private final JPanel settings = new JPanel(new BorderLayout(7, 0));
        private final JComboBox<Mode> mode = new JComboBox<>(Mode.values());
        private final JButton hotkey = new JButton();
        private final List<PackRow> packRows = new ArrayList<>();
        private final JPanel packs = new JPanel()
        {
            @Override
            protected void paintChildren(Graphics graphics)
            {
                super.paintChildren(graphics);
                if (dropIndex >= 0 && !packRows.isEmpty())
                {
                    JPanel target = packRows.get(Math.min(dropIndex, packRows.size() - 1)).row;
                    int y = dropIndex < packRows.size() ? target.getY() : target.getY() + target.getHeight() - 2;
                    graphics.setColor(ColorScheme.BRAND_ORANGE);
                    graphics.fillRect(8, y, getWidth() - 16, 2);
                }
            }
        };
        private boolean expanded;
        private boolean updatingSettings;
        private boolean recordingHotkey;
        private Keybind pendingModifierHotkey;
        private int dropIndex = -1;

        private GroupCard(TileHotkeyGroup group, boolean expanded)
        {
            super(new BorderLayout());
            this.group = group;
            this.expanded = expanded;
            setAlignmentX(Component.LEFT_ALIGNMENT);
            setBorder(BorderFactory.createLineBorder(ColorScheme.BORDER_COLOR));
            setBackground(ColorScheme.DARKER_GRAY_COLOR);

            disclosure.addActionListener(event -> toggleExpanded());
            groupName.setFont(groupName.getFont().deriveFont(Font.BOLD));
            groupName.addActionListener(event -> toggleExpanded());
            JPanel groupActions = actions("group", () ->
            {
                String newName = askName("Rename group", "Group name", group.name);
                if (newName != null && plugin.renameGroup(group, newName))
                {
                    refresh();
                }
            }, () -> export(group, null), () ->
            {
                if (confirmDelete("Delete \"" + group.name + "\" and its packs?"))
                {
                    plugin.removeGroup(group);
                    refresh();
                }
            });
            header.setBackground(ColorScheme.DARKER_GRAY_COLOR);
            header.add(disclosure, BorderLayout.WEST);
            header.add(groupName, BorderLayout.CENTER);
            header.add(groupActions, BorderLayout.EAST);
            add(header, BorderLayout.NORTH);

            buildSettings();
            body.add(settings);
            packs.setOpaque(false);
            packs.setLayout(new BoxLayout(packs, BoxLayout.Y_AXIS));
            packs.setAlignmentX(Component.LEFT_ALIGNMENT);
            JLabel packsLabel = new JLabel("Tile packs");
            packsLabel.setForeground(MUTED_TEXT);
            packsLabel.setFont(packsLabel.getFont().deriveFont(Font.BOLD));
            packsLabel.setBorder(new EmptyBorder(5, 10, 5, 10));
            packsLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            body.add(packsLabel);
            body.add(packs);

            JButton addPack = textButton();
            addPack.setText("+ Add pack");
            describe(addPack, "Add pack to group");
            addPack.setFont(addPack.getFont().deriveFont(Math.max(10f, addPack.getFont().getSize2D() - 1f)));
            addPack.setForeground(ColorScheme.BRAND_ORANGE);
            addPack.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addPack.addActionListener(event -> showAddPackMenu(group, addPack));
            JPanel packFooter = new JPanel(new BorderLayout());
            packFooter.setOpaque(false);
            packFooter.setBorder(new EmptyBorder(5, 27, 7, 10));
            packFooter.setAlignmentX(Component.LEFT_ALIGNMENT);
            packFooter.add(addPack, BorderLayout.WEST);
            body.add(packFooter);
            add(body, BorderLayout.CENTER);
        }

        @Override
        public Dimension getMaximumSize()
        {
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }

        private void buildSettings()
        {
            settings.setOpaque(false);
            settings.setAlignmentX(Component.LEFT_ALIGNMENT);
            settings.setBorder(new EmptyBorder(5, 10, 7, 10));
            JPanel labels = new JPanel(new GridLayout(2, 1, 0, 3));
            labels.setOpaque(false);
            JLabel hotkeyLabel = new JLabel("Hotkey");
            hotkeyLabel.setLabelFor(hotkey);
            JLabel modeLabel = new JLabel("Mode");
            modeLabel.setLabelFor(mode);
            labels.add(hotkeyLabel);
            labels.add(modeLabel);
            JPanel controls = new JPanel(new GridLayout(2, 1, 0, 3));
            controls.setOpaque(false);
            controls.add(hotkey);
            controls.add(mode);
            settings.add(labels, BorderLayout.WEST);
            settings.add(controls, BorderLayout.CENTER);

            hotkey.setToolTipText("Click to set a hotkey. Release Ctrl or Shift to bind it on its own. Escape cancels, Backspace clears.");
            hotkey.addActionListener(event ->
            {
                recordingHotkey = true;
                pendingModifierHotkey = null;
                hotkey.setFocusTraversalKeysEnabled(false);
                hotkey.setText("Press a key...");
                hotkey.requestFocusInWindow();
            });
            hotkey.addFocusListener(new FocusAdapter()
            {
                @Override
                public void focusLost(FocusEvent event)
                {
                    finishHotkeyRecording(null);
                }
            });
            hotkey.addKeyListener(new KeyAdapter()
            {
                @Override
                public void keyPressed(KeyEvent event)
                {
                    if (!recordingHotkey)
                    {
                        return;
                    }
                    event.consume();
                    int code = event.getKeyCode();
                    Integer modifier = Keybind.getModifierForKeyCode(code);
                    if (modifier != null)
                    {
                        pendingModifierHotkey = new Keybind(KeyEvent.VK_UNDEFINED, event.getModifiersEx() | modifier);
                        hotkey.setText(pendingModifierHotkey.toString() + "...");
                        return;
                    }
                    if (code == KeyEvent.VK_ESCAPE)
                    {
                        finishHotkeyRecording(null);
                    }
                    else if (code == KeyEvent.VK_BACK_SPACE && event.getModifiersEx() == 0)
                    {
                        finishHotkeyRecording(Keybind.NOT_SET);
                    }
                    else
                    {
                        finishHotkeyRecording(new Keybind(code, event.getModifiersEx()));
                    }
                }

                @Override
                public void keyReleased(KeyEvent event)
                {
                    if (!recordingHotkey || pendingModifierHotkey == null)
                    {
                        return;
                    }
                    Integer modifier = Keybind.getModifierForKeyCode(event.getKeyCode());
                    if (modifier != null && (pendingModifierHotkey.getModifiers() & modifier) != 0)
                    {
                        event.consume();
                        finishHotkeyRecording(pendingModifierHotkey);
                    }
                }
            });
            mode.addActionListener(event ->
            {
                Mode newMode = (Mode) mode.getSelectedItem();
                if (!updatingSettings && newMode != null && newMode != group.getMode())
                {
                    plugin.setGroupMode(group, newMode);
                    refresh();
                }
            });
        }

        private void finishHotkeyRecording(Keybind keybind)
        {
            recordingHotkey = false;
            pendingModifierHotkey = null;
            hotkey.setFocusTraversalKeysEnabled(true);
            if (keybind != null)
            {
                plugin.setGroupHotkey(group, keybind);
            }
            showHotkey();
        }

        private void showHotkey()
        {
            if (!recordingHotkey)
            {
                hotkey.setText(group.getHotkey().toString());
            }
        }

        private void toggleExpanded()
        {
            expanded = !expanded;
            refresh();
        }

        private void update()
        {
            disclosure.setIcon(expanded ? Glyph.COLLAPSE : Glyph.EXPAND);
            describe(disclosure, expanded ? "Collapse group" : "Expand group");
            long packCount = group.packs.stream().filter(pack -> !pack.isSpacer()).count();
            groupName.setText(group.name + " (" + packCount + ")");
            groupName.setToolTipText(group.name);
            body.setVisible(expanded);
            updatingSettings = true;
            showHotkey();
            mode.setSelectedItem(group.getMode());
            updatingSettings = false;

            if (packRows.size() != group.packs.size() || !samePackOrder())
            {
                packRows.clear();
                packs.removeAll();
                dropIndex = -1;
                for (TileHotkeyPack pack : group.packs)
                {
                    PackRow packRow = new PackRow(pack);
                    packRows.add(packRow);
                    packs.add(packRow.row);
                }
            }
            for (int i = 0; i < packRows.size(); i++)
            {
                PackRow packRow = packRows.get(i);
                boolean active = group.getMode() == Mode.CYCLE
                    ? group.activePackIndex == i : group.isGroupVisible() && !packRow.pack.isSpacer();
                packRow.update(packRow.pack == selectedPack, active);
            }
        }

        private boolean samePackOrder()
        {
            for (int i = 0; i < packRows.size(); i++)
            {
                if (packRows.get(i).pack != group.packs.get(i))
                {
                    return false;
                }
            }
            return true;
        }

        private int insertionIndex(Point point)
        {
            if (!packs.contains(point))
            {
                return -1;
            }
            for (int i = 0; i < packRows.size(); i++)
            {
                JPanel row = packRows.get(i).row;
                if (point.y < row.getY() + row.getHeight() / 2)
                {
                    return i;
                }
            }
            return packRows.size();
        }

        private final class PackRow
        {
            private final TileHotkeyPack pack;
            private final JPanel row = row();
            private final JButton name = textButton();
            private final JPanel packActions;

            private PackRow(TileHotkeyPack pack)
            {
                this.pack = pack;
                row.setBorder(new EmptyBorder(0, 8, 0, 4));
                name.addActionListener(event -> selectPack(pack));
                boolean spacer = pack.isSpacer();
                packActions = actions(spacer ? "spacer" : "pack",
                    spacer ? null : this::rename,
                    spacer ? null : () -> export(group, pack),
                    this::delete);
                JLabel grip = new JLabel(Glyph.GRIP, SwingConstants.CENTER);
                grip.setPreferredSize(new Dimension(19, 26));
                grip.setForeground(MUTED_TEXT);
                grip.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                grip.setToolTipText("Drag to reorder within this group");
                MouseAdapter drag = new MouseAdapter()
                {
                    private Point press;
                    private boolean dragging;

                    @Override
                    public void mousePressed(MouseEvent event)
                    {
                        if (SwingUtilities.isLeftMouseButton(event))
                        {
                            press = event.getPoint();
                            selectPack(pack);
                        }
                    }

                    @Override
                    public void mouseDragged(MouseEvent event)
                    {
                        if (press == null || !dragging && press.distance(event.getPoint()) < 4)
                        {
                            return;
                        }
                        dragging = true;
                        Point point = SwingUtilities.convertPoint(grip, event.getPoint(), packs);
                        dropIndex = insertionIndex(point);
                        if (dropIndex >= 0)
                        {
                            packs.scrollRectToVisible(new Rectangle(point.x, point.y - 12, 1, 24));
                        }
                        packs.repaint();
                    }

                    @Override
                    public void mouseReleased(MouseEvent event)
                    {
                        int insertion = dragging
                            ? insertionIndex(SwingUtilities.convertPoint(grip, event.getPoint(), packs)) : -1;
                        press = null;
                        dragging = false;
                        dropIndex = -1;
                        packs.repaint();
                        int from = group.packs.indexOf(pack);
                        if (insertion >= 0 && from >= 0)
                        {
                            int to = insertion > from ? insertion - 1 : insertion;
                            if (to != from)
                            {
                                plugin.movePack(group, pack, to);
                                reveal(group, pack);
                            }
                        }
                    }
                };
                grip.addMouseListener(drag);
                grip.addMouseMotionListener(drag);
                row.add(grip, BorderLayout.WEST);
                row.add(name, BorderLayout.CENTER);
                row.add(packActions, BorderLayout.EAST);
            }

            private void rename()
            {
                String newName = askName("Rename pack", "Pack name", pack.name);
                if (newName != null && plugin.renamePack(group, pack, newName))
                {
                    refresh();
                }
            }

            private void delete()
            {
                String target = pack.isSpacer() ? "this spacer" : "\"" + pack.name + "\"";
                if (confirmDelete("Delete " + target + "?"))
                {
                    plugin.removePack(group, pack);
                    refresh();
                }
            }

            private void update(boolean selected, boolean active)
            {
                boolean spacer = pack.isSpacer();
                String nameText = spacer ? "Spacer" : pack.name + " (" + pack.tiles.size() + ")";
                name.setText(active ? "\u25CF " + nameText : nameText);
                name.setToolTipText(spacer ? "Empty step in Cycle through mode" : pack.name);
                name.setForeground(active ? ColorScheme.BRAND_ORANGE : spacer ? MUTED_TEXT : ROW_TEXT);
                row.setBackground(selected ? SELECTED_PACK : ColorScheme.DARKER_GRAY_COLOR);
                packActions.setVisible(selected);
            }
        }
    }

    private static JPanel column()
    {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        return panel;
    }

    private static JPanel row()
    {
        JPanel panel = new JPanel(new BorderLayout())
        {
            @Override
            public Dimension getMaximumSize()
            {
                return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
            }
        };
        panel.setPreferredSize(new Dimension(0, 28));
        panel.setMinimumSize(new Dimension(0, 28));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        return panel;
    }

    private static JButton textButton()
    {
        JButton button = new JButton();
        button.setForeground(ROW_TEXT);
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setContentAreaFilled(false);
        button.setMargin(new Insets(0, 0, 0, 0));
        button.setBorder(BorderFactory.createEmptyBorder());
        button.setBorderPainted(false);
        return button;
    }

    private static JPanel actions(String target, Runnable rename, Runnable export, Runnable delete)
    {
        JPanel panel = new JPanel(new GridLayout(1, 0, 0, 0));
        panel.setOpaque(false);
        if (rename != null)
        {
            JButton renameButton = iconButton(Glyph.RENAME, "Rename " + target);
            renameButton.addActionListener(event -> rename.run());
            panel.add(renameButton);
        }
        if (export != null)
        {
            JButton exportButton = iconButton(Glyph.EXPORT, "Export " + target);
            exportButton.addActionListener(event -> export.run());
            panel.add(exportButton);
        }
        JButton deleteButton = iconButton(Glyph.DELETE, "Delete " + target);
        deleteButton.addActionListener(event -> delete.run());
        panel.add(deleteButton);
        return panel;
    }

    private static JButton iconButton(Glyph icon, String description)
    {
        JButton button = textButton();
        button.setIcon(icon);
        button.setHorizontalAlignment(SwingConstants.CENTER);
        button.setPreferredSize(new Dimension(25, 26));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        describe(button, description);
        return button;
    }

    private static void describe(JButton button, String description)
    {
        button.setToolTipText(description);
        button.getAccessibleContext().setAccessibleName(description);
    }

    private enum Glyph implements Icon
    {
        EXPAND, COLLAPSE, RENAME, EXPORT, DELETE, GRIP;

        @Override
        public int getIconWidth()
        {
            return 16;
        }

        @Override
        public int getIconHeight()
        {
            return 16;
        }

        @Override
        public void paintIcon(Component component, Graphics graphics, int x, int y)
        {
            Graphics2D g = (Graphics2D) graphics.create();
            g.translate(x, y);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(component.getForeground());
            switch (this)
            {
                case EXPAND:
                    g.drawPolyline(new int[] {6, 10, 6}, new int[] {4, 8, 12}, 3);
                    break;
                case COLLAPSE:
                    g.drawPolyline(new int[] {4, 8, 12}, new int[] {6, 10, 6}, 3);
                    break;
                case RENAME:
                    g.drawPolygon(new int[] {3, 4, 11, 13, 6}, new int[] {13, 10, 3, 5, 12}, 5);
                    g.drawLine(9, 5, 11, 7);
                    break;
                case EXPORT:
                    g.drawPolyline(new int[] {10, 3, 3, 12, 12}, new int[] {5, 5, 13, 13, 9}, 5);
                    g.drawLine(7, 9, 13, 3);
                    g.drawPolyline(new int[] {9, 13, 13}, new int[] {3, 3, 7}, 3);
                    break;
                case DELETE:
                    g.drawLine(3, 5, 13, 5);
                    g.drawPolyline(new int[] {6, 6, 10, 10}, new int[] {5, 3, 3, 5}, 4);
                    g.drawPolyline(new int[] {4, 5, 11, 12}, new int[] {6, 13, 13, 6}, 4);
                    break;
                case GRIP:
                    for (int i = 4; i <= 12; i += 4)
                    {
                        g.fillOval(5, i - 1, 2, 2);
                        g.fillOval(9, i - 1, 2, 2);
                    }
                    break;
            }
            g.dispose();
        }
    }
}
