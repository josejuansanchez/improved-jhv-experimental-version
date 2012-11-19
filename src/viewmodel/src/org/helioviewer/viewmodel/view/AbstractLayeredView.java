package org.helioviewer.viewmodel.view;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.concurrent.locks.ReentrantLock;

import org.helioviewer.base.math.RectangleDouble;
import org.helioviewer.base.math.Vector2dInt;
import org.helioviewer.viewmodel.changeevent.ChangeEvent;
import org.helioviewer.viewmodel.changeevent.LayerChangedReason;
import org.helioviewer.viewmodel.changeevent.NonConstantMetaDataChangedReason;
import org.helioviewer.viewmodel.changeevent.RegionChangedReason;
import org.helioviewer.viewmodel.changeevent.RegionUpdatedReason;
import org.helioviewer.viewmodel.changeevent.SubImageDataChangedReason;
import org.helioviewer.viewmodel.changeevent.ViewChainChangedReason;
import org.helioviewer.viewmodel.changeevent.LayerChangedReason.LayerChangeType;
import org.helioviewer.viewmodel.imagedata.ImageData;
import org.helioviewer.viewmodel.metadata.ImageSizeMetaData;
import org.helioviewer.viewmodel.metadata.MetaData;
import org.helioviewer.viewmodel.metadata.PixelBasedMetaData;
import org.helioviewer.viewmodel.region.Region;
import org.helioviewer.viewmodel.region.StaticRegion;
import org.helioviewer.viewmodel.view.jp2view.JHVJP2View;
import org.helioviewer.viewmodel.viewport.Viewport;
import org.helioviewer.viewmodel.viewportimagesize.ViewportImageSize;

/**
 * Abstract base class implementing LayeredView, providing some common
 * functions.
 * 
 * <p>
 * This class provides most of the functionality of a LayeredView, since most of
 * its behavior is independent from the render mode. Because of that, the whole
 * management of the stack of layers is centralized in this abstract class.
 * <p>
 * To improve performance, many intermediate results are cached.
 * <p>
 * For further informations about how to use layers, see {@link LayeredView}.
 * 
 * @author Ludwig Schmidt
 * @author Markus Langenberg
 * 
 */
public abstract class AbstractLayeredView extends AbstractView implements LayeredView, RegionView, ViewportView, MetaDataView, ViewListener {

    // /////////////////////////////////////////////////////////////////////////
    // Definitions
    // /////////////////////////////////////////////////////////////////////////

    protected LinkedList<View> layers = new LinkedList<View>();
    protected ReentrantLock layerLock = new ReentrantLock();
    protected HashMap<View, Layer> viewLookup = new HashMap<View, Layer>();
    protected Viewport viewport;
    protected Region region;
    protected MetaData metaData;
    protected ViewportImageSize viewportImageSize;
    protected double minimalRegionSize;

    // /////////////////////////////////////////////////////////////////////////
    // Methods
    // /////////////////////////////////////////////////////////////////////////

    /**
     * Buffer for precomputed values for each layer.
     * 
     * <p>
     * This container saves some values per layer, such as its visibility and
     * precomputed view adapters.
     * 
     */
    public class Layer {
        public View view;

        public RegionView regionView;
        public ViewportView viewportView;
        public MetaDataView metaDataView;
        public SubimageDataView subimageDataView;

        public ImageData imageData;

        public Vector2dInt renderOffset;
        public boolean visibility = true;
        private boolean needsRedraw = false;

        public synchronized ImageData getSubimage() {
            needsRedraw = false;
            if (subimageDataView != null) {
                ImageData newData = subimageDataView.getSubimageData(true);
                if (newData != null && newData != imageData) {
                    needsRedraw = true;
                    imageData = newData;
                }
                return imageData;
            }
            return null;
        }

        public synchronized void markAsDrawn() {
            needsRedraw = false;
        }

        public synchronized boolean needsRedraw() {
            return needsRedraw;
        }

        /**
         * Default constructor.
         * 
         * Computes view adapters for this layer.
         * 
         * @param base
         *            layer to save
         */
        public Layer(View base) {
            view = base;
            update();
        }

        /**
         * Recalculates the view adapters, in case the view chain has changed.
         */
        public void update() {
            if (view != null) {
                regionView = view.getAdapter(RegionView.class);
                viewportView = view.getAdapter(ViewportView.class);
                metaDataView = view.getAdapter(MetaDataView.class);
                subimageDataView = view.getAdapter(SubimageDataView.class);
            }
            imageData = null;
        }
    }

