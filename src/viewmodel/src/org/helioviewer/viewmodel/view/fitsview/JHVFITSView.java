package org.helioviewer.viewmodel.view.fitsview;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;

import org.helioviewer.base.math.Vector2dInt;
import org.helioviewer.viewmodel.changeevent.ChangeEvent;
import org.helioviewer.viewmodel.changeevent.RegionChangedReason;
import org.helioviewer.viewmodel.changeevent.RegionUpdatedReason;
import org.helioviewer.viewmodel.changeevent.SubImageDataChangedReason;
import org.helioviewer.viewmodel.changeevent.ViewportChangedReason;
import org.helioviewer.viewmodel.imagedata.ARGBInt32ImageData;
import org.helioviewer.viewmodel.imagedata.ColorMask;
import org.helioviewer.viewmodel.imagedata.ImageData;
import org.helioviewer.viewmodel.imagedata.JavaBufferedImageData;
import org.helioviewer.viewmodel.imagedata.SingleChannelByte8ImageData;
import org.helioviewer.viewmodel.imagedata.SingleChannelShortImageData;
import org.helioviewer.viewmodel.imagetransport.Short16ImageTransport;
import org.helioviewer.viewmodel.metadata.MetaData;
import org.helioviewer.viewmodel.metadata.MetaDataConstructor;
import org.helioviewer.viewmodel.metadata.ObserverMetaData;
import org.helioviewer.viewmodel.region.Region;
import org.helioviewer.viewmodel.region.StaticRegion;
import org.helioviewer.viewmodel.view.AbstractView;
import org.helioviewer.viewmodel.view.ImageInfoView;
import org.helioviewer.viewmodel.view.MetaDataView;
import org.helioviewer.viewmodel.view.RegionView;
import org.helioviewer.viewmodel.view.SubimageDataView;
import org.helioviewer.viewmodel.view.View;
import org.helioviewer.viewmodel.view.ViewHelper;
import org.helioviewer.viewmodel.view.ViewportView;
import org.helioviewer.viewmodel.view.bufferedimage.DataBufferPool;
import org.helioviewer.viewmodel.viewport.StaticViewport;
import org.helioviewer.viewmodel.viewport.Viewport;
import org.helioviewer.viewmodel.viewportimagesize.StaticViewportImageSize;
import org.helioviewer.viewmodel.viewportimagesize.ViewportImageSizeAdapter;

/**
 * Implementation of ImageInfoView for FITS images.
 * 
 * <p>
 * For further informations about the behavior of this view,
 * {@link ImageInfoView} is a good start to get into the concept.
 * 
 * @author Andreas Hoelzl
 * */
public class JHVFITSView extends AbstractView implements ViewportView, RegionView, SubimageDataView, ImageInfoView, MetaDataView {

    protected Viewport viewport;
    protected Region region;
    protected FITSImage fits;
    protected ImageData subImageData;
    protected MetaData m;
    private URI uri;

    /**
     * Constructor which loads a fits image from a given URI.
     * 
     * @param uri
     *            Specifies the location of the FITS file.
     * @throws IOException
     *             when an error occurred during reading the fits file.
     * */
    public JHVFITSView(URI uri) throws IOException {

        this.uri = uri;

        if (!uri.getScheme().equalsIgnoreCase("file"))
            throw new IOException("FITS does not support the " + uri.getScheme() + " protocol");

        try {
            fits = new FITSImage(uri.toURL().toString());
        } catch (Exception e) {
            throw new IOException("FITS image data cannot be accessed.");
        }

        initFITSImageView();
    }

    /**
     * Constructor which uses a given fits image.
     * 
     * @param fits
     *            FITSImage object which contains the image data
     * @param uri
     *            Specifies the location of the FITS file.
     * */
    public JHVFITSView(FITSImage fits, URI uri) {

        this.uri = uri;
        this.fits = fits;

        initFITSImageView();
    }

    public ImageData getSubimageData(boolean readOnly) {
        if (readOnly || subImageData == null) {
            return subImageData;
        } else {
            DataBufferPool dataBufferPool = ((JavaBufferedImageData) subImageData).getDataBufferPool();
            if (subImageData instanceof SingleChannelByte8ImageData) {
                BufferedImage bi = dataBufferPool.reserveBufferedImageByte();
                ((JavaBufferedImageData) subImageData).getBufferedImage().copyData(bi.getRaster());
                return new SingleChannelByte8ImageData(subImageData, bi);
            } else if (subImageData instanceof SingleChannelShortImageData) {
                BufferedImage bi = dataBufferPool.reserveBufferedImageShort(((Short16ImageTransport) subImageData.getImageFormat()).getNumBitsPerPixel());
                ((JavaBufferedImageData) subImageData).getBufferedImage().copyData(bi.getRaster());
                return new SingleChannelShortImageData(subImageData, bi);
            } else if (subImageData instanceof ARGBInt32ImageData) {
                BufferedImage bi = dataBufferPool.reserveBufferedImageInt();
                ((JavaBufferedImageData) subImageData).getBufferedImage().copyData(bi.getRaster());
                return new ARGBInt32ImageData(subImageData, bi);
            } else {
                return subImageData;
            }
        }
    }

