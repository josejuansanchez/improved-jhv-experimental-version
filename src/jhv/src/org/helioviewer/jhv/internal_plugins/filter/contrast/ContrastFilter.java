package org.helioviewer.jhv.internal_plugins.filter.contrast;

import javax.media.opengl.GL;

import org.helioviewer.viewmodel.filter.AbstractFilter;
import org.helioviewer.viewmodel.filter.GLFragmentShaderFilter;
import org.helioviewer.viewmodel.filter.StandardFilter;
import org.helioviewer.viewmodel.imagedata.ImageData;
import org.helioviewer.viewmodel.imagetransport.Byte8ImageTransport;
import org.helioviewer.viewmodel.imagetransport.Int32ImageTransport;
import org.helioviewer.viewmodel.imagetransport.Short16ImageTransport;
import org.helioviewer.viewmodel.view.opengl.shader.GLFragmentShaderProgram;
import org.helioviewer.viewmodel.view.opengl.shader.GLShaderBuilder;
import org.helioviewer.viewmodel.view.opengl.shader.GLTextureCoordinate;
import org.helioviewer.viewmodel.view.opengl.shader.GLShaderBuilder.GLBuildShaderException;

/**
 * Filter for enhancing the contrast of the image.
 * 
 * <p>
 * It uses the following formula:
 * 
 * <p>
 * p_res(x,y) = 255 * (0.5 * sign(2x/255 - 1) * abs(2x/255 - 1)^(1.5^c) + 0.5)
 * 
 * <p>
 * Here, p_res means the resulting pixel, p_in means the original input pixel
 * and contrast the parameter used.
 * 
 * <p>
 * Since this is a point operation, it is optimized using a lookup table filled
 * by precomputing the output value for every possible input value. The actual
 * filtering is performed by using that lookup table.
 * 
 * <p>
 * The output of the filter always has the same image format as the input.
 * 
 * <p>
 * This filter supports software rendering as well as rendering in OpenGL.
 * 
 * @author Markus Langenberg
 */
public class ContrastFilter extends AbstractFilter implements StandardFilter, GLFragmentShaderFilter {

    private ContrastPanel panel;

    private float contrast = 0.0f;
    private boolean rebuildTable = true;
    private ContrastShader shader = new ContrastShader();

    private byte[] contrastTable8 = null;
    private short[] contrastTable16 = null;

    private boolean forceRefilter = false;

    /**
     * Sets the corresponding contrast panel.
     * 
     * @param panel
     *            Corresponding panel.
     */
    void setPanel(ContrastPanel panel) {
        this.panel = panel;
        panel.setValue(contrast);
    }

    /**
     * Sets the contrast parameter.
     * 
     * @param newContrast
     *            New contrast parameter.
     */
    void setContrast(float newContrast) {
        contrast = newContrast;
        rebuildTable = true;
        notifyAllListeners();
    }

    /**
     * Internal function for building the lookup table for 8-bit input data.
     */
    private void buildTable8() {
        if (contrastTable8 == null) {
            contrastTable8 = new byte[0x100];
        }

        float N = 0xFF;

        for (int i = 0; i < 0x100; i++) {
            int v = (int) (N * (0.5f * Math.signum(2 * i / N - 1) * Math.pow(Math.abs(2 * i / N - 1), Math.pow(1.5, -contrast)) + 0.5f));
            contrastTable8[i] = (byte) v;
        }

        rebuildTable = false;
    }

    /**
     * Internal function for building the lookup table for 16-bit input data.
     */
    private void buildTable16(int bitDepth) {
        int maxValue = 1 << bitDepth;

        if (contrastTable16 == null) {
            contrastTable16 = new short[maxValue];
        }

        float N = maxValue - 1;

        for (int i = 0; i < maxValue; i++) {
            int v = (int) (N * (0.5f * Math.signum(2 * i / N - 1) * Math.pow(Math.abs(2 * i / N - 1), Math.pow(1.5, -contrast)) + 0.5f));
            contrastTable16[i] = (short) v;
        }

        rebuildTable = false;
    }