    /**
     * {@inheritDoc}
     */
    public boolean isVisible(View _view) {
        if (viewLookup.get(_view) != null)
            return viewLookup.get(_view).visibility;
        else
            return false;
    }

    /**
     * {@inheritDoc}
     */
    public int getNumberOfVisibleLayer() {
        int result = 0;
        layerLock.lock();
        try {
            Collection<Layer> values = viewLookup.values();
            for (Layer l : values) {
                if (l.visibility)
                    result++;
            }
        } finally {
            layerLock.unlock();
        }

        return result;
    }

    /**
     * {@inheritDoc}
     */
    public void toggleVisibility(View view) {
        LinkedMovieManager.getActiveInstance().pauseLinkedMovies();

        Layer layer = viewLookup.get(view);

        if (layer != null) {
            layer.visibility = !layer.visibility;

            redrawBuffer(new ChangeEvent(new LayerChangedReason(this, LayerChangeType.LAYER_VISIBILITY, view)));
        }
    }

    /**
     * {@inheritDoc}
     */
    public void addLayer(View newLayer) {
        addLayer(newLayer, layers.size());
    }

    /**
     * {@inheritDoc}
     */
    public void addLayer(View newLayer, int newIndex) {
        if (newLayer == null) {
            return;
        }
        LinkedMovieManager.getActiveInstance().pauseLinkedMovies();

        ChangeEvent changeEvent = new ChangeEvent(new LayerChangedReason(this, LayerChangeType.LAYER_ADDED, newLayer));

        layerLock.lock();
        try {
            layers.add(newIndex, newLayer);
            newLayer.addViewListener(this);

            viewLookup.put(newLayer, new Layer(newLayer));

            if (layers.size() > 1) {
                recalculateMetaData(false);
                for (Layer layer : viewLookup.values()) {
                    MetaData m = layer.metaDataView.getMetaData();
                    if (m instanceof PixelBasedMetaData) {
                        PixelBasedMetaData p = (PixelBasedMetaData) m;
                        p.updatePhysicalRegion(metaData.getPhysicalRegion());
                    }
                }
            }
            recalculateMetaData();
            region = metaData.getPhysicalRegion();
            if (viewport != null)
                region = ViewHelper.expandRegionToViewportAspectRatio(viewport, region, metaData);
            recalculateRegionsAndViewports(new ChangeEvent());
        } finally {
            layerLock.unlock();
        }
        redrawBuffer(changeEvent);
    }

    /**
     * {@inheritDoc}
     */
    public View getLayer(int index) {
        return layers.get(index);
    }

    /**
     * {@inheritDoc}
     */
    public int getNumLayers() {
        return layers.size();
    }

    /**
     * {@inheritDoc}
     */
    public int getLayerLevel(View view) {
        return layers.indexOf(view);
    }

    /**
     * {@inheritDoc}
     */
    public void removeLayer(View view) {

        if (view == null) {
            return;
        }

        int index = layers.indexOf(view);
        if (index == -1) {
            return;
        }
        LinkedMovieManager.getActiveInstance().pauseLinkedMovies();
        layerLock.lock();
        try {
            layers.remove(view);
            viewLookup.remove(view);
            view.removeViewListener(this);
        } finally {
            layerLock.unlock();
        }
        if (view.getAdapter(JHVJP2View.class) != null) {
            view.getAdapter(JHVJP2View.class).abolish();
        }

        ChangeEvent event = new ChangeEvent(new LayerChangedReason(this, LayerChangeType.LAYER_REMOVED, view, index));

        recalculateMetaData(false);
        if (metaData != null) {
            for (Layer layer : viewLookup.values()) {
                MetaData m = layer.metaDataView.getMetaData();
                if (m instanceof PixelBasedMetaData) {
                    PixelBasedMetaData p = (PixelBasedMetaData) m;
                    p.updatePhysicalRegion(metaData.getPhysicalRegion());
                }
            }
        }
        recalculateMetaData();
        if (metaData != null) {
            Region bound = metaData.getPhysicalRegion();
            double lowerLeftX = Math.max(bound.getCornerX(), region.getCornerX());
            double lowerLeftY = Math.max(bound.getCornerY(), region.getCornerY());
            Region newRegion = ViewHelper.cropInnerRegionToOuterRegion(StaticRegion.createAdaptedRegion(lowerLeftX, lowerLeftY, region.getSize()), bound);
            setRegion(newRegion, event);
        } else {
            recalculateRegionsAndViewports(event);
        }
        redrawBuffer(event);
    }

