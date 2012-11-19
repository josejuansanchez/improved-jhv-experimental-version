package org.helioviewer.viewmodel.view.bufferedimage;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferInt;

import org.helioviewer.base.math.Vector2dInt;
import org.helioviewer.base.physics.Constants;
import org.helioviewer.viewmodel.changeevent.ChangeEvent;
import org.helioviewer.viewmodel.changeevent.RegionChangedReason;
import org.helioviewer.viewmodel.changeevent.SubImageDataChangedReason;
import org.helioviewer.viewmodel.changeevent.ViewChainChangedReason;
import org.helioviewer.viewmodel.imagedata.ARGBInt32ImageData;
import org.helioviewer.viewmodel.imagedata.ImageData;
import org.helioviewer.viewmodel.imagedata.JavaBufferedImageData;
import org.helioviewer.viewmodel.metadata.HelioviewerMetaData;
import org.helioviewer.viewmodel.metadata.HelioviewerOcculterMetaData;
import org.helioviewer.viewmodel.region.Region;
import org.helioviewer.viewmodel.view.AbstractSubimageDataView;
import org.helioviewer.viewmodel.view.HelioviewerGeometryView;
import org.helioviewer.viewmodel.view.MetaDataView;
import org.helioviewer.viewmodel.view.RegionView;
import org.helioviewer.viewmodel.view.View;
import org.helioviewer.viewmodel.view.ViewHelper;
import org.helioviewer.viewmodel.viewportimagesize.StaticViewportImageSize;
import org.helioviewer.viewmodel.viewportimagesize.ViewportImageSize;

/**
 * Implementation of HelioviewGeometryView for rendering in OpenGL mode.
 * 
 * <p>
 * This class provides the capability to cut out the invalid areas in solar
 * images. It does so by calculating the distance from the center for every
 * single pixel in the image. If the distance is outside the valid area of that
 * specific image, its alpha value is set to zero, otherwise it remains
 * untouched.
 * 
 * <p>
 * Technically, it uses the Java AlphaComposite to mask invalid areas.
 * 
 * <p>
 * For further information about the role of the HelioviewerGeometryView within
 * the view chain, see
 * {@link org.helioviewer.viewmodel.view.HelioviewerGeometryView}
 * 
 * @author Markus Langenberg
 */
public class BufferedImageHelioviewerGeometryView extends AbstractSubimageDataView implements HelioviewerGeometryView {

    private RegionView regionView;
    private BufferedImage maskImage;
    private HelioviewerMetaData metaData;

    /**
     * {@inheritDoc}
     */
    protected void setViewSpecificImplementation(View newView, ChangeEvent changeEvent) {
        super.setViewSpecificImplementation(newView, changeEvent);
        updatePrecomputed();
    }

    /**
     * {@inheritDoc}
     */
    public void viewChanged(View sender, ChangeEvent aEvent) {
        if (aEvent.reasonOccurred(ViewChainChangedReason.class)) {
            updatePrecomputed();
        }

        if (aEvent.reasonOccurred(RegionChangedReason.class)) {
            aEvent.addReason(new SubImageDataChangedReason(this));
        }
        super.viewChanged(sender, aEvent);

        notifyViewListeners(aEvent);
    }

    /**
     * Updates the precomputed view adapters.
     */
    private void updatePrecomputed() {
        regionView = ViewHelper.getViewAdapter(view, RegionView.class);

        MetaDataView metaDataView = ViewHelper.getViewAdapter(view, MetaDataView.class);
        if (metaDataView != null && metaDataView.getMetaData() != null && metaDataView.getMetaData() instanceof HelioviewerMetaData) {
            metaData = (HelioviewerMetaData) metaDataView.getMetaData();
        }
    }

