package org.helioviewer.viewmodel.view.bufferedimage;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import org.helioviewer.viewmodel.changeevent.ChangeEvent;
import org.helioviewer.viewmodel.changeevent.SubImageDataChangedReason;
import org.helioviewer.viewmodel.imagedata.ARGBInt32ImageData;
import org.helioviewer.viewmodel.imagedata.ColorMask;
import org.helioviewer.viewmodel.imagedata.ImageData;
import org.helioviewer.viewmodel.imagedata.JavaBufferedImageData;
import org.helioviewer.viewmodel.view.AbstractLayeredView;
import org.helioviewer.viewmodel.view.SubimageDataView;
import org.helioviewer.viewmodel.view.View;
import org.helioviewer.viewmodel.view.ViewHelper;
import org.helioviewer.viewmodel.viewport.Viewport;
import org.helioviewer.viewmodel.viewportimagesize.ViewportImageSize;

/**
 * Implementation of LayeredView for rendering in software mode.
 * 
 * <p>
 * This class merged multiple layers by drawing them into one single image,
 * including scaling and moving them in a correct way.
 * 
 * <p>
 * For further information about the role of the LayeredView within the view
 * chain, see {@link org.helioviewer.viewmodel.view.LayeredView}
 * 
 * @author Ludwig Schmidt
 * @author Markus Langenberg
 */
public class BufferedImageLayeredView extends AbstractLayeredView implements SubimageDataView {
	
    ARGBInt32ImageData imageData;
    BufferedImage buffer;
    DataBufferPool dataBufferPool;
    boolean forceRedraw = false;

    public boolean setViewport(Viewport v, ChangeEvent event) {
        boolean changed = v != null && (viewport == null || viewport.getWidth() != v.getWidth() || viewport.getHeight() != v.getHeight());
        if (changed) {
            //synchronized (this) {
                dataBufferPool = new DataBufferPool(v.getWidth(), v.getHeight());
            //}
        }
        return super.setViewport(v, event);
    }

    public ImageData getSubimageData(boolean readOnly) {
        if (!isSubimageDataValid()) {
            redrawBufferImpl();
        }
        //synchronized (this) {
            ImageData result = imageData;
            if (!readOnly) {
                imageData = null;
            }
            return result;
        //}
    }

    public void viewChanged(View sender, ChangeEvent aEvent) {

        if (aEvent.reasonOccurred(SubImageDataChangedReason.class) && sender != null) {
            viewLookup.get(sender).getSubimage();
        }

        super.viewChanged(sender, aEvent);
    }

    public boolean isSubimageDataValid() {
        if (forceRedraw || imageData == null) {
            return false;
        }
        layerLock.lock();
        try {
            for (Layer l : viewLookup.values()) {
                if (l.needsRedraw()) {
                    return false;
                }
            }
        } finally {
            layerLock.unlock();
        }
        return true;
    }

    protected void redrawBuffer(ChangeEvent aEvent) {
        forceRedraw = true;
        super.redrawBuffer(aEvent);
    }

