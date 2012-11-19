package org.helioviewer.jhv.gui.components;

import java.awt.event.KeyEvent;

import javax.swing.JMenu;
import javax.swing.JMenuBar;

import org.helioviewer.base.FileUtils;
import org.helioviewer.jhv.gui.actions.CenterImageAction;
import org.helioviewer.jhv.gui.actions.CheckUpdateAction;
import org.helioviewer.jhv.gui.actions.ExitProgramAction;
import org.helioviewer.jhv.gui.actions.LoadStateAction;
import org.helioviewer.jhv.gui.actions.OpenLocalFileAction;
import org.helioviewer.jhv.gui.actions.OpenURLinBrowserAction;
import org.helioviewer.jhv.gui.actions.SaveScreenshotAction;
import org.helioviewer.jhv.gui.actions.SaveScreenshotAsAction;
import org.helioviewer.jhv.gui.actions.SaveStateAction;
import org.helioviewer.jhv.gui.actions.ShowDialogAction;
import org.helioviewer.jhv.gui.actions.ToggleFullscreenAction;
import org.helioviewer.jhv.gui.actions.Zoom1to1Action;
import org.helioviewer.jhv.gui.actions.ZoomFitAction;
import org.helioviewer.jhv.gui.actions.ZoomInAction;
import org.helioviewer.jhv.gui.actions.ZoomOutAction;
import org.helioviewer.jhv.gui.dialogs.AboutDialog;
import org.helioviewer.jhv.gui.dialogs.ExportMovieDialog;
import org.helioviewer.jhv.gui.dialogs.HelpDialog;
import org.helioviewer.jhv.gui.dialogs.OpenRemoteFileDialog;
import org.helioviewer.jhv.gui.dialogs.PreferencesDialog;
import org.helioviewer.jhv.gui.dialogs.plugins.FilterPluginDialog;
import org.helioviewer.jhv.gui.dialogs.plugins.OverlayPluginDialog;
import org.helioviewer.jhv.gui.dialogs.plugins.PluginDialog;

/**
 * Menu bar of the main window.
 * 
 * <p>
 * Basically, contains all actions from {@link org.helioviewer.jhv.gui.actions}.
 * 
 * @author Markus Langenberg
 * 
 */
public class MenuBar extends JMenuBar {

    private static final long serialVersionUID = 1L;

    /**
     * Default constructor.
     */
    public MenuBar() {
        super();

        JMenu fileMenu = new JMenu("File");
        fileMenu.setMnemonic(KeyEvent.VK_F);
        fileMenu.add(new OpenLocalFileAction());
        fileMenu.add(new ShowDialogAction("Open Remote Image...", OpenRemoteFileDialog.class));
        fileMenu.addSeparator();

        JMenu exampleMenu = new JMenu("Load Example");
        exampleMenu.add(new LoadStateAction("SDO/AIA Prominence Eruption (2010/12/06)", FileUtils.getResourceUrl("/examples/SDO_AIA_Prominence_Eruption_2010_12_06.jhv")));
        exampleMenu.add(new LoadStateAction("SDO/AIA Flare (2010/11/11)", FileUtils.getResourceUrl("/examples/SDO_AIA_Flare_Nov2010.jhv")));
        exampleMenu.add(new LoadStateAction("SOHO Halloween Storms (2003)", FileUtils.getResourceUrl("/examples/SOHO_Halloween_Storms_2003.jhv")));
        exampleMenu.add(new LoadStateAction("SOHO Comet Neat (February 2003)", FileUtils.getResourceUrl("/examples/SOHO_Comet_Neat_Feb2003.jhv")));
        exampleMenu.add(new LoadStateAction("SOHO Bastille Day flare (July 2000)", FileUtils.getResourceUrl("/examples/SOHO_Bastille_Day_Flare_July2000.jhv")));
        exampleMenu.add(new LoadStateAction("SOHO Lightbulb CME (February 2000)", FileUtils.getResourceUrl("/examples/SOHO_Lightbulb_CME_Feb2000.jhv")));
        exampleMenu.add(new LoadStateAction("SOHO CMEs (May 1998)", FileUtils.getResourceUrl("/examples/SOHO_CMEs_May1998.jhv")));

        fileMenu.add(exampleMenu);
        fileMenu.add(new LoadStateAction());
        fileMenu.add(new SaveStateAction());
        fileMenu.addSeparator();
        fileMenu.add(new SaveScreenshotAction());
        fileMenu.add(new SaveScreenshotAsAction());
        fileMenu.add(new ShowDialogAction("Export Movie...", ExportMovieDialog.class));

        // VSO Export - DEACTIVATED FOR NOW
        // fileMenu.addSeparator();
        // fileMenu.add(new NewQueryAction(false));

        fileMenu.addSeparator();
        fileMenu.add(new ExitProgramAction());
        add(fileMenu);

        JMenu viewMenu = new JMenu("View");
        viewMenu.setMnemonic(KeyEvent.VK_V);
        viewMenu.add(new CenterImageAction());
        viewMenu.add(new ToggleFullscreenAction());
        viewMenu.addSeparator();
        viewMenu.add(new ZoomInAction(true));
        viewMenu.add(new ZoomOutAction(true));
        viewMenu.add(new ZoomFitAction(true));
        viewMenu.add(new Zoom1to1Action(true));
        add(viewMenu);

        JMenu movieMenu = new JMenu("Movie");
        movieMenu.setMnemonic(KeyEvent.VK_A);
        movieMenu.add(new MoviePanel.StaticPlayPauseAction());
        movieMenu.add(new MoviePanel.StaticPreviousFrameAction());
        movieMenu.add(new MoviePanel.StaticNextFrameAction());
        add(movieMenu);

        JMenu pluginsMenu = new JMenu("Plugins");
        pluginsMenu.setMnemonic(KeyEvent.VK_I);
        pluginsMenu.add(new ShowDialogAction("Manage Plugins...", PluginDialog.class));
        pluginsMenu.add(new ShowDialogAction("Manage Filters...", FilterPluginDialog.class));
        pluginsMenu.add(new ShowDialogAction("Manage Overlays...", OverlayPluginDialog.class));
        add(pluginsMenu);

        JMenu optionsMenu = new JMenu("Options");
        optionsMenu.setMnemonic(KeyEvent.VK_O);
        optionsMenu.add(new ShowDialogAction("Preferences...", PreferencesDialog.class));
        add(optionsMenu);

        JMenu helpMenu = new JMenu("Help");
        helpMenu.setMnemonic(KeyEvent.VK_H);
        helpMenu.add(new ShowDialogAction("About JHelioviewer...", AboutDialog.class));
        helpMenu.add(new OpenURLinBrowserAction("Open Online Handbook", "http://www.helioviewer.org/wiki/index.php?title=JHelioviewer_Handbook"));
        helpMenu.add(new ShowDialogAction("Show Shortcuts...", HelpDialog.class));
        helpMenu.addSeparator();
        helpMenu.add(new OpenURLinBrowserAction("Report a Bug", "https://bugs.launchpad.net/jhelioviewer/+filebug"));
        helpMenu.add(new OpenURLinBrowserAction("Submit a Feature Request", "https://bugs.launchpad.net/jhelioviewer/+filebug"));
        helpMenu.addSeparator();
        helpMenu.add(new CheckUpdateAction());
        helpMenu.add(new OpenURLinBrowserAction("Open Website", "http://www.jhelioviewer.org"));
        add(helpMenu);
    }
}
