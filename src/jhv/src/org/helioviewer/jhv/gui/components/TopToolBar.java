package org.helioviewer.jhv.gui.components;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

import javax.swing.AbstractButton;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;
import javax.swing.SwingConstants;

import org.helioviewer.base.logging.Log;
import org.helioviewer.jhv.Settings;
import org.helioviewer.jhv.gui.IconBank;
import org.helioviewer.jhv.gui.IconBank.JHVIcon;
import org.helioviewer.jhv.gui.actions.SetPanSelectionAction;
import org.helioviewer.jhv.gui.actions.SetZoomBoxSelectionAction;
import org.helioviewer.jhv.gui.actions.ToggleSolarRotationTrackingAction;
import org.helioviewer.jhv.gui.actions.Zoom1to1Action;
import org.helioviewer.jhv.gui.actions.ZoomFitAction;
import org.helioviewer.jhv.gui.actions.ZoomInAction;
import org.helioviewer.jhv.gui.actions.ZoomOutAction;

/**
 * Toolbar containing the most common actions.
 * 
 * <p>
 * The toolbar provides a context menu to change its appearance.
 * 
 * @author Markus Langenberg
 * @author Andre Dau
 */
public class TopToolBar extends JToolBar implements MouseListener {

    private static final long serialVersionUID = 1L;

    public enum SelectionMode {
        PAN, ZOOMBOX
    };

    private enum DisplayMode {
        ICONANDTEXT, ICONONLY, TEXTONLY
    };

    private DisplayMode displayMode;

    private JToggleButton panButton;
    private JToggleButton zoomBoxButton;

    private JToggleButton trackSolarRotationButton;

    /**
     * Default constructor.
     */
    public TopToolBar() {
        setRollover(true);

        try {
            displayMode = DisplayMode.valueOf(Settings.getSingletonInstance().getProperty("display.toolbar").toUpperCase());
        } catch (Exception e) {
            Log.error("Error when reading the display mode of the toolbar", e);
            displayMode = DisplayMode.ICONANDTEXT;
        }

        createNewToolBar(SelectionMode.PAN);

        addMouseListener(this);
    }

    /**
     * Sets the active selection mode.
     * 
     * @param mode
     *            Selection mode, can be either PAN, ZOOMBOX or FOCUS.
     */
    public void setActiveSelectionMode(SelectionMode mode) {
        switch (mode) {
        case PAN:
            panButton.doClick();
            break;
        case ZOOMBOX:
            zoomBoxButton.doClick();
            break;
        }
    }

    /**
     * {@inheritDoc}
     */
    public void mouseClicked(MouseEvent e) {
        maybeShowPopup(e);
    }

    /**
     * {@inheritDoc}
     */
    public void mouseEntered(MouseEvent e) {
    }

    /**
     * {@inheritDoc}
     */
    public void mouseExited(MouseEvent e) {
    }

    /**
     * {@inheritDoc}
     */
    public void mousePressed(MouseEvent e) {
        maybeShowPopup(e);
    }

    /**
     * {@inheritDoc}
     */
    public void mouseReleased(MouseEvent e) {
    }