    /**
     * Initializes global variables.
     */
    private void initFITSImageView() {

        m = MetaDataConstructor.getMetaData(fits);

        BufferedImage bi = fits.getImage(0, 0, fits.getPixelHeight(), fits.getPixelWidth());

        if (bi.getColorModel().getPixelSize() <= 8) {
            subImageData = new SingleChannelByte8ImageData(bi, new ColorMask());
        } else if (bi.getColorModel().getPixelSize() <= 16) {
            subImageData = new SingleChannelShortImageData(bi.getColorModel().getPixelSize(), bi, new ColorMask());
        } else {
            subImageData = new ARGBInt32ImageData(bi, new ColorMask());
        }

        region = StaticRegion.createAdaptedRegion(m.getPhysicalLowerLeft().getX(), m.getPhysicalLowerLeft().getY(), m.getPhysicalImageSize().getX(), m.getPhysicalImageSize().getY());

        viewport = StaticViewport.createAdaptedViewport(100, 100);
    }

    /**
     * Updates the sub image depending on the current region.
     * 
     * @param event
     *            Event that belongs to the request.
     * */
    private void updateImageData(ChangeEvent event) {
        Region r = region;

        m = getMetaData();

        double imageMeterPerPixel = m.getPhysicalImageWidth() / fits.getPixelWidth();
        int imageWidth = (int) Math.round(r.getWidth() / imageMeterPerPixel);
        int imageHeight = (int) Math.round(r.getHeight() / imageMeterPerPixel);

        Vector2dInt imagePostion = ViewHelper.calculateInnerViewportOffset(r, m.getPhysicalRegion(), new ViewportImageSizeAdapter(new StaticViewportImageSize(fits.getPixelWidth(), fits.getPixelHeight())));

        BufferedImage bi = fits.getImage(imagePostion.getX(), imagePostion.getY(), (int) imageHeight, (int) imageWidth);

        /*
         * mageData.getSubimage(imagePostion.getX(), imagePostion.getY(), (int)
         * imageWidth, (int) imageHeight);
         */

        if (bi.getColorModel().getPixelSize() <= 8) {
            subImageData = new SingleChannelByte8ImageData(bi, new ColorMask());
        } else if (bi.getColorModel().getPixelSize() <= 16) {
            subImageData = new SingleChannelShortImageData(bi.getColorModel().getPixelSize(), bi, new ColorMask());
        } else {
            subImageData = new ARGBInt32ImageData(bi, new ColorMask());
        }

        event.addReason(new SubImageDataChangedReason(this));
        notifyViewListeners(event);
    }

    /**
     * {@inheritDoc}
     * */
    public Viewport getViewport() {
        return viewport;
    }

    /**
     * {@inheritDoc}
     * */
    public boolean setViewport(Viewport v, ChangeEvent event) {

        // check if viewport has changed
        if (viewport != null && v != null && viewport.getWidth() == v.getWidth() && viewport.getHeight() == v.getHeight())
            return false;

        viewport = v;
        event.addReason(new ViewportChangedReason(this, v));
        notifyViewListeners(event);

        return true;
    }

    /**
     * {@inheritDoc}
     * */
    @SuppressWarnings("unchecked")
    public <T extends View> T getAdapter(Class<T> c) {
        if (c.isInstance(this)) {
            return (T) this;
        } else {
            return null;
        }
    }

    /**
     * {@inheritDoc}
     * */
    public Region getRegion() {

        return region;
    }

    /**
     * {@inheritDoc}
     * */
    public boolean setRegion(Region r, ChangeEvent event) {

        event.addReason(new RegionUpdatedReason(this, r));

        // check if region has changed
        if ((region == r) || (region != null && r != null && region.getCornerX() == r.getCornerX() && region.getCornerY() == r.getCornerY() && region.getWidth() == r.getWidth() && region.getHeight() == r.getHeight()))
            return false;

        region = r;
        event.addReason(new RegionChangedReason(this, r));
        updateImageData(event);

        return true;
    }

    /**
     * Returns the header information as XML string.
     * 
     * @return XML string including all header information.
     * */
    public String getHeaderAsXML() {
        return fits.getHeaderAsXML();
    }

    /**
     * {@inheritDoc}
     * */
    public MetaData getMetaData() {
        return m;
    }

    /**
     * Returns the FITS image managed by this class.
     * 
     * @return FITS image.
     */
    public FITSImage getFITSImage() {
        return fits;
    }

    /**
     * {@inheritDoc}
     * */
    public String getName() {
        if (m instanceof ObserverMetaData) {
            ObserverMetaData observerMetaData = (ObserverMetaData) m;
            return observerMetaData.getFullName();
        } else {
            String name = uri.getPath();
            return name.substring(name.lastIndexOf('/') + 1, name.lastIndexOf('.'));
        }
    }

    /**
     * {@inheritDoc}
     * */
    public URI getUri() {
        return uri;
    }

    /**
     * {@inheritDoc}
     * */
    public boolean isRemote() {
        return false;
    }

    public URI getDownloadURI() {
        return uri;
    }

    public boolean isSubimageDataValid() {
        return subImageData != null;
    }
}
