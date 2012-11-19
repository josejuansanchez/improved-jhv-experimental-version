package org.helioviewer.jhv.gui.controller;

import org.helioviewer.base.math.Vector2dDouble;
import org.helioviewer.base.math.Vector2dInt;
import org.helioviewer.jhv.gui.ImageViewerGui;
import org.helioviewer.jhv.gui.components.BasicImagePanel;
import org.helioviewer.jhv.layers.LayersModel;
import org.helioviewer.viewmodel.changeevent.ChangeEvent;
import org.helioviewer.viewmodel.metadata.ImageSizeMetaData;
import org.helioviewer.viewmodel.region.Region;
import org.helioviewer.viewmodel.region.StaticRegion;
import org.helioviewer.viewmodel.view.LayeredView;
import org.helioviewer.viewmodel.view.MetaDataView;
import org.helioviewer.viewmodel.view.RegionView;
import org.helioviewer.viewmodel.view.View;
import org.helioviewer.viewmodel.view.ViewHelper;
import org.helioviewer.viewmodel.view.ViewportView;
import org.helioviewer.viewmodel.view.jp2view.JHVJP2View;
import org.helioviewer.viewmodel.viewport.Viewport;
import org.helioviewer.viewmodel.viewportimagesize.ViewportImageSize;

/**
 * Collection of several zooming functions.
 * 
 * <p>
 * This class provides several zooming functions. The controller is used by
 * several classes, such as {@link org.helioviewer.jhv.gui.actions.ZoomInAction}, {@link org.helioviewer.jhv.gui.actions.ZoomOutAction},
 * {@link org.helioviewer.jhv.gui.actions.ZoomFitAction},
 * {@link org.helioviewer.jhv.gui.actions.Zoom1to1Action} and
 * {@link org.helioviewer.jhv.gui.controller.MainImagePanelMouseController}.
 */
public class ZoomController {

    private volatile View view;
    private volatile RegionView regionView;
    private volatile MetaDataView metaDataView;
    private volatile ViewportView viewportView;
    private volatile BasicImagePanel panel = null;

    private static final double zoomFactorStep = Math.pow(2, 1.0 / (4.0));

    /**
     * Returns the topmost view of the view chain associated with this
     * controller.
     * 
     * @return Topmost view of the view chain
     * @see #setView(View)
     */
    public View getView() {
        return view;
    }

    /**
     * Sets the topmost view of the view chain.
     * 
     * That way the controller can access the whole view chain.
     * 
     * @param newView
     *            Topmost view of the view chain
     * @see #getView()
     */
    public void setView(View newView) {
        view = newView;
        regionView = view == null ? null : view.getAdapter(RegionView.class);
        metaDataView = view == null ? null : view.getAdapter(MetaDataView.class);
        viewportView = view == null ? null : view.getAdapter(ViewportView.class);
    }

    /**
     * Sets the panel on which the zoom controller should operate. Can be used
     * to zoom to the current mouse position within the specified panel.
     * 
     * @param panel
     *            An ImagePanel
     */
    public void setImagePanel(BasicImagePanel panel) {
        this.panel = panel;
    }

    /**
     * Zooms in one step. A step mean scaling the current region of interest by
     * the square root of two
     */
    public void zoomIn() {
        zoom(zoomFactorStep);
    }

    /**
     * Zooms out one step. A step mean scaling the current region of interest by
     * the square root of two
     */
    public void zoomOut() {
        zoom(1.0 / zoomFactorStep);
    }

    /**
     * Zooms in or out the desired number of steps. A step mean scaling the
     * current region of interest by the square root of two. To zoom in, steps
     * has to be greater than zero, to zoom out it has to be lesser than zero.
     * 
     * @param steps
     *            Number of steps to zoom, the sign defines the direction.
     */
    public void zoomSteps(int steps) {
        zoom(Math.pow(zoomFactorStep, steps));
    }