    /**
     * (Re)creates the toolbar.
     * 
     * This function is called during the construction of this panel as well as
     * after the display mode has changed.
     * 
     * @param selectionMode
     *            Current selection mode, to select the correct button.
     * @see #setDisplayMode(DisplayMode)
     */
    private void createNewToolBar(SelectionMode selectionMode) {
        removeAll();

        // Zoom
        addButton(new JButton(new ZoomInAction(false)));
        addButton(new JButton(new ZoomOutAction(false)));
        addButton(new JButton(new ZoomFitAction(false)));
        addButton(new JButton(new Zoom1to1Action(false)));

        addSeparator();

        // Selection
        ButtonGroup group = new ButtonGroup();

        panButton = new JToggleButton(new SetPanSelectionAction());
        panButton.setSelected(selectionMode == SelectionMode.PAN);
        panButton.setIcon(IconBank.getIcon(JHVIcon.PAN));
        panButton.setSelectedIcon(IconBank.getIcon(JHVIcon.PAN_SELECTED));
        panButton.setToolTipText("Select Panning");
        group.add(panButton);
        addButton(panButton);

        zoomBoxButton = new JToggleButton(new SetZoomBoxSelectionAction());
        zoomBoxButton.setSelected(selectionMode == SelectionMode.ZOOMBOX);
        zoomBoxButton.setIcon(IconBank.getIcon(JHVIcon.SELECT));
        zoomBoxButton.setSelectedIcon(IconBank.getIcon(JHVIcon.SELECT_SELECTED));
        zoomBoxButton.setToolTipText("Select Zoom Box");
        group.add(zoomBoxButton);
        addButton(zoomBoxButton);

        addSeparator();

        boolean solarRotationWasEnabled = (trackSolarRotationButton != null && trackSolarRotationButton.isSelected());
        trackSolarRotationButton = new JToggleButton(new ToggleSolarRotationTrackingAction(solarRotationWasEnabled));
        trackSolarRotationButton.setSelected(solarRotationWasEnabled);
        trackSolarRotationButton.setIcon(IconBank.getIcon(JHVIcon.FOCUS));
        trackSolarRotationButton.setSelectedIcon(IconBank.getIcon(JHVIcon.FOCUS_SELECTED));
        trackSolarRotationButton.setToolTipText("Enable Solar Rotation Tracking");
        addButton(trackSolarRotationButton);

        // VSO Export - DEACTIVATED FOR NOW
        // addSeparator();
        // addButton(new JButton(new NewQueryAction(true)));
    }

    /**
     * Adds a given button to the toolbar.
     * 
     * This function sets some standard values of the button regarding the
     * appearance. The current display mode is taken into account.
     * 
     * @param button
     *            Button to add
     */
    private void addButton(AbstractButton button) {
        // button.setMargin(buttonMargin);
        button.setVerticalTextPosition(SwingConstants.BOTTOM);
        button.setHorizontalTextPosition(SwingConstants.CENTER);
        button.setBorderPainted(false);
        button.setFocusPainted(false);
        button.setOpaque(false);
        button.addMouseListener(this);

        switch (displayMode) {
        case TEXTONLY:
            button.setIcon(null);
            break;
        case ICONONLY:
            button.setText("");
            break;
        }

        add(button);
    }

    /**
     * Sets the current display mode.
     * 
     * This changes the way the toolbar is display.
     * 
     * @param mode
     *            Display mode, can be either ICONANDTEXT, ICONONLY or TEXTONLY
     */
    private void setDisplayMode(DisplayMode mode) {
        displayMode = mode;
        Settings.getSingletonInstance().setProperty("display.toolbar", mode.toString().toLowerCase());
        Settings.getSingletonInstance().save();

        SelectionMode selectionMode = SelectionMode.PAN;

        if (zoomBoxButton.isSelected()) {
            selectionMode = SelectionMode.ZOOMBOX;
        }

        createNewToolBar(selectionMode);
        revalidate();
    }

    /**
     * Shows the popup if the correct mouse button was pressed.
     * 
     * @param e
     *            MouseEvent that triggered the event
     */
    private void maybeShowPopup(MouseEvent e) {
        if (e.isPopupTrigger() || e.getButton() == MouseEvent.BUTTON3) {

            JPopupMenu popUpMenu = new JPopupMenu();
            ButtonGroup group = new ButtonGroup();

            JRadioButtonMenuItem iconAndText = new JRadioButtonMenuItem("Icon and Text", displayMode == DisplayMode.ICONANDTEXT);
            iconAndText.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    setDisplayMode(DisplayMode.ICONANDTEXT);
                }
            });
            group.add(iconAndText);
            popUpMenu.add(iconAndText);

            JRadioButtonMenuItem iconOnly = new JRadioButtonMenuItem("Icon Only", displayMode == DisplayMode.ICONONLY);
            iconOnly.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    setDisplayMode(DisplayMode.ICONONLY);
                }
            });
            group.add(iconOnly);
            popUpMenu.add(iconOnly);

            JRadioButtonMenuItem textOnly = new JRadioButtonMenuItem("Text Only", displayMode == DisplayMode.TEXTONLY);
            textOnly.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    setDisplayMode(DisplayMode.TEXTONLY);
                }
            });
            group.add(textOnly);
            popUpMenu.add(textOnly);

            popUpMenu.show(e.getComponent(), e.getX(), e.getY());
        }
    }
}
