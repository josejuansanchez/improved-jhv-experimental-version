package org.helioviewer.viewmodel.view.opengl;

import java.util.AbstractList;
import java.util.LinkedList;
import java.awt.Color;
import java.awt.Component;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.IntBuffer;

import javax.imageio.ImageIO;
import javax.media.opengl.GL;
import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.GLCanvas;
import javax.media.opengl.GLEventListener;
import javax.media.opengl.glu.GLU;

import org.helioviewer.base.logging.Log;
import org.helioviewer.base.math.Vector2dInt;
import org.helioviewer.viewmodel.changeevent.ChangeEvent;
import org.helioviewer.viewmodel.changeevent.LayerChangedReason;
import org.helioviewer.viewmodel.changeevent.ViewChainChangedReason;
import org.helioviewer.viewmodel.changeevent.LayerChangedReason.LayerChangeType;
import org.helioviewer.viewmodel.region.Region;
import org.helioviewer.viewmodel.renderer.screen.GLScreenRenderGraphics;
import org.helioviewer.viewmodel.renderer.screen.ScreenRenderer;
import org.helioviewer.viewmodel.view.AbstractComponentView;
import org.helioviewer.viewmodel.view.ComponentView;
import org.helioviewer.viewmodel.view.RegionView;
import org.helioviewer.viewmodel.view.SubimageDataView;
import org.helioviewer.viewmodel.view.View;
import org.helioviewer.viewmodel.view.ViewHelper;
import org.helioviewer.viewmodel.view.ViewListener;
import org.helioviewer.viewmodel.view.ViewportView;
import org.helioviewer.viewmodel.view.opengl.shader.GLFragmentShaderView;
import org.helioviewer.viewmodel.view.opengl.shader.GLMinimalFragmentShaderProgram;
import org.helioviewer.viewmodel.view.opengl.shader.GLMinimalVertexShaderProgram;
import org.helioviewer.viewmodel.view.opengl.shader.GLShaderBuilder;
import org.helioviewer.viewmodel.view.opengl.shader.GLShaderHelper;
import org.helioviewer.viewmodel.view.opengl.shader.GLVertexShaderView;
import org.helioviewer.viewmodel.viewport.Viewport;
import org.helioviewer.viewmodel.viewportimagesize.ViewportImageSize;

import com.sun.opengl.util.FPSAnimator;

/**
 * Implementation of ComponentView for rendering in OpenGL mode.
 * 
 * <p>
 * This class starts the tree walk through all the GLViews to draw the final
 * scene. Therefore the class owns a GLCanvas. Note that GLCanvas is a
 * heavyweight component.
 * 
 * <p>
 * For further information about the use of OpenGL within this application, see
 * {@link GLView}.
 * 
 * <p>
 * For further information about the role of the ComponentView within the view
 * chain, see {@link org.helioviewer.viewmodel.view.ComponentView}
 * 
 * @author Markus Langenberg
 */
public class GLComponentView extends AbstractComponentView implements ViewListener, GLEventListener {

    // general
    private GLCanvas canvas;
    private RegionView regionView;

    // render options
    private Color backgroundColor = Color.BLACK;
    private boolean backGroundColorHasChanged = true;
    private float xOffset = 0.0f;
    private float yOffset = 0.0f;
    private AbstractList<ScreenRenderer> postRenderers = new LinkedList<ScreenRenderer>();

    // Helper
    private boolean rebuildShadersRequest = false;
    private GLTextureHelper textureHelper = new GLTextureHelper();
    private GLShaderHelper shaderHelper = new GLShaderHelper();

    // screenshot
    private boolean saveScreenshotRequest = false;
    private String saveScreenshotFormat;
    private File saveScreenshotFile;

    // // fps
    // private int frame = 0;
    // private int frameUpdated = 0;
    // private long timebase = System.currentTimeMillis();

    /**
     * Default constructor.
     * 
     * Also initializes all OpenGL Helper classes.
     */
    public GLComponentView() {
        canvas = new GLCanvas(null, null, GLSharedContext.getSharedContext(), null);
        // Just for testing...
        FPSAnimator animator = new FPSAnimator(canvas, 30);
        animator.start();

        canvas.addGLEventListener(this);
    }

