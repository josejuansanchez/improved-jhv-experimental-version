package org.helioviewer.jhv.gui.dialogs.plugins;

import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.util.LinkedList;

import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import org.helioviewer.jhv.gui.ImageViewerGui;
import org.helioviewer.jhv.gui.ViewchainFactory;
import org.helioviewer.jhv.gui.interfaces.ShowableDialog;
import org.helioviewer.viewmodel.factory.ViewFactory;
import org.helioviewer.viewmodel.view.ImageInfoView;
import org.helioviewer.viewmodel.view.LayeredView;
import org.helioviewer.viewmodelplugin.controller.PluginManager;
import org.helioviewer.viewmodelplugin.interfaces.Container;

/**
 * This class provides a basic plug-in management dialog. The dialog contains
 * two lists which display the activated {@link Container} and the available
 * {@link Container}. {@link Container} can be moved between those both status.
 * When the dialog will be closed, it saves the latest settings and rebuilds the
 * view chains.
 * 
 * @author Stephan Pagel
 */
public abstract class AbstractPluginDialog extends JDialog implements ShowableDialog, ActionListener, WindowListener {

    // ////////////////////////////////////////////////////////////////
    // Definitions
    // ////////////////////////////////////////////////////////////////

    private static final long serialVersionUID = 1L;

    protected JList activatedPluginsList = new JList();
    protected JList availablePluginsList = new JList();

    protected DefaultListModel activatedPluginsListModel = new DefaultListModel();
    protected DefaultListModel availablePluginsListModel = new DefaultListModel();

    private JButton activateButton = new JButton("<");
    private JButton deactivateButton = new JButton(">");
    private JButton closeButton = new JButton("Ok");

