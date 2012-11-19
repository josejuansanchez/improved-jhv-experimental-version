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
 * Action to zoom in.
 * 
 * @author Markus Langenberg
 */
public class ZoomInAction extends AbstractAction {

    private static final long serialVersionUID = 1L;

    /**
     * Constructor
     * 
     * @param small
     *            - if true, chooses a small (16x16), otherwise a large (24x24)
     *            icon for the action
     */
    public ZoomInAction(boolean small) {
        super("Zoom in", small ? IconBank.getIcon(JHVIcon.ZOOM_IN_SMALL) : IconBank.getIcon(JHVIcon.ZOOM_IN));
        putValue(SHORT_DESCRIPTION, "Zoom in x2");
        putValue(MNEMONIC_KEY, KeyEvent.VK_I);
        putValue(ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_COMMA, KeyEvent.ALT_MASK));
    }

    /**
     * {@inheritDoc}
     */
    public void actionPerformed(ActionEvent arg0) {
        ZoomController zoomController = new ZoomController();
        zoomController.setView(ImageViewerGui.getSingletonInstance().getMainView());
        zoomController.setImagePanel(ImageViewerGui.getSingletonInstance().getMainImagePanel());
        zoomController.zoomSteps(2);
    }

}