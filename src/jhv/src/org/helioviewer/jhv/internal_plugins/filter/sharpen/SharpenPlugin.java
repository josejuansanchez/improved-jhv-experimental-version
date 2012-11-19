package org.helioviewer.jhv.internal_plugins.filter.sharpen;

import org.helioviewer.viewmodel.filter.Filter;
import org.helioviewer.viewmodelplugin.filter.FilterPanel;
import org.helioviewer.viewmodelplugin.filter.FilterTabDescriptor;
import org.helioviewer.viewmodelplugin.filter.SimpleFilterContainer;
import org.helioviewer.viewmodel.view.FilterView;
import org.helioviewer.viewmodel.view.opengl.shader.GLShaderHelper;

/**
 * Plugin for sharpen the image.
 * 
 * <p>
 * This plugin provides the capability to sharpen the image. It manages a filter
 * for applying the sharpening and a slider to influence its weighting.
 * 
 * <p>
 * Depending of the graphics card, it may only provide a software implementation
 * although OpenGL is enabled.
 * 
 * @author Markus Langenberg
 * 
 */
public class SharpenPlugin extends SimpleFilterContainer {
    /**
     * {@inheritDoc}
     */
    protected Filter getFilter() {

        if (GLShaderHelper.getMaxTextureIndirections() < 10) {
            return new SharpenFilter();
        }

        return new SharpenGLFilter();
    }

    /**
     * Use the basis class to refer to it consitently
     * 
     * @see org.helioviewer.viewmodelplugin.filter.SimpleFilterContainer#getFilterClass()
     */
    @Override
    public Class<? extends Filter> getFilterClass() {
        return SharpenFilter.class;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected boolean useFilter(FilterView view) {
        return true;
    }

    /**
     * {@inheritDoc}
     */
    protected FilterPanel getPanel() {
        return new SharpenPanel();
    }

    /**
     * {@inheritDoc}
     */
    public String getDescription() {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    public String getName() {
        return "Sharpening";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected FilterTabDescriptor getFilterTab() {
        return new FilterTabDescriptor(FilterTabDescriptor.Type.COMPACT_FILTER, "");
    }
}