    /**
     * {@inheritDoc}
     */
    public Component getComponent() {
        return canvas;
    }

    /**
     * {@inheritDoc}
     * 
     * Since the screenshot is saved after the next rendering cycle, the result
     * is not available directly after calling this function. It only places a
     * request to save the screenshot.
     */
    public void saveScreenshot(String imageFormat, File outputFile) throws IOException {
        saveScreenshotRequest = true;
        saveScreenshotFormat = imageFormat;
        saveScreenshotFile = outputFile;
    }

    /**
     * {@inheritDoc}
     */
    public void setOffset(Vector2dInt offset) {
        xOffset = offset.getX();
        yOffset = offset.getY();
    }

    /**
     * {@inheritDoc}
     */
    public void addPostRenderer(ScreenRenderer postRenderer) {
        if (postRenderer != null) {
            synchronized (postRenderers) {
                postRenderers.add(postRenderer);
                if (postRenderer instanceof ViewListener) {
                    addViewListener((ViewListener) postRenderer);
                }
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    public void removePostRenderer(ScreenRenderer postRenderer) {
        if (postRenderer != null) {
            synchronized (postRenderers) {
                do {
                    postRenderers.remove(postRenderer);
                    if (postRenderer instanceof ViewListener) {
                        removeViewListener((ViewListener) postRenderer);
                    }
                } while (postRenderers.contains(postRenderer));
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    public AbstractList<ScreenRenderer> getAllPostRenderer() {
        return postRenderers;
    }

    /**
     * {@inheritDoc}
     * 
     * In this case, the canvas is repainted.
     */
    protected synchronized void setViewSpecificImplementation(View newView, ChangeEvent changeEvent) {
        if (newView != null) {
            regionView = newView.getAdapter(RegionView.class);
        }

        canvas.repaint();
    }

    /**
     * {@inheritDoc}
     */
    public void viewChanged(View sender, ChangeEvent aEvent) {

        if (aEvent.reasonOccurred(ViewChainChangedReason.class)) {
            regionView = view.getAdapter(RegionView.class);
        }

        // rebuild shaders, if necessary
        if (aEvent.reasonOccurred(ViewChainChangedReason.class) || (aEvent.reasonOccurred(LayerChangedReason.class) && aEvent.getLastChangedReasonByType(LayerChangedReason.class).getLayerChangeType() == LayerChangeType.LAYER_ADDED)) {
            rebuildShadersRequest = true;
        }

        // inform all listener of the latest change reason
        // frameUpdated++;
        notifyViewListeners(aEvent);
    }

    public void setBackgroundColor(Color color) {
        backgroundColor = color;
        backGroundColorHasChanged = true;
    }

    /**
     * {@inheritDoc}
     */
    public void displayChanged(GLAutoDrawable drawable, boolean modeChanged, boolean deviceChanged) {
    }

    /**
     * Initializes OpenGL.
     * 
     * This function is called when the canvas is visible the first time. It
     * initializes OpenGL by setting some system properties, such as switching
     * on some OpenGL features. Apart from that, the function also calls
     * {@link GLTextureHelper#initHelper(GL)}.
     * 
     * <p>
     * Note, that this function should not be called by any user defined
     * function. It is part of the GLEventListener and invoked by the OpenGL
     * thread.
     * 
     * @param drawable
     *            GLAutoDrawable passed by the OpenGL thread
     */
    public void init(GLAutoDrawable drawable) {
        GLSharedContext.setSharedContext(drawable.getContext());

        final GL gl = drawable.getGL();

        textureHelper.delAllTextures(gl);
        GLTextureHelper.initHelper(gl);

        shaderHelper.delAllShaderIDs(gl);

        gl.glShadeModel(GL.GL_FLAT);
        gl.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        gl.glEnable(GL.GL_TEXTURE_1D);
        gl.glEnable(GL.GL_TEXTURE_2D);
        gl.glEnable(GL.GL_BLEND);
        gl.glEnable(GL.GL_POINT_SMOOTH);
        gl.glBlendFunc(GL.GL_SRC_ALPHA, GL.GL_ONE_MINUS_SRC_ALPHA);

        gl.glColor3f(1.0f, 1.0f, 1.0f);
    }

    /**
     * Reshapes the viewport.
     * 
     * This function is called, whenever the canvas is resized. It ensures, that
     * the perspective never gets corrupted.
     * 
     * <p>
     * Note, that this function should not be called by any user defined
     * function. It is part of the GLEventListener and invoked by the OpenGL
     * thread.
     * 
     * @param drawable
     *            GLAutoDrawable passed by the OpenGL thread
     * @param x
     *            New x-offset on the screen
     * @param y
     *            New y-offset on the screen
     * @param width
     *            New width of the canvas
     * @param height
     *            New height of the canvas
     */
    public void reshape(GLAutoDrawable drawable, int x, int y, int width, int height) {
        final GL gl = drawable.getGL();

        gl.setSwapInterval(1);

        gl.glViewport(0, 0, width, height);
        gl.glMatrixMode(GL.GL_PROJECTION);
        gl.glLoadIdentity();

        gl.glOrtho(0, width, 0, height, -1, 1);

        gl.glMatrixMode(GL.GL_MODELVIEW);
        gl.glLoadIdentity();
    }

    /**
     * Displays the scene on the screen.
     * 
     * This is the most important function of this class, it is responsible for
     * rendering the entire scene to the screen. Therefore, it starts the tree
     * walk. After that, all post renderes are called.
     * 
     * <p>
     * Note, that this function should not be called by any user defined
     * function. It is part of the GLEventListener and invoked by the OpenGL
     * thread.
     * 
     * @param drawable
     *            GLAutoDrawable passed by the OpenGL thread
     * @see ComponentView#addPostRenderer(ScreenRenderer)
     */
    public synchronized void display(GLAutoDrawable drawable) {

        if (view == null || canvas.getSize().width <= 0 || canvas.getSize().height <= 0) {
            return;
        }
        final GL gl = drawable.getGL();

        // Set clear color (= backgroundcolor)
        if (backGroundColorHasChanged) {
            gl.glClearColor(backgroundColor.getRed() / 255.0f, backgroundColor.getGreen() / 255.0f, backgroundColor.getBlue() / 255.0f, backgroundColor.getAlpha() / 255.0f);

            backGroundColorHasChanged = false;
        }

        // Rebuild all shaders, if necessary
        if (rebuildShadersRequest) {
            rebuildShaders(gl);
        }

        // Set up screen
        gl.glClear(GL.GL_COLOR_BUFFER_BIT);
        gl.glLoadIdentity();

        Viewport viewport = view.getAdapter(ViewportView.class).getViewport();
        ViewportImageSize viewportImageSize = ViewHelper.calculateViewportImageSize(view);

        if (viewportImageSize != null) {
            // Draw image
            gl.glPushMatrix();

            Region region = regionView.getRegion();

            float xOffsetFinal = xOffset;
            float yOffsetFinal = yOffset;

            if (mainImagePanelSize != null) {
                if (viewportImageSize.getWidth() < mainImagePanelSize.getX()) {
                    xOffsetFinal += (mainImagePanelSize.getX() - viewportImageSize.getWidth()) / 2;
                }
                if (viewportImageSize.getHeight() < mainImagePanelSize.getY()) {
                    yOffsetFinal += (mainImagePanelSize.getY() - viewportImageSize.getHeight()) / 2;
                }
            }

            gl.glTranslatef(xOffsetFinal, viewport.getHeight() - viewportImageSize.getHeight() - yOffsetFinal, 0.0f);
            gl.glScalef(viewportImageSize.getWidth() / (float) region.getWidth(), viewportImageSize.getHeight() / (float) region.getHeight(), 1.0f);
            gl.glTranslated(-region.getCornerX(), -region.getCornerY(), 0.0);

            if (view instanceof GLView) {
                ((GLView) view).renderGL(gl);
            } else {
                textureHelper.renderImageDataToScreen(gl, view.getAdapter(RegionView.class).getRegion(), view.getAdapter(SubimageDataView.class).getSubimageData(true));
            }
            gl.glPopMatrix();
        }

        // Draw post renderer

        gl.glTranslatef(0.0f, viewport.getHeight(), 0.0f);
        gl.glScalef(1.0f, -1.0f, 1.0f);

        GLScreenRenderGraphics glRenderer = new GLScreenRenderGraphics(gl);
        synchronized (postRenderers) {
            for (ScreenRenderer r : postRenderers) {
                r.render(glRenderer);
            }
        }

        // Save Screenshot, if requested
        if (saveScreenshotRequest) {

            // Read pixels
            IntBuffer intBuffer = IntBuffer.allocate(canvas.getWidth() * canvas.getHeight());
            gl.glReadPixels(0, 0, canvas.getWidth(), canvas.getHeight(), GL.GL_BGRA, GL.GL_UNSIGNED_INT_8_8_8_8_REV, intBuffer);

            // Save to image
            BufferedImage screenshot = new BufferedImage(canvas.getWidth(), canvas.getHeight(), BufferedImage.TYPE_INT_RGB);
            screenshot.setRGB(0, 0, canvas.getWidth(), canvas.getHeight(), intBuffer.array(), 0, canvas.getWidth());

            // Flip image
            AffineTransform tx = AffineTransform.getScaleInstance(1, -1);
            tx.translate(0, -screenshot.getHeight(null));
            AffineTransformOp op = new AffineTransformOp(tx, AffineTransformOp.TYPE_NEAREST_NEIGHBOR);
            screenshot = op.filter(screenshot, null);

            // save image
            try {
                ImageIO.write(screenshot, saveScreenshotFormat, saveScreenshotFile);
            } catch (IOException e) {
                e.printStackTrace();
            }
            saveScreenshotRequest = false;
        }

        // // fps counter;
        // frame++;
        // long time = System.currentTimeMillis();
        //
        // if (time - timebase > 1000) {
        // float factor = 1000.0f/(time-timebase);
        // float fps = frame*factor;
        // float fps2 = frameUpdated*factor;
        // timebase = time;
        // frame = 0;
        // frameUpdated = 0;
        // System.out.println(fps2 + ", " + fps);
        // }

        // check for errors
        int errorCode = gl.glGetError();
        if (errorCode != GL.GL_NO_ERROR) {
            GLU glu = new GLU();
            Log.error("OpenGL Error (" + errorCode + ") : " + glu.gluErrorString(errorCode));
        }
    }

    /**
     * Start rebuilding all shaders.
     * 
     * This function is called, whenever the shader structure of the whole view
     * chain may have changed, e.g. when new views are added.
     * 
     * @param gl
     *            Valid reference to the current gl object
     */
    private void rebuildShaders(GL gl) {

        rebuildShadersRequest = false;
        shaderHelper.delAllShaderIDs(gl);

        GLFragmentShaderView fragmentView = view.getAdapter(GLFragmentShaderView.class);
        if (fragmentView != null) {
            // create new shader builder
            GLShaderBuilder newShaderBuilder = new GLShaderBuilder(gl, GL.GL_FRAGMENT_PROGRAM_ARB);

            // fill with standard values
            GLMinimalFragmentShaderProgram minimalProgram = new GLMinimalFragmentShaderProgram();
            minimalProgram.build(newShaderBuilder);

            // fill with other filters and compile
            fragmentView.buildFragmentShader(newShaderBuilder).compile();
        }

        GLVertexShaderView vertexView = view.getAdapter(GLVertexShaderView.class);
        if (vertexView != null) {
            // create new shader builder
            GLShaderBuilder newShaderBuilder = new GLShaderBuilder(gl, GL.GL_VERTEX_PROGRAM_ARB);

            // fill with standard values
            GLMinimalVertexShaderProgram minimalProgram = new GLMinimalVertexShaderProgram();
            minimalProgram.build(newShaderBuilder);

            // fill with other filters and compile
            vertexView.buildVertexShader(newShaderBuilder).compile();
        }
    }

}
