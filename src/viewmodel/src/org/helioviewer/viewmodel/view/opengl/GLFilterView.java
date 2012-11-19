package org.helioviewer.viewmodel.view.opengl;

import javax.media.opengl.GL;

import org.helioviewer.viewmodel.changeevent.ChangeEvent;
import org.helioviewer.viewmodel.changeevent.FilterChangedReason;
import org.helioviewer.viewmodel.changeevent.SubImageDataChangedReason;
import org.helioviewer.viewmodel.changeevent.ViewChainChangedReason;
import org.helioviewer.viewmodel.filter.Filter;
import org.helioviewer.viewmodel.filter.GLFilter;
import org.helioviewer.viewmodel.filter.GLFragmentShaderFilter;
import org.helioviewer.viewmodel.filter.GLImageSizeFilter;
import org.helioviewer.viewmodel.filter.GLPostFilter;
import org.helioviewer.viewmodel.view.StandardFilterView;
import org.helioviewer.viewmodel.view.View;
import org.helioviewer.viewmodel.view.ViewHelper;
import org.helioviewer.viewmodel.view.ViewportView;
import org.helioviewer.viewmodel.view.opengl.shader.GLFragmentShaderView;
import org.helioviewer.viewmodel.view.opengl.shader.GLShaderBuilder;
import org.helioviewer.viewmodel.viewport.Viewport;

/**
 * Implementation of FilterView for rendering in OpenGL mode.
 * 
 * <p>
 * Since filters in OpenGL are implemented as shaders, it is not possible to use
 * every filter in OpenGL mode. In particular, only GLFilters should be used.
 * Never the less, the view chain still works, when a filter does not support
 * OpenGL, but OpenGL will not be used to accelerate views beneath that filter.
 * Instead, it switches to standard mode for the remaining views. This behavior
 * is implemented in this class.
 * 
 * <p>
 * For further information on how to use filters, see
 * {@link org.helioviewer.viewmodel.filter} and
 * {@link org.helioviewer.viewmodel.view.StandardFilterView}
 * 
 * <p>
 * For further information about how to build shaders, see
 * {@link org.helioviewer.viewmodel.view.opengl.shader.GLShaderBuilder} as well
 * as the Cg User Manual.
 * 
 * @author Markus Langenberg
 * 
 */
public class GLFilterView extends StandardFilterView implements GLFragmentShaderView {

    protected static GLTextureHelper textureHelper = new GLTextureHelper();
    protected ViewportView viewportView;

    /**
     * {@inheritDoc} This function also sets the image size.
     */
    protected void refilterPrepare() {
        super.refilterPrepare();
        if (filter instanceof GLImageSizeFilter && viewportView != null) {
            Viewport viewport = viewportView.getViewport();
            if (viewport != null) {
                ((GLImageSizeFilter) filter).setImageSize(viewport.getWidth(), viewport.getHeight());
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    public void renderGL(GL gl) {
        if (filter instanceof GLFilter) {

            refilterPrepare();

            if (filter instanceof GLFragmentShaderFilter) {
                gl.glEnable(GL.GL_FRAGMENT_PROGRAM_ARB);
            }

            ((GLFilter) filter).applyGL(gl);

            if (view instanceof GLView) {
                ((GLView) view).renderGL(gl);
            } else {
                if (subimageDataView != null)
                    textureHelper.renderImageDataToScreen(gl, regionView.getRegion(), subimageDataView.getSubimageData(true));
            }

            if (filter instanceof GLPostFilter) {
                ((GLPostFilter) filter).postApplyGL(gl);
            }

            gl.glDisable(GL.GL_FRAGMENT_PROGRAM_ARB);

        } else {
            textureHelper.renderImageDataToScreen(gl, regionView.getRegion(), getSubimageData(true));
        }
    }

    /**
     * {@inheritDoc}
     */
    public void viewChanged(View sender, ChangeEvent aEvent) {
        super.viewChanged(sender, aEvent);
        if (aEvent.reasonOccurred(ViewChainChangedReason.class)) {
            updatePrecomputedViews();
        }
    }

    /**
     * {@inheritDoc}
     */
    public void filterChanged(Filter f) {
        if (!(filter instanceof GLFilter)) {
            super.filterChanged(f);
        } else {
            subimageDataValid = false;

            ChangeEvent event = new ChangeEvent();

            event.addReason(new FilterChangedReason(this, filter));
            event.addReason(new SubImageDataChangedReason(this));

            notifyViewListeners(event);
        }
    }

    /**
     * {@inheritDoc}
     */
    public GLShaderBuilder buildFragmentShader(GLShaderBuilder shaderBuilder) {
        if (!(filter instanceof GLFilter)) {
            return shaderBuilder;
        }

        GLFragmentShaderView nextView = view.getAdapter(GLFragmentShaderView.class);
        if (nextView != null) {
            shaderBuilder = nextView.buildFragmentShader(shaderBuilder);
        }

        if (filter instanceof GLFragmentShaderFilter) {
            shaderBuilder = ((GLFragmentShaderFilter) filter).buildFragmentShader(shaderBuilder);
        }

        return shaderBuilder;
    }

    /**
     * {@inheritDoc}
     */
    protected void updatePrecomputedViews() {
        super.updatePrecomputedViews();
        viewportView = ViewHelper.getViewAdapter(view, ViewportView.class);
    }

}