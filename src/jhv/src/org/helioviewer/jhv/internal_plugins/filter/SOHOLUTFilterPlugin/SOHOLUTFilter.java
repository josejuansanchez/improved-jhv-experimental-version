package org.helioviewer.jhv.internal_plugins.filter.SOHOLUTFilterPlugin;

import java.awt.image.DataBufferInt;
import java.nio.IntBuffer;

import javax.media.opengl.GL;

import org.helioviewer.viewmodel.filter.AbstractFilter;
import org.helioviewer.viewmodel.filter.GLFragmentShaderFilter;
import org.helioviewer.viewmodel.filter.StandardFilter;
import org.helioviewer.viewmodel.imagedata.ARGBInt32ImageData;
import org.helioviewer.viewmodel.imagedata.ImageData;
import org.helioviewer.viewmodel.imagedata.JavaBufferedImageData;
import org.helioviewer.viewmodel.imageformat.SingleChannelImageFormat;
import org.helioviewer.viewmodel.imagetransport.Byte8ImageTransport;
import org.helioviewer.viewmodel.imagetransport.Short16ImageTransport;
import org.helioviewer.viewmodel.view.bufferedimage.DataBufferPool;
import org.helioviewer.viewmodel.view.opengl.GLTextureHelper;
import org.helioviewer.viewmodel.view.opengl.shader.GLShaderBuilder;
import org.helioviewer.viewmodel.view.opengl.shader.GLSingleChannelLookupFragmentShaderProgram;

/**
 * Filter for applying a color table to a single channel image.
 * 
 * <p>
 * If the input image is not a single channel image, the filter does nothing and
 * returns the input data.
 * 
 * <p>
 * This filter supports software rendering as well as rendering in OpenGL.
 * 
 * mostly rewritten
 * 
 * @author Helge Dietert
 */
public class SOHOLUTFilter extends AbstractFilter implements StandardFilter, GLFragmentShaderFilter {
    // /////////////////////////
    // GENERAL //
    // /////////////////////////

    private SOHOLUTPanel panel;

    /**
     * Used lut
     */
    private LUT lut;
    private boolean invertLUT = false;

    /**
     * {@inheritDoc}
     * 
     * <p>
     * This filter is a major filter.
     */
    public boolean isMajorFilter() {
        return true;
    }

    /**
     * LUT is set to Gray as default table.
     */
    public SOHOLUTFilter() {
        lut = LUT.getStandardList().get("Gray");
    }

    /**
     * Constructor setting the color table.
     * 
     * @param startWithLut
     *            Color table to apply to the image
     */
    public SOHOLUTFilter(LUT startWithLut) {
        lut = startWithLut;
    }

    /**
     * Sets the corresponding SOHOLUT panel.
     * 
     * @param panel
     *            Corresponding panel.
     */
    void setPanel(SOHOLUTPanel panel) {
        this.panel = panel;
        panel.setValue(lut, invertLUT);
    }

    /**
     * Sets a new color table to use from now on.
     * 
     * @param newLUT
     *            New color table
     */
    void setLUT(LUT newLUT, boolean invert) {
        if (newLUT == null || (lut == newLUT && invertLUT == invert)) {
            return;
        }
        lut = newLUT;
        invertLUT = invert;
        notifyAllListeners();
    }

    // /////////////////////////
    // STANDARD //
    // /////////////////////////

    /**
     * {@inheritDoc}
     */
    public ImageData apply(ImageData data) {
        // Ship over gray for performance as before
        if (data == null || !(data.getImageFormat() instanceof SingleChannelImageFormat) || (lut.getName() == "Gray" && !invertLUT)) {
            return data;
        }

        DataBufferPool dataBufferPool = ((JavaBufferedImageData) data).getDataBufferPool();

        if (data.getImageTransport() instanceof Byte8ImageTransport) {
            byte[] pixelData = ((Byte8ImageTransport) data.getImageTransport()).getByte8PixelData();
            DataBufferInt newBuffer = dataBufferPool.reserveDataBufferInt();
            lut.lookup8(pixelData, newBuffer.getData(), invertLUT);
            dataBufferPool.releaseBufferedImage(((JavaBufferedImageData) data).getBufferedImage());
            return new ARGBInt32ImageData(data, newBuffer.getData());
        } else if (data.getImageTransport() instanceof Short16ImageTransport) {
            short[] pixelData = ((Short16ImageTransport) data.getImageTransport()).getShort16PixelData();
            DataBufferInt newBuffer = dataBufferPool.reserveDataBufferInt();
            lut.lookup16(pixelData, newBuffer.getData(), invertLUT);
            dataBufferPool.releaseBufferedImage(((JavaBufferedImageData) data).getBufferedImage());
            data = new ARGBInt32ImageData(data, newBuffer.getData());
            return data;
        }

        return null;
    }

