// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.preferences.plugin;

import static java.awt.GridBagConstraints.HORIZONTAL;
import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javax.swing.AbstractAction;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import org.openstreetmap.josm.data.Preferences;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.util.GuiHelper;
import org.openstreetmap.josm.tools.GBC;
import org.openstreetmap.josm.tools.Utils;

class PluginRepositoriesConfigurationDialog extends JPanel {

    private final DefaultListModel<String> model = new DefaultListModel<>();

    PluginRepositoriesConfigurationDialog() {
        super(new GridBagLayout());
        add(new JLabel(tr("Add JOSM Plugin description URL.")), GBC.eol());
        for (String s : Preferences.main().getPluginSites()) {
            model.addElement(s);
        }
        final JList<String> list = new JList<>(model);
        add(new JScrollPane(list), GBC.std().fill());
        JPanel buttons = new JPanel(new GridBagLayout());
        buttons.add(new JButton(new AbstractAction(tr("Add")) {
            @Override
            public void actionPerformed(ActionEvent e) {
                String s = JOptionPane.showInputDialog(
                        GuiHelper.getFrameForComponent(PluginRepositoriesConfigurationDialog.this),
                        tr("Add JOSM Plugin description URL."),
                        tr("Enter URL"),
                        JOptionPane.QUESTION_MESSAGE
                        );
                if (!Utils.isEmpty(s)) {
                    model.addElement(s);
                }
            }
        }), GBC.eol().fill(HORIZONTAL));
        buttons.add(new JButton(new AbstractAction(tr("Edit")) {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (list.getSelectedValue() == null) {
                    JOptionPane.showMessageDialog(
                            GuiHelper.getFrameForComponent(PluginRepositoriesConfigurationDialog.this),
                            tr("Please select an entry."),
                            tr("Warning"),
                            JOptionPane.WARNING_MESSAGE
                            );
                    return;
                }
                String s = (String) JOptionPane.showInputDialog(
                        MainApplication.getMainFrame(),
                        tr("Edit JOSM Plugin description URL."),
                        tr("JOSM Plugin description URL"),
                        JOptionPane.QUESTION_MESSAGE,
                        null,
                        null,
                        list.getSelectedValue()
                        );
                if (!Utils.isEmpty(s)) {
                    model.setElementAt(s, list.getSelectedIndex());
                }
            }
        }), GBC.eol().fill(HORIZONTAL));
        buttons.add(new JButton(new AbstractAction(tr("Delete")) {
            @Override
            public void actionPerformed(ActionEvent event) {
                if (list.getSelectedValue() == null) {
                    JOptionPane.showMessageDialog(
                            GuiHelper.getFrameForComponent(PluginRepositoriesConfigurationDialog.this),
                            tr("Please select an entry."),
                            tr("Warning"),
                            JOptionPane.WARNING_MESSAGE
                            );
                    return;
                }
                model.removeElement(list.getSelectedValue());
            }
        }), GBC.eol().fill(HORIZONTAL));
        buttons.add(new JButton(new AbstractAction(tr("Move Up")) {
            @Override
            public void actionPerformed(ActionEvent event) {
                int index = list.getSelectedIndex();
                if (index > 0) {
                    model.add(index - 1, model.remove(index));
                    list.setSelectedIndex(index - 1);
                }
            }
        }), GBC.eol().fill(HORIZONTAL));
        buttons.add(new JButton(new AbstractAction(tr("Move Down")) {
            @Override
            public void actionPerformed(ActionEvent event) {
                int index = list.getSelectedIndex();
                if (index < model.size() - 1) {
                    model.add(index + 1, model.remove(index));
                    list.setSelectedIndex(index + 1);
                }
            }
        }), GBC.eol().fill(HORIZONTAL));
        buttons.add(new JButton(new AbstractAction(tr("Add Default Site")) {
            @Override
            public void actionPerformed(ActionEvent event) {
                model.add(0, Preferences.getDefaultPluginSite());
            }
        }), GBC.eol().fill(HORIZONTAL));
        add(buttons, GBC.eol());

        add(new JLabel(tr("<html>Use:<ul>" +
            "<li>https:// to install plugins from the web" +
            "<li>file:///path/to/file to install a single jar" +
            "<li>file:///path/to/directory/ to install plugins from a local directory" +
            "<li>github://owner/repo to install plugins from a github repository")),
        GBC.eol());
    }

    protected List<String> getUpdateSites() {
        if (model.getSize() == 0)
            return Collections.emptyList();
        return IntStream.range(0, model.getSize())
                .mapToObj(model::get)
                .collect(Collectors.toList());
    }
}