    /**
     * {@inheritDoc}
     */
    public void removeLayer(int index) {
        removeLayer(layers.get(index));
    }

    /**
     * {@inheritDoc}
     */
    public void removeAllLayers() {

        ChangeEvent event = new ChangeEvent();
        LinkedMovieManager.getActiveInstance().pauseLinkedMovies();
        layerLock.lock();
        try {
            for (View view : layers) {
                int index = layers.indexOf(view);
                view.removeViewListener(this);
                if (view.getAdapter(JHVJP2View.class) != null) {
                    view.getAdapter(JHVJP2View.class).abolish();
                }
                event.addReason(new LayerChangedReason(this, LayerChangeType.LAYER_REMOVED, view, index));
            }
            layers.clear();
            viewLookup.clear();
        } finally {
            layerLock.unlock();
        }
        recalculateMetaData();
        recalculateRegionsAndViewports(event);
        redrawBuffer(event);
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings(value = { "unchecked" })
    public <T extends View> T getAdapter(Class<T> c) {
        if (c.isInstance(this)) {
            return (T) this;
        } else {
            return null;
        }
    }

    /**
     * {@inheritDoc}
     */
    public Region getRegion() {
        return region;
    }

    /**
     * {@inheritDoc}
     */
    public boolean setRegion(Region r, ChangeEvent event) {

        if (event == null) {
            event = new ChangeEvent(new RegionUpdatedReason(this, r));
        } else {
            event.addReason(new RegionUpdatedReason(this, r));
        }

        // check if region is valid
        if (region == null || r == null || r.getWidth() < minimalRegionSize || r.getHeight() < minimalRegionSize) {
            notifyViewListeners(event);
            return false;
        }
        // check if region to small or viewport
        r = ViewHelper.expandRegionToViewportAspectRatio(viewport, r, metaData);

        // check if region has changed
        if (region.getCornerX() == r.getCornerX() && region.getCornerY() == r.getCornerY() && region.getWidth() == r.getWidth() && region.getHeight() == r.getHeight()) {
            notifyViewListeners(event);
            return false;
        }

        event.addReason(new RegionChangedReason(this, r));

        region = r;
        recalculateRegionsAndViewports(event);
        redrawBuffer(event);
        return true;
    }

    /**
     * {@inheritDoc}
     */
    public Viewport getViewport() {
        return viewport;
    }

    /**
     * {@inheritDoc}
     */
    public boolean setViewport(Viewport v, ChangeEvent event) {

        boolean changed = v != null && (viewport == null || viewport.getWidth() != v.getWidth() || viewport.getHeight() != v.getHeight());

        if (!changed) {
            return false;
        }
        viewport = v;
        recalculateRegionsAndViewports(event);
        redrawBuffer(event);

        return true;
    }

    /**
     * {@inheritDoc}
     */
    public MetaData getMetaData() {
        return metaData;
    }

    /**
     * Recalculates the regions and viewports of all layers.
     * 
     * <p>
     * Sets the regions and viewports of all layers according to the region and
     * viewport of the LayeredView. Also, calculates the offset of the layers to
     * each other.
     * 
     * @param event
     *            ChangeEvent to collect history of all following changes
     * @return true, if at least one region or viewport changed
     */
    protected boolean recalculateRegionsAndViewports(ChangeEvent event) {
        return recalculateRegionsAndViewports(event, true);
    }

    /**
     * Recalculates the regions and viewports of all layers.
     * 
     * <p>
     * Sets the regions and viewports of all layers according to the region and
     * viewport of the LayeredView. Also, calculates the offset of the layers to
     * each other.
     * 
     * @param event
     *            ChangeEvent to collect history of all following changes
     * @return true, if at least one region or viewport changed
     */
    protected boolean recalculateRegionsAndViewports(ChangeEvent event, boolean includePixelBasedImages) {

        boolean changed = false;
        // check region and viewport
        if (region == null && metaData != null) {
            region = StaticRegion.createAdaptedRegion(metaData.getPhysicalRectangle());
        }

        // if (viewport == null)
        // viewport = StaticViewport.createAdaptedViewport(100, 100);

        if (viewport != null && region != null) {
            ViewportImageSize oldViewportImageSize = viewportImageSize;
            viewportImageSize = ViewHelper.calculateViewportImageSize(viewport, region);
            changed |= viewportImageSize == null ? oldViewportImageSize == null : viewportImageSize.equals(oldViewportImageSize);
            layerLock.lock();
            try {
                for (Layer layer : viewLookup.values()) {
                    MetaData m = layer.metaDataView.getMetaData();
                    if (includePixelBasedImages || !(m instanceof PixelBasedMetaData)) {

                        Region layerRegion = ViewHelper.cropInnerRegionToOuterRegion(m.getPhysicalRegion(), region);
                        Viewport layerViewport = ViewHelper.calculateInnerViewport(layerRegion, region, viewportImageSize);
                        layer.renderOffset = ViewHelper.calculateInnerViewportOffset(layerRegion, region, viewportImageSize);
                        changed |= layer.regionView.setRegion(layerRegion, event);
                        changed |= layer.viewportView.setViewport(layerViewport, event);
                    }
                }
            } finally {
                layerLock.unlock();
            }
        }
        return changed;
    }

    /**
     * {@inheritDoc}
     */
    public void viewChanged(View sender, ChangeEvent aEvent) {
        if (aEvent.reasonOccurred(ViewChainChangedReason.class)) {
            for (Layer layer : viewLookup.values()) {
                layer.update();
            }
        }
        if (aEvent.reasonOccurred(NonConstantMetaDataChangedReason.class)) {
            recalculateMetaData();
            region = ViewHelper.cropRegionToImage(region, metaData);
            recalculateRegionsAndViewports(new ChangeEvent(aEvent));
        }

        if ((aEvent.reasonOccurred(RegionChangedReason.class) || aEvent.reasonOccurred(ViewChainChangedReason.class)) && sender != null) {
            redrawBuffer(aEvent);
        } else {
            notifyViewListeners(aEvent);
        }
    }

    /**
     * Recalculates the meta data of the LayeredView.
     * 
     * The region of the LayeredView is set to the bounding box of all layers.
     */
    protected void recalculateMetaData() {
        recalculateMetaData(true);
    }

    /**
     * Recalculates the meta data of the LayeredView.
     * 
     * The region of the LayeredView is set to the bounding box of all layers.
     */
    protected void recalculateMetaData(boolean includePixelBasedImages) {
        if (layers.size() == 0) {
            metaData = null;
            return;
        }

        RectangleDouble bounds = null;
        minimalRegionSize = 0.0f;
        layerLock.lock();
        try {
            for (Layer layer : viewLookup.values()) {
                if (layer.metaDataView != null) {
                    if (includePixelBasedImages || !(layer.metaDataView.getMetaData() instanceof PixelBasedMetaData)) {
                        RectangleDouble metaDataRectangle = layer.metaDataView.getMetaData().getPhysicalRectangle();
                        if (bounds == null) {
                            bounds = metaDataRectangle;
                        } else {
                            bounds = bounds.getBoundingRectangle(metaDataRectangle);
                        }

                        double unitsPerPixel = ((ImageSizeMetaData) layer.metaDataView.getMetaData()).getUnitsPerPixel();
                        if (unitsPerPixel > minimalRegionSize) {
                            minimalRegionSize = unitsPerPixel;
                        }
                    }
                }
            }
        } finally {
            layerLock.unlock();
        }
        if (bounds != null)
            metaData = new PixelBasedMetaData(bounds);

        minimalRegionSize *= 2.0f;
    }

    /**
     * Redraws the scene.
     * 
     * Calls the implementation specific function redrawBufferImpl(). A
     * SubImageDataChangedReason will be appended to the given ChangeEvent.
     * 
     * @param aEvent
     *            ChangeEvent to collect history
     */
    protected void redrawBuffer(ChangeEvent aEvent) {

        // add reason to change event
        if (aEvent == null)
            aEvent = new ChangeEvent();

        aEvent.addReason(new SubImageDataChangedReason(this));

        notifyViewListeners(aEvent);
    }

    /**
     * {@inheritDoc}
     */
    public void moveView(View view, int newLevel) {
        ChangeEvent changeEvent = new ChangeEvent(new LayerChangedReason(this, LayerChangeType.LAYER_MOVED, view, newLevel));
        layerLock.lock();
        try {
            if (layers.contains(view)) {
                layers.remove(view);
                layers.add(newLevel, view);
                redrawBuffer(changeEvent);
            }
        } finally {
            layerLock.unlock();
        }
    }
}
