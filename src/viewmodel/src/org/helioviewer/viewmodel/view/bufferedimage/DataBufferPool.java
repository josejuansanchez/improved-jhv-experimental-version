package org.helioviewer.viewmodel.view.bufferedimage;

import java.awt.Transparency;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.ComponentColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferInt;
import java.awt.image.DataBufferShort;
import java.awt.image.Raster;
import java.awt.image.SampleModel;
import java.awt.image.WritableRaster;
import java.util.Arrays;
import java.util.Stack;

import org.helioviewer.base.logging.Log;

/**
 * Each JavaBufferedImageData instance has a DataBufferPool instance associated
 * with it. The purpose of this pool is to save memory and to increase
 * performance by caching unused DataBuffer objects which can be used in the
 * future. Since the image size seldom changes within the viewchain (only in the
 * LayeredView) each DataBufferPool manages only DataBuffers with the same size
 * as the associated ImageData. The DataBufferPool should be used whenever
 * temporary buffer instances with the same size as the original are needed as
 * ii is often the case in FilterViews etc.
 * 
 * @author Andre Dau
 * 
 */
public class DataBufferPool {

    Stack<DataBufferByte> byteBuffer;
    Stack<DataBufferInt> intBuffer;
    Stack<DataBufferShort> shortBuffer;

    final int width, height;
    static final ColorModel ARGB32_COLOR_MODEL = ColorModel.getRGBdefault();
    static final ColorModel GRAY8_COLOR_MODEL = new ComponentColorModel(ColorSpace.getInstance(ColorSpace.CS_GRAY), new int[] { 8 }, false, false, Transparency.OPAQUE, DataBuffer.TYPE_BYTE);
    final SampleModel GRAY8_SAMPLE_MODEL, ARGB32_SAMPLE_MODEL;

    static final int[] BYTE_MASK = new int[] { 0xff };
    static final int[] INT_MASK = new int[] { 0xff0000, 0xff00, 0xff, 0xff000000 };

    public DataBufferPool(int width, int height) {
        this.width = width;
        this.height = height;
        this.GRAY8_SAMPLE_MODEL = GRAY8_COLOR_MODEL.createCompatibleSampleModel(width, height);
        this.ARGB32_SAMPLE_MODEL = ARGB32_COLOR_MODEL.createCompatibleSampleModel(width, height);
        byteBuffer = new Stack<DataBufferByte>();
        intBuffer = new Stack<DataBufferInt>();
        shortBuffer = new Stack<DataBufferShort>();
    }

    public int getHeight() {
        return height;
    }

    public int getWidth() {
        return width;
    }

    public DataBufferByte reserveDataBufferByte() {
        synchronized (byteBuffer) {
            if (byteBuffer.isEmpty()) {
                return new DataBufferByte(width * height);
            } else {
                DataBufferByte db = byteBuffer.pop();
                Arrays.fill(db.getData(), (byte) 0);
                return db;
            }
        }

    }

    public DataBufferInt reserveDataBufferInt() {
        synchronized (intBuffer) {
            if (intBuffer.isEmpty()) {
                return new DataBufferInt(width * height);
            } else {
                DataBufferInt db = intBuffer.pop();
                Arrays.fill(db.getData(), 0);
                return db;
            }
        }
    }

    public BufferedImage reserveBufferedImageByte() {
        DataBufferByte db = reserveDataBufferByte();
        WritableRaster raster = Raster.createWritableRaster(GRAY8_SAMPLE_MODEL, db, null);
        return new BufferedImage(GRAY8_COLOR_MODEL, raster, GRAY8_COLOR_MODEL.isAlphaPremultiplied(), null);

    }

    public BufferedImage reserveBufferedImageInt() {
        DataBufferInt db = reserveDataBufferInt();
        WritableRaster raster = Raster.createWritableRaster(ARGB32_SAMPLE_MODEL, db, null);
        return new BufferedImage(ARGB32_COLOR_MODEL, raster, ARGB32_COLOR_MODEL.isAlphaPremultiplied(), null);

    }

    public DataBufferShort reserveDataBufferShort() {
        synchronized (shortBuffer) {
            if (shortBuffer.isEmpty()) {
                return new DataBufferShort(width * height);
            } else {
                DataBufferShort db = shortBuffer.pop();
                Arrays.fill(db.getData(), (short) 0);
                return db;
            }
        }
    }

    public BufferedImage reserveBufferedImageShort(int bitDepth) {
        int mask = 0xffffffff;
        mask = mask >>> (32 - bitDepth);
        DataBufferShort db = reserveDataBufferShort();
        final ColorModel GRAY16_COLOR_MODEL = new ComponentColorModel(ColorSpace.getInstance(ColorSpace.CS_GRAY), new int[] { 8 }, false, false, Transparency.OPAQUE, DataBuffer.TYPE_BYTE);
        final SampleModel GRAY16_SAMPLE_MODEL = GRAY16_COLOR_MODEL.createCompatibleSampleModel(width, height);
        WritableRaster raster = Raster.createWritableRaster(GRAY16_SAMPLE_MODEL, db, null);
        return new BufferedImage(GRAY16_COLOR_MODEL, raster, GRAY16_COLOR_MODEL.isAlphaPremultiplied(), null);

    }

    public DataBuffer reserveDataBuffer(DataBuffer instanceFrom) {
        if (instanceFrom instanceof DataBufferByte) {
            return reserveDataBufferByte();
        } else if (instanceFrom instanceof DataBufferShort) {
            return reserveDataBufferShort();
        } else if (instanceFrom instanceof DataBufferInt) {
            return reserveDataBufferInt();
        } else {
            throw new RuntimeException("Unknown format");
        }
    }

    public BufferedImage reserveBufferedImage(BufferedImage instanceFrom) {
        DataBuffer db = instanceFrom.getRaster().getDataBuffer();
        if (db instanceof DataBufferByte) {
            return reserveBufferedImageByte();
        } else if (db instanceof DataBufferShort) {
            return reserveBufferedImageShort(instanceFrom.getColorModel().getPixelSize());
        } else if (db instanceof DataBufferInt) {
            return reserveBufferedImageInt();
        } else {
            throw new RuntimeException("Unknown format");
        }
    }

    public void releaseBufferedImage(BufferedImage img) {
        if (img == null) {
            return;
        }
        DataBuffer db = img.getRaster().getDataBuffer();
        releaseDataBuffer(db);
    }

    public void releaseDataBuffer(DataBuffer db) {
        if (db == null)
            return;
        if (db.getSize() != width * height) {
            Log.error(">> DataBufferPool.releaseDataBuffer(DataBuffer) > Wrong size: " + db.getSize() + ". Expected: " + (width * height) + ". DataBuffer will be discarded.");
            return;
        }
        if (db instanceof DataBufferByte) {
            synchronized (byteBuffer) {
                if (!byteBuffer.contains(db))
                    byteBuffer.add((DataBufferByte) db);
            }
        } else if (db instanceof DataBufferShort) {
            synchronized (shortBuffer) {
                if (!shortBuffer.contains(db))
                    shortBuffer.add((DataBufferShort) db);
            }
        } else if (db instanceof DataBufferInt) {
            synchronized (intBuffer) {
                if (!intBuffer.contains(db))
                    intBuffer.add((DataBufferInt) db);
            }
        } else {
            throw new RuntimeException("Unknown format");
        }
    }

}