    /**
     * Zooms by scaling the current region by the given zoom factor. Uses a
     * heuristic to avoid zooming out TOO much!
     * 
     * @param zoomFactor
     *            zoom factor to scale the current region with
     */
    public void zoom(double zoomFactor) {

        if (regionView != null && metaDataView != null && metaDataView.getMetaData() != null) {

            // if zooming out, make sure that we do not get off too far
            if (zoomFactor < 1) {

                LayeredView layeredView = regionView.getAdapter(LayeredView.class);

                // loop over all layers to check if all layers would be getting
                // too small
                boolean tooSmall = true;
                for (int i = 0; i < layeredView.getNumLayers(); ++i) {
                    View view = layeredView.getLayer(i);
                    if (getZoom(view) * zoomFactor > 0.005) {
                        tooSmall = false;
                        break;
                    }
                }
                if (tooSmall) {
                    return;
                }
            }
            Region oldRegion = regionView.getRegion();
            if (oldRegion == null) {
                return;
            }
            Vector2dDouble newSizeVector = Vector2dDouble.scale(oldRegion.getSize(), 1.0 / zoomFactor);

            Vector2dDouble newCorner = null;
            if (panel != null && viewportView != null) {
                Vector2dInt mousePosition = panel.getInputController().getMousePosition();
                if (mousePosition != null) {
                    Viewport v = viewportView.getViewport();
                    ViewportImageSize vis = ViewHelper.calculateViewportImageSize(v, oldRegion);
                    Vector2dInt visOffset = v.getSize().subtract(vis.getSizeVector()).scale(0.5);
                    Vector2dInt fixPointViewport = mousePosition.subtract(visOffset);
                    if (fixPointViewport.getX() >= 0 && fixPointViewport.getY() >= 0) {
                        Vector2dDouble fixPointOffset = ViewHelper.convertScreenToImageDisplacement(fixPointViewport.subtract(new Vector2dInt(0, vis.getHeight())), oldRegion, vis);
                        Vector2dDouble fixPoint = fixPointOffset.add(oldRegion.getLowerLeftCorner());
                        Vector2dDouble relativeFixPointOffset = fixPointOffset.invertedScale(oldRegion.getSize());
                        Vector2dDouble newFixPointOffset = newSizeVector.scale(relativeFixPointOffset);
                        newCorner = fixPoint.subtract(newFixPointOffset);
                    }
                }
            }

            if (newCorner == null) {
                newCorner = Vector2dDouble.add(oldRegion.getLowerLeftCorner(), Vector2dDouble.scale(Vector2dDouble.subtract(oldRegion.getSize(), newSizeVector), 0.5));
            }
            Region zoomedRegion = ViewHelper.cropRegionToImage(StaticRegion.createAdaptedRegion(newCorner, newSizeVector), metaDataView.getMetaData());

            regionView.setRegion(zoomedRegion, new ChangeEvent());
        }
    }

    public static double getZoom(View view) {

        Viewport viewport = view.getAdapter(ViewportView.class).getViewport();

        RegionView viewAdapter = view.getAdapter(RegionView.class);
        Region region = viewAdapter.getRegion();

        // JHVJP2View's getRegion method will only return the latest region that
        // has been fully downloaded. This workaround get's the newest region
        // which might not yet fully been downloaded yet
        if (viewAdapter instanceof JHVJP2View) {
            region = ((JHVJP2View) viewAdapter).getNewestRegion();
        }

        if (region == null || viewport == null) {
            return 1.0;
        }

        double unitsPerPixel = ((ImageSizeMetaData) view.getAdapter(MetaDataView.class).getMetaData()).getUnitsPerPixel();
        Vector2dInt actualSize = ViewHelper.calculateViewportImageSize(viewport, region).getSizeVector();
        double zoom = actualSize.getX() * unitsPerPixel / region.getSize().getX();
        return zoom;

    }

    /**
     * Zooms the image in such a way, that the active layer is displayed in its
     * native resolution.
     */
    public void zoom1to1() {
        View view = LayersModel.getSingletonInstance().getActiveView();
        if (view != null) {
            zoom(1.0 / getZoom(view));
        }
    }

    /**
     * Zooms the image in such a way, that the active layer fits exactly into
     * the viewport.
     */
    public void zoomFit() {
        View view = LayersModel.getSingletonInstance().getActiveView();
        if (view != null) {
            Region region = view.getAdapter(MetaDataView.class).getMetaData().getPhysicalRegion();
            Vector2dDouble size = region.getSize();
            Vector2dDouble lowerLeft = region.getLowerLeftCorner();
            region = StaticRegion.createAdaptedRegion(lowerLeft, size);
            ImageViewerGui.getSingletonInstance().getMainView().getAdapter(RegionView.class).setRegion(region, new ChangeEvent());
        }
    }

}