    /**
     * {@inheritDoc}
     */
    public ImageData apply(ImageData data) {
        if (data == null) {
            return null;
        }

        if (Math.abs(contrast) <= 0.01f) {
            return data;
        }

        try {
            // Single channel byte image
            if (data.getImageTransport() instanceof Byte8ImageTransport) {
                if (forceRefilter || rebuildTable) {
                    buildTable8();
                }
                byte[] pixelData = ((Byte8ImageTransport) data.getImageTransport()).getByte8PixelData();
                for (int i = 0; i < pixelData.length; i++) {
                    pixelData[i] = contrastTable8[pixelData[i] & 0xFF];
                }
                return data;

                // Single channel short image
            } else if (data.getImageTransport() instanceof Short16ImageTransport) {
                if (forceRefilter || rebuildTable) {
                    buildTable16(data.getImageTransport().getNumBitsPerPixel());
                }

                short[] pixelData = ((Short16ImageTransport) data.getImageTransport()).getShort16PixelData();
                for (int i = 0; i < pixelData.length; i++) {
                    pixelData[i] = contrastTable16[pixelData[i] & 0xFFFF];
                }
                return data;

                // (A)RGB image: Filter each channel separate
            } else if (data.getImageTransport() instanceof Int32ImageTransport) {
                if (forceRefilter || rebuildTable) {
                    buildTable8();
                }
                int[] pixelData = ((Int32ImageTransport) data.getImageTransport()).getInt32PixelData();
                for (int i = 0; i < pixelData.length; i++) {

                    int rgb = pixelData[i];
                    int a = rgb >>> 24;
                    int r = (rgb >>> 16) & 0xFF;
                    int g = (rgb >>> 8) & 0xFF;
                    int b = rgb & 0xff;

                    r = contrastTable8[r] & 0xFF;
                    g = contrastTable8[g] & 0xFF;
                    b = contrastTable8[b] & 0xFF;

                    pixelData[i] = (a << 24) | (r << 16) | (g << 8) | b;
                }
                return data;
            }
        } finally {
            forceRefilter = false;
        }

        return null;
    }

    /**
     * Fragment shader for enhancing the contrast.
     */
    private class ContrastShader extends GLFragmentShaderProgram {
        private GLTextureCoordinate contrastParam;

        /**
         * Sets the contrast parameter
         * 
         * @param gl
         *            Valid reference to the current gl object
         * @param contrast
         *            Contrast parameter
         */
        private void setContrast(GL gl, float contrast) {
            if (contrastParam != null) {
                contrastParam.setValue(gl, contrast);
            }
        }

        /**
         * {@inheritDoc}
         */
        protected void buildImpl(GLShaderBuilder shaderBuilder) {
            try {
                contrastParam = shaderBuilder.addTexCoordParameter(1);
                String program = "\toutput.rgb = 0.5f * sign(2.0f * output.rgb - 1.0f) * pow(abs(2.0f * output.rgb - 1.0f), pow(1.5f, -contrast)) + 0.5f;";
                program = program.replace("output", shaderBuilder.useOutputValue("float4", "COLOR"));
                program = program.replace("contrast", contrastParam.getIdentifier(1));
                shaderBuilder.addMainFragment(program);
            } catch (GLBuildShaderException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    public GLShaderBuilder buildFragmentShader(GLShaderBuilder shaderBuilder) {
        shader.build(shaderBuilder);
        return shaderBuilder;
    }

    /**
     * {@inheritDoc}
     */
    public void applyGL(GL gl) {
        shader.bind(gl);
        shader.setContrast(gl, contrast);
    }

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
     * {@inheritDoc}
     */
    public void forceRefilter() {
        forceRefilter = true;
    }

    /**
     * {@inheritDoc}
     */
    public void setState(String state) {
        setContrast(Float.parseFloat(state));
        panel.setValue(contrast);
    }

    /**
     * {@inheritDoc}
     */
    public String getState() {
        return Float.toString(contrast);
    }
}
