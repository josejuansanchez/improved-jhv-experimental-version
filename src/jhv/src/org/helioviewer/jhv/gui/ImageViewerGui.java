package org.helioviewer.jhv.gui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.AbstractList;

import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JMenuBar;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;

import org.helioviewer.base.logging.Log;
import org.helioviewer.base.message.Message;
import org.helioviewer.jhv.JHVSplashScreen;
import org.helioviewer.jhv.gui.actions.ExitProgramAction;
import org.helioviewer.jhv.gui.components.ControlPanelContainer;
import org.helioviewer.jhv.gui.components.ImageSelectorPanel;
import org.helioviewer.jhv.gui.components.MainImagePanel;
import org.helioviewer.jhv.gui.components.MenuBar;
import org.helioviewer.jhv.gui.components.MoviePanel;
import org.helioviewer.jhv.gui.components.OverviewImagePanel;
import org.helioviewer.jhv.gui.components.QualitySpinner;
import org.helioviewer.jhv.gui.components.SideContentPane;
import org.helioviewer.jhv.gui.components.StatusPanel;
import org.helioviewer.jhv.gui.components.TopToolBar;
import org.helioviewer.jhv.gui.components.statusplugins.FramerateStatusPanel;
import org.helioviewer.jhv.gui.components.statusplugins.JPIPStatusPanel;
import org.helioviewer.jhv.gui.components.statusplugins.MetaDataStatusPanel;
import org.helioviewer.jhv.gui.components.statusplugins.PositionStatusPanel;
import org.helioviewer.jhv.gui.components.statusplugins.QualityStatusPanel;
import org.helioviewer.jhv.gui.components.statusplugins.RenderModeStatusPanel;
import org.helioviewer.jhv.gui.components.statusplugins.ZoomStatusPanel;
import org.helioviewer.jhv.gui.controller.MainImagePanelMousePanController;
import org.helioviewer.jhv.gui.controller.ZoomController;
import org.helioviewer.jhv.internal_plugins.filter.SOHOLUTFilterPlugin.SOHOLUTPanel;
import org.helioviewer.jhv.internal_plugins.filter.channelMixer.ChannelMixerPanel;
import org.helioviewer.jhv.internal_plugins.filter.contrast.ContrastPanel;
import org.helioviewer.jhv.internal_plugins.filter.gammacorrection.GammaCorrectionPanel;
import org.helioviewer.jhv.internal_plugins.filter.opacity.OpacityFilter;
import org.helioviewer.jhv.internal_plugins.filter.opacity.OpacityPanel;
import org.helioviewer.jhv.internal_plugins.filter.sharpen.SharpenPanel;
import org.helioviewer.jhv.internal_plugins.selectedLayer.SelectedLayerPanel;
import org.helioviewer.jhv.io.APIRequestManager;
import org.helioviewer.jhv.io.CommandLineProcessor;
import org.helioviewer.jhv.io.FileDownloader;
import org.helioviewer.jhv.io.JHVRequest;
import org.helioviewer.viewmodel.metadata.ImageSizeMetaData;
import org.helioviewer.viewmodel.view.ComponentView;
import org.helioviewer.viewmodel.view.FilterView;
import org.helioviewer.viewmodel.view.ImageInfoView;
import org.helioviewer.viewmodel.view.LayeredView;
import org.helioviewer.viewmodel.view.MetaDataView;
import org.helioviewer.viewmodel.view.MovieView;
import org.helioviewer.viewmodel.view.SynchronizeView;
import org.helioviewer.viewmodel.view.View;
import org.helioviewer.viewmodelplugin.filter.FilterTabPanelManager;

/**
 * A class that sets up the graphical user interface.
 * 
 * @author caplins
 * @author Benjamin Wamsler
 * @author Alen Agheksanterian
 * @author Stephan Pagel
 * @author Markus Langenberg
 * @author Andre Dau
 * 
 */
public class ImageViewerGui {

    /** The sole instance of this class. */
    private static final ImageViewerGui singletonImageViewer = new ImageViewerGui();

    private ComponentView mainComponentView;
    private ComponentView overviewComponentView;

    private static JFrame mainFrame;
    private JPanel contentPanel;
    private JScrollPane leftScrollPane;

    private TopToolBar topToolBar;
    private MainImagePanel mainImagePanel;
    private OverviewImagePanel overviewImagePanel;
    private SideContentPane leftPane;
    private RenderModeStatusPanel renderModeStatus;
    private ImageSelectorPanel imageSelectorPanel;
    private ControlPanelContainer moviePanelContainer;
    private ControlPanelContainer filterPanelContainer;
    private JMenuBar menuBar;