    /**
     * {@inheritDoc}
     */
    protected void redrawBufferImpl() {
        if (dataBufferPool == null || viewport == null || (viewportImageSize == null) || (viewportImageSize.getWidth() <= 0) || (viewportImageSize.getHeight() <= 0)) {
            return;
        }

        layerLock.lock();
        forceRedraw = false;

        //synchronized (this) {
            if (imageData != null) {
                imageData.getDataBufferPool().releaseBufferedImage(buffer);
            }
            imageData = null;
        //}
        DataBufferPool dataBufferPool;
        //synchronized(this) {
            dataBufferPool = this.dataBufferPool;
            buffer = dataBufferPool.reserveBufferedImageInt();
        //}
        Graphics2D g = buffer.createGraphics();

        boolean redWasUsed = false;
        boolean greenWasUsed = false;
        boolean blueWasUsed = false;

        try {
            for (View v : layers) {
                Layer layer = viewLookup.get(v);
                synchronized (layer) {
                    if (!layer.visibility) {
                        continue;
                    }

                    ViewportImageSize s = ViewHelper.calculateViewportImageSize(layer.viewportView.getViewport(), layer.regionView.getRegion());

                    if (s == null || !s.hasArea()) {
                        continue;
                    }

                    JavaBufferedImageData data = (JavaBufferedImageData) layer.getSubimage();

                    layer.markAsDrawn();

                    if (data == null) {
                        continue;
                    }
                    DataBufferPool dbLayer = data.getDataBufferPool();
                    BufferedImage img = data.getBufferedImage();
                    ColorMask colorMask = data.getColorMask();
                    int intMask = colorMask.getMask();

                    if (intMask == 0xFFFFFFFF) {
                        g.drawImage(img, layer.renderOffset.getX(), layer.renderOffset.getY(), s.getWidth(), s.getHeight(), null);
                    } else {
                        // Since Color mask are not supported in Java, perform
                        // blending manually :-/
                        // The blending is performed with the SRC_OVER-Rule,
                        // regardless of the current AlphaComposite.

                        BufferedImage scaledImage;

                        if (img.getWidth() == s.getWidth() && img.getHeight() == s.getHeight()) {
                            scaledImage = img;
                        } else {
                            scaledImage = dataBufferPool.reserveBufferedImageInt();
                            Graphics2D g2 = scaledImage.createGraphics();
                            g2.setComposite(AlphaComposite.Src);
                            g2.drawImage(img, 0, 0, s.getWidth(), s.getHeight(), null);
                            g2.dispose();
                            dataBufferPool.releaseBufferedImage(img);
                        }

                        boolean dstAlphaIsPremultiplied = buffer.getColorModel().isAlphaPremultiplied();

                        int[] dstPixels = ((DataBufferInt) buffer.getRaster().getDataBuffer()).getData();

                        if (!dstAlphaIsPremultiplied) {
                            for (int i = 0; i < s.getHeight(); i++) {
                                for (int j = 0; j < s.getWidth(); ++j) {
                                    int index = (i + layer.renderOffset.getY()) * buffer.getWidth() + layer.renderOffset.getX() + j;
                                    int dstPixel = dstPixels[index];
                                    dstPixels[index] = dstPixel & 0xFF000000;
                                    int dstAlpha = dstPixels[index] >>> 24;
                                    dstPixels[index] |= (((dstPixel & 0x00FF0000) >>> 16) * dstAlpha / 0xFF) << 16;
                                    dstPixels[index] |= (((dstPixel & 0x0000FF00) >>> 8) * dstAlpha / 0xFF) << 8;
                                    dstPixels[index] |= (((dstPixel & 0x000000FF)) * dstAlpha / 0xFF);
                                }
                            }
                        }

                        int[] srcPixels = ((DataBufferInt) scaledImage.getRaster().getDataBuffer()).getData();

                        if (!scaledImage.getColorModel().isAlphaPremultiplied()) {
                            for (int i = 0; i < s.getHeight(); i++) {
                                for (int j = 0; j < s.getWidth(); ++j) {
                                    int index = i * scaledImage.getWidth() + j;
                                    int srcPixel = srcPixels[index];
                                    srcPixels[index] = srcPixel & 0xFF000000;
                                    int srcAlpha = srcPixels[index] >>> 24;
                                    srcPixels[index] |= (((srcPixel & 0x00FF0000) >>> 16) * srcAlpha / 0xFF) << 16;
                                    srcPixels[index] |= (((srcPixel & 0x0000FF00) >>> 8) * srcAlpha / 0xFF) << 8;
                                    srcPixels[index] |= (((srcPixel & 0x000000FF)) * srcAlpha / 0xFF);
                                }
                            }
                        }

                        for (int i = 0; i < s.getHeight(); i++) {
                            for (int j = 0; j < s.getWidth(); ++j) {
                                int indexDst = (i + layer.renderOffset.getY()) * buffer.getWidth() + layer.renderOffset.getX() + j;
                                int indexSrc = i * scaledImage.getWidth() + j;

                                int srcPixel = srcPixels[indexSrc];
                                int srcAlphaRaw = srcPixel & 0xFF000000;
                                int srcAlpha = (srcAlphaRaw >>> 24) & 0xFF;
                                int srcAlphaDiff = 0xFF - srcAlpha;

                                int dstPixel = dstPixels[indexDst];
                                dstPixels[indexDst] = (dstPixel & ~intMask) | (srcAlphaRaw + ((((dstPixel & 0xFF000000) >>> 24) * srcAlphaDiff / 0xFF) << 24));

                                if (colorMask.showRed()) {
                                    dstPixels[indexDst] |= ((((dstPixel & 0x00FF0000) >>> 16) * srcAlphaDiff / 0xFF) << 16) + (srcPixel & 0x00FF0000);
                                }
                                if (colorMask.showGreen()) {
                                    dstPixels[indexDst] |= ((((dstPixel & 0x0000FF00) >>> 8) * srcAlphaDiff / 0xFF) << 8) + (srcPixel & 0x0000FF00);
                                }
                                if (colorMask.showBlue()) {
                                    dstPixels[indexDst] |= ((((dstPixel & 0x000000FF)) * srcAlphaDiff / 0xFF)) + (srcPixel & 0x000000FF);
                                }
                            }
                        }

                        dbLayer.releaseBufferedImage(scaledImage);

                        if (!dstAlphaIsPremultiplied) {
                            for (int i = 0; i < s.getHeight(); i++) {
                                for (int j = 0; j < s.getWidth(); ++j) {
                                    int index = (i + layer.renderOffset.getY()) * buffer.getWidth() + layer.renderOffset.getX() + j;
                                    int dstPixel = dstPixels[index];
                                    dstPixels[index] = dstPixel & 0xFF000000;
                                    int dstAlpha = dstPixels[index] >>> 24;

                                    if (dstAlpha == 0) {
                                        continue;
                                    }

                                    dstPixels[index] |= (((dstPixel & 0x00FF0000) >>> 16) * 0xFF / dstAlpha) << 16;
                                    dstPixels[index] |= (((dstPixel & 0x0000FF00) >>> 8) * 0xFF / dstAlpha) << 8;
                                    dstPixels[index] |= (((dstPixel & 0x000000FF)) * 0xFF / dstAlpha);
                                }
                            }
                        }
                    }
                }

            }
            //synchronized (this) {
                imageData = new ARGBInt32ImageData(buffer, new ColorMask(redWasUsed, greenWasUsed, blueWasUsed));
                imageData.setDataBufferPool(dataBufferPool);
            //}
        } finally {
            layerLock.unlock();
        }
    }    
}