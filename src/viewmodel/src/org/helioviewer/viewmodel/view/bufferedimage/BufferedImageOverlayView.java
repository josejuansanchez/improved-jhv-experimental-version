package org.helioviewer.viewmodel.view.bufferedimage;

import java.awt.Graphics;
import java.awt.image.BufferedImage;

import org.helioviewer.viewmodel.changeevent.ChangeEvent;
import org.helioviewer.viewmodel.changeevent.RegionChangedReason;
import org.helioviewer.viewmodel.changeevent.SubImageDataChangedReason;
import org.helioviewer.viewmodel.changeevent.ViewChainChangedReason;
import org.helioviewer.viewmodel.imagedata.ImageData;
import org.helioviewer.viewmodel.imagedata.JavaBufferedImageData;
import org.helioviewer.viewmodel.renderer.physical.BufferedImagePhysicalRenderGraphics;
import org.helioviewer.viewmodel.renderer.physical.PhysicalRenderer;
import org.helioviewer.viewmodel.view.AbstractSubimageDataView;
import org.helioviewer.viewmodel.view.LayeredView;
import org.helioviewer.viewmodel.view.OverlayView;
import org.helioviewer.viewmodel.view.View;
import org.helioviewer.viewmodel.view.ViewHelper;

/**
 * Implementation of OverlayView for rendering in software mode.
 * 
 * <p>
 * This class provides the capability to draw overlays in software mode.
 * Therefore it manages a {@link PhysicalRenderer}, which is passed to the
 * registered renderer.
 * 
 * <p>
 * For further information about the role of the OverlayView within the view
 * chain, see {@link org.helioviewer.viewmodel.view.OverlayView}.
 * 
 * @author Markus Langenberg
 */
public class BufferedImageOverlayView extends AbstractSubimageDataView implements OverlayView {

    private LayeredView layeredView;
    private PhysicalRenderer overlayRenderer;

    /**
     * {@inheritDoc}
     */
    public void setRenderer(PhysicalRenderer renderer) {
        overlayRenderer = renderer;
    }

    /**
     * {@inheritDoc}
     */
    public PhysicalRenderer getRenderer() {
        return overlayRenderer;
    }

    /**
     * {@inheritDoc}
     */
    public void setView(View newView) {
        // If no ScaleToViewportImageSizeView present, insert it
        // if (newView != null &&
        // newView.getAdapter(ScaleToViewportImageSizeView.class) == null) {
        // ScaleToViewportImageSizeView scaleToViewportImageSizeView = new
        // BufferedImageScaleToViewportImageSizeView();
        // scaleToViewportImageSizeView.setInterpolationMode(InterpolationMode.BILINEAR);
        // scaleToViewportImageSizeView.setView(newView);
        //
        // // use scaleToViewportImageSizeView as follower
        // newView = scaleToViewportImageSizeView;
        // }

        super.setView(newView);
    }

    /**
     * {@inheritDoc}
     */
    protected void setViewSpecificImplementation(View newView, ChangeEvent changeEvent) {
        super.setViewSpecificImplementation(newView, changeEvent);
        layeredView = ViewHelper.getViewAdapter(view, LayeredView.class);
    }

    /**
     * {@inheritDoc}
     */
    public void viewChanged(View sender, ChangeEvent aEvent) {
        if (aEvent.reasonOccurred(ViewChainChangedReason.class)) {
            layeredView = ViewHelper.getViewAdapter(view, LayeredView.class);
        }

        if (aEvent.reasonOccurred(RegionChangedReason.class)) {
            aEvent.addReason(new SubImageDataChangedReason(this));
        }
        super.viewChanged(sender, aEvent);

        notifyViewListeners(aEvent);
    }

    /**
     * Draws the overlays to the image.
     * 
     * Therefore, calls the registered renderer.
     */
    protected ImageData performImageOperation(ImageData srcData) {
        // if no renderer registered, just pass image data
        if (overlayRenderer == null || layeredView.getNumLayers() <= 0) {
            return srcData;
        }

        // get buffered image
        BufferedImage source = ((JavaBufferedImageData) srcData).getBufferedImage();
        if (source == null) {
            return srcData;
        }

        // render overlays to image
        Graphics g = source.createGraphics();
        BufferedImagePhysicalRenderGraphics renderGraphics = new BufferedImagePhysicalRenderGraphics(g, view);
        overlayRenderer.render(renderGraphics);

        return srcData;
    }
}