    private JPanel activatedPluginsControlPane = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
    private JPanel availablePluginsControlPane = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));

    protected boolean changed = false;

    // ////////////////////////////////////////////////////////////////
    // Methods
    // ////////////////////////////////////////////////////////////////

    /**
     * Basic constructor which has to be called from inherited class.
     * 
     * @param title
     *            Title which should be displayed in the dialog header.
     */
    protected AbstractPluginDialog(String title) {

        super(ImageViewerGui.getMainFrame(), title, true);

        initVisualComponents();
    }

    protected void setChanged(boolean changed) {
        this.changed = changed;
    }

    /**
     * Initialize the visual parts of the component.
     */
    private void initVisualComponents() {

        // basic window layout
        setLayout(new GridBagLayout());
        setResizable(false);
        addWindowListener(this);

        GridBagConstraints c = new GridBagConstraints();

        // /////////////////////////
        // activated plug-ins pane
        // /////////////////////////

        // header
        c.gridx = 0;
        c.gridy = 0;
        c.gridwidth = 1;
        c.gridheight = 1;
        c.weightx = 0.0;
        c.weighty = 0.0;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(4, 4, 0, 2);
        add(new JLabel("Enabled"), c);

        // list
        activatedPluginsList.setModel(activatedPluginsListModel);
        JScrollPane activatedListPane = new JScrollPane(activatedPluginsList);
        activatedListPane.setPreferredSize(new Dimension(250, activatedListPane.getPreferredSize().height));

        c.gridx = 0;
        c.gridy = 1;
        c.gridwidth = 1;
        c.gridheight = 1;
        c.weightx = 1.0;
        c.weighty = 1.0;
        c.fill = GridBagConstraints.BOTH;
        c.insets = new Insets(2, 4, 2, 2);
        add(activatedListPane, c);

        // control area
        c.gridx = 0;
        c.gridy = 2;
        c.gridwidth = 1;
        c.gridheight = 1;
        c.weightx = 0.0;
        c.weighty = 0.0;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(0, 4, 0, 2);
        add(activatedPluginsControlPane, c);

        // /////////////////////////
        // middle pane
        // /////////////////////////

        JPanel middlePane = new JPanel(new GridBagLayout());
        JPanel middleButtonPane = new JPanel();
        middleButtonPane.setLayout(new BoxLayout(middleButtonPane, BoxLayout.Y_AXIS));

        activateButton.addActionListener(this);
        activateButton.setToolTipText("Enable Plugin");
        deactivateButton.addActionListener(this);
        deactivateButton.setToolTipText("Disable Plugin");

        middleButtonPane.add(activateButton);
        middleButtonPane.add(deactivateButton);
        middlePane.add(middleButtonPane);

        c.gridx = 1;
        c.gridy = 0;
        c.gridwidth = 1;
        c.gridheight = 3;
        c.weightx = 0.0;
        c.weighty = 1.0;
        c.fill = GridBagConstraints.BOTH;
        c.insets = new Insets(4, 0, 0, 0);
        add(middlePane, c);

        // /////////////////////////
        // available plug ins pane
        // /////////////////////////

        // header
        c.gridx = 2;
        c.gridy = 0;
        c.gridwidth = 1;
        c.gridheight = 1;
        c.weightx = 0.0;
        c.weighty = 0.0;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(4, 2, 0, 4);
        add(new JLabel("Disabled"), c);

        // list
        availablePluginsList.setModel(availablePluginsListModel);
        JScrollPane availableListPane = new JScrollPane(availablePluginsList);
        availableListPane.setPreferredSize(new Dimension(250, availableListPane.getPreferredSize().height));

        c.gridx = 2;
        c.gridy = 1;
        c.gridwidth = 1;
        c.gridheight = 1;
        c.weightx = 1.0;
        c.weighty = 1.0;
        c.fill = GridBagConstraints.BOTH;
        c.insets = new Insets(2, 2, 2, 4);
        add(availableListPane, c);

        // control area
        c.gridx = 2;
        c.gridy = 2;
        c.gridwidth = 1;
        c.gridheight = 1;
        c.weightx = 0.0;
        c.weighty = 0.0;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(0, 2, 0, 4);
        add(availablePluginsControlPane, c);

        // /////////////////////////
        // bottom pane
        // /////////////////////////

        JPanel bottomPane = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        closeButton.addActionListener(this);
        bottomPane.add(closeButton);

        c.gridx = 0;
        c.gridy = 3;
        c.gridwidth = 3;
        c.gridheight = 1;
        c.weightx = 1.0;
        c.weighty = 0.0;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(0, 4, 4, 4);
        add(bottomPane, c);
    }

    /**
     * React on clicked buttons.
     */
    public void actionPerformed(ActionEvent arg0) {

        if (arg0.getSource() == closeButton) {

            // close dialog
            closeDialog();

        } else if (arg0.getSource() == activateButton) {
            setChanged(true);
            // activate the current selected container (plugin, filter, overlay,
            // ...)
            Object selected = availablePluginsList.getSelectedValue();

            if (selected != null && selected instanceof Container) {
                ((Container) selected).setActive(true);
                ((Container) selected).changeSettings();
                availablePluginsListModel.removeElement(selected);
                activatedPluginsListModel.addElement(selected);
            }
        } else if (arg0.getSource() == deactivateButton) {
            setChanged(true);
            // deactivate the current selected container (plugin, filter,
            // overlay, ...)
            Object selected = activatedPluginsList.getSelectedValue();

            if (selected != null && selected instanceof Container) {
                ((Container) selected).setActive(false);
                ((Container) selected).changeSettings();
                activatedPluginsListModel.removeElement(selected);
                availablePluginsListModel.addElement(selected);
            }
        }
    }

    /**
     * This method will close the dialog and handles things which have to be
     * done before. This includes saving the settings and rebuild the viewchains
     * with the current activated plug ins.
     */
    private void closeDialog() {

        // execute functions of inherited classes which have to be done before
        // leaving the dialog
        beforeSaveAndClose();

        if (changed) {
            // rebuild the view chains
            recreateViewChains();

            // save plug-in settings to XML file
            PluginManager.getSingeltonInstance().saveSettings();
        }
        changed = false;
        // close dialog
        dispose();
    }

    /**
     * Before the dialog will be closed inherited classes can execute individual
     * things here.
     */
    protected void beforeSaveAndClose() {
    }

    /**
     * Rebuilds the existing view chains and removes and adds corresponding
     * parts from plug ins.
     */
    private void recreateViewChains() {

        ViewchainFactory chainFactory = new ViewchainFactory();
        ViewFactory viewFactory = chainFactory.getUsedViewFactory();

        // Memorize all ImageInfoViews, remove all existing layers and add the
        // memorized ImageInfoViews as new layers again. Activated and needed
        // filters will be added to the corresponding sub chains.
        LayeredView mainLayeredView = ImageViewerGui.getSingletonInstance().getMainView().getAdapter(LayeredView.class);
        LinkedList<ImageInfoView> newImageInfoViews = new LinkedList<ImageInfoView>();

        while (mainLayeredView.getNumLayers() > 0) {
            newImageInfoViews.add(viewFactory.createViewFromSource(mainLayeredView.getLayer(0).getAdapter(ImageInfoView.class), true));
            mainLayeredView.removeLayer(0);
        }

        for (ImageInfoView imageView : newImageInfoViews) {
            chainFactory.addLayerToViewchainMain(imageView, mainLayeredView);
        }

        // Update all OverlayViews which are included in the view chain above
        // the layered view
        chainFactory.updateOverlayViewsInViewchainMain(ImageViewerGui.getSingletonInstance().getMainView());
    }

    /**
     * Adds a button to the activated list. The button will occur below the
     * list. The event the button will cause has to deal with the data of the
     * activated list.
     * 
     * @param button
     *            Button which has to be added to the activated list.
     */
    protected final void addActivatedPluginsControlButton(JButton button) {
        activatedPluginsControlPane.add(button);
    }

    /**
     * Adds a button to the available list. The button will occur below the
     * list. The event the button will cause has to deal with the data of the
     * available list.
     * 
     * @param button
     *            Button which has to be added to the available list.
     */
    protected final void addAvailablePluginsControlButton(JButton button) {
        availablePluginsControlPane.add(button);
    }

    /**
     * Sets a tool tip text to the activate button.
     * 
     * @param text
     *            Text to display as tool tip.
     */
    protected final void setActivateButtonToolTipText(String text) {
        activateButton.setToolTipText(text);
    }

    /**
     * Sets a tool tip text to the deactivate button.
     * 
     * @param text
     *            Text to display as tool tip.
     */
    protected final void setDeactivateButtonToolTipText(String text) {
        deactivateButton.setToolTipText(text);
    }

    /**
     * {@inheritDoc}
     */
    public void showDialog() {
        pack();
        setSize(getPreferredSize());
        setLocationRelativeTo(ImageViewerGui.getMainFrame());
        setVisible(true);
    };

    public void windowActivated(WindowEvent e) {
    }

    public void windowClosed(WindowEvent e) {
    }

    public void windowClosing(WindowEvent e) {
        closeDialog();
    }

    public void windowDeactivated(WindowEvent e) {
    }

    public void windowDeiconified(WindowEvent e) {
    }

    public void windowIconified(WindowEvent e) {
    }

    public void windowOpened(WindowEvent e) {
    }
}