    // private SolarEventCatalogsPanel solarEventCatalogsPanel;

    public static final int SIDE_PANEL_WIDTH = 320;
    public static final int SIDE_PADDING = 10;

    /**
     * The private constructor that creates and positions all the gui
     * components.
     */
    private ImageViewerGui() {
        mainFrame = createMainFrame();

        contentPanel = new JPanel(new BorderLayout());

        menuBar = new MenuBar();
        menuBar.setFocusable(false);

        mainFrame.setContentPane(contentPanel);
        mainFrame.setJMenuBar(menuBar);
        mainFrame.setFocusable(true);

        topToolBar = new TopToolBar();
        contentPanel.add(topToolBar, BorderLayout.PAGE_START);

        // ////////////////////////////////////////////////////////////////////////////////
        // MAIN IMAGE PANEL
        // ////////////////////////////////////////////////////////////////////////////////

        // set up main image panel
        mainImagePanel = new MainImagePanel();
        mainImagePanel.setAutoscrolls(true);
        mainImagePanel.setFocusable(false);

        mainImagePanel.setInputController(new MainImagePanelMousePanController());
        // TODO HEK mainImagePanel.addPlugin(new
        // ImagePanelEventPopupController());

        contentPanel.add(mainImagePanel, BorderLayout.CENTER);

        // ////////////////////////////////////////////////////////////////////////////////
        // LEFT CONTROL PANEL
        // ////////////////////////////////////////////////////////////////////////////////

        leftPane = new SideContentPane();

        // create overview image panel instance
        overviewImagePanel = new OverviewImagePanel();

        // set up the overview image panel
        overviewImagePanel.setAutoscrolls(true);
        overviewImagePanel.setPreferredSize(new Dimension(175, 175));
        overviewImagePanel.setFocusable(false);

        JPanel overviewImagePanelContainer = new JPanel();
        overviewImagePanelContainer.setLayout(new BoxLayout(overviewImagePanelContainer, BoxLayout.Y_AXIS));
        overviewImagePanelContainer.add(overviewImagePanel);

        leftPane.add("Overview", overviewImagePanelContainer, true);

        // Movie control
        moviePanelContainer = new ControlPanelContainer();
        moviePanelContainer.setDefaultPanel(new MoviePanel());

        leftPane.add("Movie Controls", moviePanelContainer, true);

        // Layer control
        imageSelectorPanel = new ImageSelectorPanel();

        leftPane.add("Layers", imageSelectorPanel, true);

        // Image adjustments and filters
        FilterTabPanelManager compactPanelManager = new FilterTabPanelManager();
        compactPanelManager.add(new OpacityPanel());
        compactPanelManager.add(new QualitySpinner(null));
        compactPanelManager.add(new SOHOLUTPanel());
        compactPanelManager.add(new SelectedLayerPanel(null));
        compactPanelManager.add(new GammaCorrectionPanel());
        compactPanelManager.add(new ContrastPanel());
        compactPanelManager.add(new SharpenPanel());
        compactPanelManager.add(new ChannelMixerPanel());

        JPanel compactPanel = compactPanelManager.createCompactPanel();

        JTabbedPane tab = new JTabbedPane();
        tab.addTab("Internal Plugins", compactPanel);
        tab.setEnabled(false);
        compactPanel.setEnabled(false);

        filterPanelContainer = new ControlPanelContainer();
        filterPanelContainer.setDefaultPanel(tab);
        leftPane.add("Adjustments", filterPanelContainer, false);

        // // FEATURES / EVENTS
        // solarEventCatalogsPanel = new SolarEventCatalogsPanel();
        // leftPane.add("Features/Events", solarEventCatalogsPanel, false);

        JPanel leftPanelContainer = new JPanel();
        leftPanelContainer.add(leftPane);

        leftScrollPane = new JScrollPane(leftPanelContainer, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        leftScrollPane.setFocusable(false);
        leftScrollPane.getVerticalScrollBar().setUnitIncrement(10);

        contentPanel.add(leftScrollPane, BorderLayout.WEST);

        // ///////////////////////////////////////////////////////////////////////////////
        // STATUS PANEL
        // ///////////////////////////////////////////////////////////////////////////////
        ZoomStatusPanel zoomStatusPanel = new ZoomStatusPanel();
        QualityStatusPanel qualityStatusPanel = new QualityStatusPanel();
        FramerateStatusPanel framerateStatus = new FramerateStatusPanel();
        PositionStatusPanel positionStatusPanel = new PositionStatusPanel(mainImagePanel);
        MetaDataStatusPanel jhvXMLStatusPanel = new MetaDataStatusPanel();
        renderModeStatus = new RenderModeStatusPanel();
        JPIPStatusPanel jpipStatusPanel = new JPIPStatusPanel();

        StatusPanel statusPanel = new StatusPanel(SIDE_PANEL_WIDTH + 20, 5);
        statusPanel.addPlugin(zoomStatusPanel, StatusPanel.Alignment.LEFT);
        statusPanel.addPlugin(qualityStatusPanel, StatusPanel.Alignment.LEFT);
        statusPanel.addPlugin(framerateStatus, StatusPanel.Alignment.LEFT);
        statusPanel.addPlugin(renderModeStatus, StatusPanel.Alignment.RIGHT);
        statusPanel.addPlugin(jhvXMLStatusPanel, StatusPanel.Alignment.RIGHT);
        statusPanel.addPlugin(jpipStatusPanel, StatusPanel.Alignment.RIGHT);
        statusPanel.addPlugin(positionStatusPanel, StatusPanel.Alignment.RIGHT);
        contentPanel.add(statusPanel, BorderLayout.PAGE_END);
    }

    /**
     * Packs, positions and shows the GUI
     * 
     * @param _show
     *            If GUI should be displayed.
     */
    public void packAndShow(final boolean _show) {

        final JHVSplashScreen splash = JHVSplashScreen.getSingletonInstance();

        // load images which should be displayed first in a separated thread
        // that splash screen will be updated
        splash.setProgressText("Loading Images...");

        Thread thread = new Thread(new Runnable() {

            public void run() {

                loadImagesAtStartup();

                // show GUI
                splash.setProgressText("Starting JHelioviewer...");
                splash.nextStep();
                mainFrame.pack();
                mainFrame.setLocationRelativeTo(null);
                mainFrame.setVisible(_show);
                splash.setProgressValue(100);

                // remove splash screen
                splash.dispose();

            }
        }, "LoadImagesOnStartUp");

        thread.start();
    }

    public boolean viewchainCreated() {
        return mainComponentView != null;
    }

    /**
     * Initializes the main and overview view chain.
     */
    public void createViewchains() {
        Log.info("Start creating view chains");

        boolean firstTime = (mainComponentView == null);

        renderModeStatus.updateStatus();

        // Create main view chain
        ViewchainFactory mainFactory = new ViewchainFactory();
        mainComponentView = mainFactory.createViewchainMain(mainComponentView, false);
        if (mainComponentView != null) {
            mainImagePanel.setView(mainComponentView);
        }

        // create overview view chain
        if (firstTime) {

            ViewchainFactory overviewFactory = new ViewchainFactory(true);
            overviewComponentView = overviewFactory.createViewchainOverview(mainComponentView, overviewComponentView, false);

            // Connect Viewchain to GUI
            if (overviewComponentView != null) {
                overviewImagePanel.setView(overviewComponentView);
            }
        } else {
            overviewComponentView.getAdapter(SynchronizeView.class).setObservedView(mainComponentView);
        }

        ViewListenerDistributor.getSingletonInstance().setView(mainComponentView);
        // imageSelectorPanel.setLayeredView(mainComponentView.getAdapter(LayeredView.class));

        if (firstTime) {
            packAndShow(true);
        } else {
            mainFrame.validate();
        }
    }

    /**
     * Loads the images which have to be displayed when the program starts.
     * 
     * If there are any images defined in the command line, than this messages
     * tries to load this images. Otherwise it tries to load a default image
     * which is defined by the default entries of the observation panel.
     * */
    private void loadImagesAtStartup() {
        // get values for different command line options
        AbstractList<JHVRequest> jhvRequests = CommandLineProcessor.getJHVOptionValues();
        AbstractList<URI> jpipUris = CommandLineProcessor.getJPIPOptionValues();
        AbstractList<URI> downloadAddresses = CommandLineProcessor.getDownloadOptionValues();
        AbstractList<URL> jpxUrls = CommandLineProcessor.getJPXOptionValues();

        // Do nothing if no resource is specified
        if (jhvRequests.isEmpty() && jpipUris.isEmpty() && downloadAddresses.isEmpty() && jpxUrls.isEmpty()) {
            return;
        }

        // //////////////////////
        // -jhv
        // //////////////////////

        // go through all jhv values
        for (JHVRequest jhvRequest : jhvRequests) {
            try {
                for (int layer = 0; layer < jhvRequest.imageLayers.length; ++layer) {
                    // load image and memorize corresponding view
                    ImageInfoView imageInfoView = APIRequestManager.requestAndOpenRemoteFile(jhvRequest.cadence, jhvRequest.startTime, jhvRequest.endTime, jhvRequest.imageLayers[layer].observatory, jhvRequest.imageLayers[layer].instrument, jhvRequest.imageLayers[layer].detector, jhvRequest.imageLayers[layer].measurement);
                    if (imageInfoView != null && mainComponentView != null) {
                        // get the layered view
                        LayeredView layeredView = mainComponentView.getAdapter(LayeredView.class);

                        // go through all sub view chains of the layered
                        // view and try to find the
                        // view chain of the corresponding image info view
                        for (int i = 0; i < layeredView.getNumLayers(); i++) {
                            View subView = layeredView.getLayer(i);

                            // if view has been found
                            if (imageInfoView.equals(subView.getAdapter(ImageInfoView.class))) {

                                // Set the correct image scale
                                ImageSizeMetaData imageSizeMetaData = (ImageSizeMetaData) imageInfoView.getAdapter(MetaDataView.class).getMetaData();
                                ZoomController zoomController = new ZoomController();
                                zoomController.setView(ImageViewerGui.getSingletonInstance().getMainView());
                                zoomController.zoom(imageSizeMetaData.getUnitsPerPixel() / (jhvRequest.imageScale * 1000.0));

                                // Lock movie
                                if (jhvRequest.linked) {
                                    MovieView movieView = subView.getAdapter(MovieView.class);
                                    if (movieView != null && movieView.getMaximumFrameNumber() > 0) {
                                        MoviePanel moviePanel = MoviePanel.getMoviePanel(movieView);
                                        if (moviePanel == null)
                                            throw new InvalidViewException();
                                        moviePanel.setMovieLink(true);
                                    }
                                }

                                // opacity

                                // find opacity filter view
                                FilterView filterView = subView.getAdapter(FilterView.class);

                                while (filterView != null) {

                                    // if opacity filter has been found set
                                    // opacity value
                                    if (filterView.getFilter() instanceof OpacityFilter) {
                                        ((OpacityFilter) (filterView.getFilter())).setState(Float.toString(jhvRequest.imageLayers[layer].opacity / 100.0f));
                                        break;
                                    }

                                    // find next filter view
                                    View view = filterView.getView();

                                    if (view == null)
                                        filterView = null;
                                    else
                                        filterView = view.getAdapter(FilterView.class);
                                }

                                break;
                            }
                        }
                    }
                }
            } catch (MalformedURLException e) {
                e.printStackTrace();
            } catch (IOException e) {
                Message.err("An error occured while opening the remote file!", e.getMessage(), false);
            } catch (NumberFormatException e) {
                e.printStackTrace();
            } catch (InvalidViewException e) {
                e.printStackTrace();
            }

        }

        // //////////////////////
        // -jpx
        // //////////////////////

        for (URL jpxUrl : jpxUrls) {
            if (jpxUrl != null) {
                try {
                    ImageInfoView imageInfoView = APIRequestManager.requestData(true, jpxUrl, null);
                    if (imageInfoView != null && mainComponentView != null) {
                        // get the layered view
                        LayeredView layeredView = mainComponentView.getAdapter(LayeredView.class);

                        // go through all sub view chains of the layered
                        // view and try to find the
                        // view chain of the corresponding image info view
                        for (int i = 0; i < layeredView.getNumLayers(); i++) {
                            View subView = layeredView.getLayer(i);

                            // if view has been found
                            if (imageInfoView.equals(subView.getAdapter(ImageInfoView.class))) {
                                MovieView movieView = subView.getAdapter(MovieView.class);
                                MoviePanel moviePanel = MoviePanel.getMoviePanel(movieView);
                                if (moviePanel == null)
                                    throw new InvalidViewException();
                                moviePanel.setMovieLink(true);
                                break;
                            }
                        }
                    }
                } catch (IOException e) {
                    Message.err("An error occured while opening the remote file!", e.getMessage(), false);
                } catch (InvalidViewException e) {
                    e.printStackTrace();
                }
            }
        }
        // //////////////////////
        // -jpip
        // //////////////////////
        for (URI jpipUri : jpipUris) {
            if (jpipUri != null) {
                try {
                    APIRequestManager.newLoad(jpipUri, true);
                } catch (IOException e) {
                    Message.err("An error occured while opening the remote file!", e.getMessage(), false);
                }
            }
        }
        // //////////////////////
        // -download
        // //////////////////////
        for (URI downloadAddress : downloadAddresses) {
            if (downloadAddress != null) {
                try {
                    FileDownloader fileDownloader = new FileDownloader();
                    File downloadFile = fileDownloader.getDefaultDownloadLocation(downloadAddress);
                    fileDownloader.get(downloadAddress, downloadFile);
                    APIRequestManager.newLoad(downloadFile.toURI(), true);
                } catch (IOException e) {
                    Message.err("An error occured while opening the remote file!", e.getMessage(), false);
                }
            }
        }
    }

    /**
     * Method that creates and initializes the main JFrame.
     * 
     * @return the created and initialized main frame.
     */
    private JFrame createMainFrame() {
        JFrame frame = new JFrame("ESA JHelioviewer");

        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent arg0) {
                ExitProgramAction exitAction = new ExitProgramAction();
                exitAction.actionPerformed(new ActionEvent(this, 0, ""));
            }
        });

        Dimension maxSize = Toolkit.getDefaultToolkit().getScreenSize();
        Dimension minSize = new Dimension(800, 600);

        maxSize.width -= 200;
        // if the display is not very high, we want to take most of the height,
        // as the rest is not useful anyway
        if (maxSize.height < 1000) {
            maxSize.height -= 100;
        } else {
            maxSize.height -= 150;
        }

        minSize.width = Math.min(minSize.width, maxSize.width);
        minSize.height = Math.min(minSize.height, maxSize.height);
        frame.setMaximumSize(maxSize);
        frame.setMinimumSize(minSize);
        frame.setPreferredSize(maxSize);
        frame.setFont(new Font("SansSerif", Font.BOLD, 12));
        return frame;
    }

    /**
     * Returns the only instance of this class.
     * 
     * @return the only instance of this class.
     * */
    public static ImageViewerGui getSingletonInstance() {
        return singletonImageViewer;
    }

    /**
     * Returns the main frame.
     * 
     * @return the main frame.
     * */
    public static JFrame getMainFrame() {
        return mainFrame;
    }

    /**
     * Returns instance of the main image panel.
     * 
     * @return instance of the main image panel.
     * */
    public MainImagePanel getMainImagePanel() {
        return mainImagePanel;
    }

    /**
     * Returns instance of the main ComponentView.
     * 
     * @return instance of the main ComponentView.
     */
    public ComponentView getMainView() {
        return mainComponentView;
    }

    /**
     * Returns instance of the overview ComponentView.
     * 
     * @return instance of the overview ComponentView.
     */
    public ComponentView getOverviewView() {
        return overviewComponentView;
    }

    /**
     * Returns the scrollpane containing the left content pane.
     * 
     * @return instance of the scrollpane containing the left content pane.
     * */
    public JScrollPane getLeftScrollPane() {
        return leftScrollPane;
    }

    /**
     * Returns the left content pane.
     * 
     * @return instance of the left content pane.
     * */
    public SideContentPane getLeftContentPane() {
        return leftPane;
    }

    /**
     * Returns the instance of the ImageSelectorPanel.
     * 
     * @return instance of the image selector panel.
     * */
    public ImageSelectorPanel getImageSelectorPanel() {
        return imageSelectorPanel;
    }

    /**
     * @return the movie panel container
     */
    public ControlPanelContainer getMoviePanelContainer() {
        return moviePanelContainer;
    }

    /**
     * @return the filter panel container
     */
    public ControlPanelContainer getFilterPanelContainer() {
        return filterPanelContainer;
    }

    /**
     * @return the menu bar of jhv
     */
    public JMenuBar getMenuBar() {
        return menuBar;
    }

    /**
     * Returns the top tool bar instance.
     * 
     * @return instance of the top tool bar.
     * */
    public TopToolBar getTopToolBar() {
        return topToolBar;
    }

    /**
     * Calls the update methods of sub components of the main frame so they can
     * e.g. reload data.
     */
    public void updateComponents() {
        renderModeStatus.updateStatus();
    }

    /**
     * Toggles the visibility of the control panel on the left side.
     */
    public void toggleShowSidePanel() {
        leftScrollPane.setVisible(!leftScrollPane.isVisible());
        contentPanel.revalidate();
    }

    /**
     * Returns the content panel of JHV
     * 
     * @return The content panel of JHV
     */
    public JPanel getContentPane() {
        return contentPanel;
    }
}
