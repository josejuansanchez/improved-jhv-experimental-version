package org.helioviewer.jhv.gui.actions;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

import javax.swing.AbstractAction;
import javax.swing.KeyStroke;

import org.helioviewer.jhv.gui.IconBank;
import org.helioviewer.jhv.gui.ImageViewerGui;
import org.helioviewer.jhv.gui.IconBank.JHVIcon;
import org.helioviewer.jhv.gui.controller.ZoomController;

/**
 * Action to zoom to the native resoltion of the active layer.
 * 
 * @author Markus Langenberg
 */
public class Zoom1to1Action extends AbstractAction {

    private static final long serialVersionUID = 1L;

    /**
     * Constructor
     * 
     * @param small
     *            - if true, chooses a small (16x16), otherwise a large (24x24)
     *            icon for the action
     */
    public Zoom1to1Action(boolean small) {
        super("Zoom 1:1", small ? IconBank.getIcon(JHVIcon.ZOOM_1TO1_SMALL) : IconBank.getIcon(JHVIcon.ZOOM_1TO1));
        putValue(SHORT_DESCRIPTION, "Zoom to Native Resolution");
        putValue(MNEMONIC_KEY, KeyEvent.VK_Z);
        putValue(ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_L, KeyEvent.ALT_MASK));
    }

    /**
     * {@inheritDoc}
     */
    public void actionPerformed(ActionEvent e) {
        ZoomController zoomController = new ZoomController();
        zoomController.setView(ImageViewerGui.getSingletonInstance().getMainView());
        zoomController.setImagePanel(ImageViewerGui.getSingletonInstance().getMainImagePanel());
        zoomController.zoom1to1();
    }

}
