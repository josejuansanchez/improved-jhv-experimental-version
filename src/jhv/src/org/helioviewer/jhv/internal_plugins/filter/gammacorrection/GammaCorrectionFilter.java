package org.helioviewer.jhv.internal_plugins.filter.gammacorrection;

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
 * Filter for applying gamma correction.
 * 
 * <p>
 * It uses the following formula:
 * 
 * <p>
 * p_res(x,y) = 255 * power( p_in(x,y) / 255, gamma)
 * 
 * <p>
 * Here, p_res means the resulting pixel, p_in means the original input pixel
 * and gamma the gamma value used.
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
public class GammaCorrectionFilter extends AbstractFilter implements StandardFilter, GLFragmentShaderFilter {

    private GammaCorrectionPanel panel;

    private float gamma = 1.0f;
    private boolean rebuildTable = true;
    private GammaCorrectionShader shader = new GammaCorrectionShader();

    private byte[] gammaTable8 = null;
    private short[] gammaTable16 = null;

    private boolean forceRefilter = false;

    /**
     * Sets the corresponding gamma correction panel.
     * 
     * @param panel
     *            Corresponding panel.
     */
    void setPanel(GammaCorrectionPanel panel) {
        this.panel = panel;
        panel.setValue(gamma);
    }

    /**
     * Sets the gamma value.
     * 
     * @param newGamma
     *            New gamma value.
     */
    void setGamma(float newGamma) {
        gamma = newGamma;
        rebuildTable = true;
        notifyAllListeners();
    }

    /**
     * Internal function for building the lookup table for 8-bit input data.
     */
    private void buildTable8() {
        if (gammaTable8 == null) {
            gammaTable8 = new byte[0x100];
        }

        float N = 0xFF;

        for (int i = 0; i < 0x100; i++) {
            int v = (int) Math.round(N * Math.pow(i / N, gamma));
            gammaTable8[i] = (byte) v;
        }

        rebuildTable = false;
    }

    /**
     * Internal function for building the lookup table for 16-bit input data.
     */
    private void buildTable16(int bitDepth) {
        int maxValue = 1 << bitDepth;

        if (gammaTable16 == null) {
            gammaTable16 = new short[maxValue];
        }

        float N = maxValue - 1;

        for (int i = 0; i < maxValue; i++) {
            int v = (int) Math.round(N * Math.pow(i / N, gamma));
            gammaTable16[i] = (short) v;
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

        if (Math.abs(gamma - 1.0f) <= 0.01f) {
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
                    pixelData[i] = gammaTable8[pixelData[i] & 0xFF];
                }
                return data;

                // Single channel short image
            } else if (data.getImageTransport() instanceof Short16ImageTransport) {
                if (forceRefilter || rebuildTable) {
                    buildTable16(data.getImageTransport().getNumBitsPerPixel());
                }

                short[] pixelData = ((Short16ImageTransport) data.getImageTransport()).getShort16PixelData();
                for (int i = 0; i < pixelData.length; i++) {
                    pixelData[i] = gammaTable16[pixelData[i] & 0xFFFF];
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

                    r = gammaTable8[r] & 0xFF;
                    g = gammaTable8[g] & 0xFF;
                    b = gammaTable8[b] & 0xFF;

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
     * Fragment shader for applying the gamma correction.
     */
    private class GammaCorrectionShader extends GLFragmentShaderProgram {
        private GLTextureCoordinate gammaParam;

        /**
         * Sets the gamma value
         * 
         * @param gl
         *            Valid reference to the current gl object
         * @param gamma
         *            Gamma value
         */
        private void setGamma(GL gl, float gamma) {
            if (gammaParam != null) {
                gammaParam.setValue(gl, gamma);
            }
        }

        /**
         * {@inheritDoc}
         */
        protected void buildImpl(GLShaderBuilder shaderBuilder) {
            try {
                gammaParam = shaderBuilder.addTexCoordParameter(1);
                String program = "\toutput.rgb = pow(output.rgb, gamma);";
                program = program.replace("output", shaderBuilder.useOutputValue("float4", "COLOR"));
                program = program.replace("gamma", gammaParam.getIdentifier(3));
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
        shader.setGamma(gl, gamma);
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
        setGamma(Float.parseFloat(state));
        panel.setValue(gamma);
    }

    /**
     * {@inheritDoc}
     */
    public String getState() {
        return Float.toString(gamma);
    }
}