    // /////////////////////////
    // OPENGL //
    // /////////////////////////
    private GLSingleChannelLookupFragmentShaderProgram shader = new GLSingleChannelLookupFragmentShaderProgram();
    private int lookupTex = 0;
    private LUT lastLut = null;
    private boolean lastInverted = false;

    /**
     * {@inheritDoc}
     */
    public GLShaderBuilder buildFragmentShader(GLShaderBuilder shaderBuilder) {
        shader.build(shaderBuilder);

        if (lastLut == null) {
            GLTextureHelper textureHelper = new GLTextureHelper();
            textureHelper.delTextureID(shaderBuilder.getGL(), lookupTex);
            // I think this may be wrong, but I just reused the openGL code
            lookupTex = textureHelper.genTextureID(shaderBuilder.getGL());
        }

        return shaderBuilder;
    }

    /**
     * {@inheritDoc}
     * 
     * In this case, also updates the color table, if necessary.
     */
    public void applyGL(GL gl) {
        shader.bind(gl);
        shader.activateLutTexture(gl);

        // Note: The lookup table will always be power of two,
        // so we won't get any problems here.

        gl.glBindTexture(GL.GL_TEXTURE_1D, lookupTex);

        if (lastLut != lut || invertLUT != lastInverted) {
            int[] intLUT;

            if (invertLUT) {
                int[] sourceLUT = lut.getLut8();
                intLUT = new int[sourceLUT.length];

                int offset = sourceLUT.length - 1;
                for (int i = 0; i < sourceLUT.length / 2; i++) {
                    intLUT[i] = sourceLUT[offset - i];
                    intLUT[offset - i] = sourceLUT[i];
                }
            } else {
                intLUT = lut.getLut8();
            }

            IntBuffer buffer = IntBuffer.wrap(intLUT);

            gl.glPixelStorei(GL.GL_UNPACK_SKIP_PIXELS, 0);
            gl.glPixelStorei(GL.GL_UNPACK_SKIP_ROWS, 0);
            gl.glPixelStorei(GL.GL_UNPACK_ROW_LENGTH, 0);
            gl.glPixelStorei(GL.GL_UNPACK_ALIGNMENT, 4);

            gl.glTexImage1D(GL.GL_TEXTURE_1D, 0, GL.GL_RGBA, buffer.limit(), 0, GL.GL_BGRA, GL.GL_UNSIGNED_INT_8_8_8_8_REV, buffer);
            gl.glTexParameteri(GL.GL_TEXTURE_1D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_NEAREST);
            gl.glTexParameteri(GL.GL_TEXTURE_1D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_NEAREST);
            gl.glTexParameteri(GL.GL_TEXTURE_1D, GL.GL_TEXTURE_WRAP_S, GL.GL_CLAMP);
            lastLut = lut;
            lastInverted = invertLUT;
        }
    }

    protected void finalize() {
        if (lookupTex != 0) {
            GLTextureHelper textureHelper = new GLTextureHelper();
            textureHelper.delTextureID(null, lookupTex);
        }
    }

    /**
     * {@inheritDoc}
     */
    public void forceRefilter() {
        lastLut = null;
    }

    /**
     * {@inheritDoc}
     */
    public void setState(String state) {
        String[] values = state.trim().split(" ");
        String tableString = values[0];
        String invertString = values[values.length - 1];
        for (int i = 1; i < values.length - 1; i++) {
            tableString += " " + values[i];
        }
        setLUT(LUT.getStandardList().get(tableString.replaceAll("ANGSTROM", Character.toString(LUT.angstrom))), Boolean.parseBoolean(invertString));
        panel.setValue(lut, invertLUT);
    }

    /**
     * {@inheritDoc}
     */
    public String getState() {
        return lut.getName().replaceAll(Character.toString(LUT.angstrom), "ANGSTROM") + " " + invertLUT;
    }
}