    @Override
    protected ImageData performImageOperation(ImageData source_) {
        if (regionView != null && regionView.getRegion() != null) {
            if (source_ instanceof JavaBufferedImageData && metaData != null) {
                BufferedImage source = ((JavaBufferedImageData) source_).getBufferedImage();
                DataBufferPool dataBufferPool = ((JavaBufferedImageData) source_).getDataBufferPool();

                ViewportImageSize viewportImageSize = StaticViewportImageSize.createAdaptedViewportImageSize(source.getWidth(), source.getHeight());

                Vector2dInt offset = ViewHelper.convertImageToScreenDisplacement(-regionView.getRegion().getUpperLeftCorner().getX(), regionView.getRegion().getUpperLeftCorner().getY(), regionView.getRegion(), viewportImageSize);

                Vector2dInt radius;
                Region region = regionView.getRegion();

                if (metaData instanceof HelioviewerOcculterMetaData) {
                    radius = ViewHelper.convertImageToScreenDisplacement(((HelioviewerOcculterMetaData) metaData).getInnerPhysicalOcculterRadius() * roccInnerFactor, ((HelioviewerOcculterMetaData) metaData).getOuterPhysicalOcculterRadius() * roccOuterFactor, region, viewportImageSize);
                } else if (metaData.getInstrument().equalsIgnoreCase("LASCO")) {
                    radius = ViewHelper.convertImageToScreenDisplacement(Constants.SunRadius * discFadingFactor, Constants.SunRadius, region, viewportImageSize);
                } else {
                    radius = ViewHelper.convertImageToScreenDisplacement(0, Constants.SunRadius * discFactor, region, viewportImageSize);
                }

                if (metaData.getInstrument().equalsIgnoreCase("MDI") || metaData.getInstrument().equalsIgnoreCase("HMI") || metaData.getInstrument().equalsIgnoreCase("LASCO")) {
                    BufferedImage target = dataBufferPool.reserveBufferedImageInt();
                    Graphics2D g = target.createGraphics();
                    g.setComposite(AlphaComposite.Clear);
                    g.fillRect(0, 0, target.getWidth(), target.getHeight());
                    g.setComposite(AlphaComposite.Src);
                    g.drawImage(source, 0, 0, null);
                    dataBufferPool.releaseBufferedImage(source);

                    maskImage = dataBufferPool.reserveBufferedImageInt();
                    Graphics2D g2 = maskImage.createGraphics();
                    g2.setComposite(AlphaComposite.Clear);
                    g2.fillRect(0, 0, maskImage.getWidth(), maskImage.getHeight());
                    g2.setComposite(AlphaComposite.Src);
                    g2.fillOval(offset.getX() - radius.getY(), offset.getY() - radius.getY(), radius.getY() * 2, radius.getY() * 2);
                    if (radius.getX() != 0) {
                        g2.setComposite(AlphaComposite.Clear);
                        g2.fillOval(offset.getX() - radius.getX(), offset.getY() - radius.getX(), radius.getX() * 2, radius.getX() * 2);
                    }
                    g2.dispose();

                    g.setComposite(AlphaComposite.DstIn);
                    g.drawImage(maskImage, 0, 0, null);
                    g.dispose();

                    dataBufferPool.releaseBufferedImage(maskImage);
                    return new ARGBInt32ImageData(source_, target);
                } else { // EIT and AIA
                    DataBuffer dbSrc = source.getRaster().getDataBuffer();
                    BufferedImage target = null;
                    int[] sourcePixels = null;
                    if (dbSrc instanceof DataBufferInt) {
                        sourcePixels = ((DataBufferInt) dbSrc).getData();
                        target = source;
                    } else {
                        target = dataBufferPool.reserveBufferedImageInt();
                        sourcePixels = ((DataBufferInt) target.getRaster().getDataBuffer()).getData();
                        source.getRGB(0, 0, source.getWidth(), source.getHeight(), sourcePixels, 0, source.getWidth());
                        dataBufferPool.releaseBufferedImage(source);
                    }

                    for (int i = 0; i < target.getWidth() * target.getHeight(); i++) {
                        int posX = i % target.getWidth();
                        int posY = i / target.getWidth();

                        double currentRadius = Math.sqrt(Math.pow(offset.getX() - posX, 2) + Math.pow(offset.getY() - posY, 2));

                        if (currentRadius >= radius.getY()) {
                            int pixel = sourcePixels[i] & 0x00FFFFFF;
                            int maxPixelValue = Math.max(Math.max((pixel & 0x00FF0000) >> 16, (pixel & 0x0000FF00) >> 8), (pixel & 0x000000FF));
                            double alphaModification = Math.pow(maxPixelValue / 255.0f, 1.0f - ((sourcePixels[i] >> 24) & 0xFF) / 255.0f);

                            if (currentRadius <= radius.getX()) {

                                float fadeDisc = (float) ((radius.getX() - currentRadius) / (radius.getX() - radius.getY()));
                                fadeDisc = Math.max(Math.min(fadeDisc, 1.0f), 0.0f);
                                fadeDisc = fadeDisc * fadeDisc * (3.0f - 2.0f * fadeDisc);

                                alphaModification = fadeDisc + alphaModification * (1 - fadeDisc);
                            }
                            int a = (int) (((sourcePixels[i] & 0xFF000000) >>> 24) * alphaModification);
                            int r = (int) (((sourcePixels[i] & 0x00FF0000) >>> 16) * alphaModification);
                            int b = (int) (((sourcePixels[i] & 0x0000FF00) >>> 8) * alphaModification);
                            int g = (int) (((sourcePixels[i] & 0x000000FF)) * alphaModification);
                            sourcePixels[i] = (a << 24) | (r << 16) | (b << 8) | (g);
                        }
                    }
                    return new ARGBInt32ImageData(source_, target);
                }
            }
        }
        return source_;
    }
}
