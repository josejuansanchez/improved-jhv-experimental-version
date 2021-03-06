package org.helioviewer.viewmodel.view.opengl.shader;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.CharBuffer;
import java.util.LinkedList;
import java.util.List;

import javax.media.opengl.GL;
import javax.media.opengl.glu.GLU;

import org.apache.log4j.Level;
import org.helioviewer.base.FileUtils;
import org.helioviewer.base.logging.Log;
import org.helioviewer.viewmodel.renderer.GLCommonRenderGraphics;
import org.helioviewer.viewmodel.view.opengl.GLSceneSaver;

/**
 * Helper class to handle OpenGL shaders.
 * 
 * <p>
 * This class provides a lot of useful functions to handle shaders in OpenGL,
 * including compiling them. Therefore, it uses the Cg stand-alone compiler.
 * 
 * <p>
 * For further information about how to build shaders, see
 * {@link GLShaderBuilder} as well as the Cg User Manual.
 * 
 * @author Markus Langenberg
 */
public class GLShaderHelper {

    private static String tmpPath;

    private static int maxTextureIndirections = 0;

    private static LinkedList<Integer> allShaders = new LinkedList<Integer>();
    private static int shaderCurrentlyBound = 0;

    /**
     * Initializes the helper.
     * 
     * This function has to be called before using any other helper function.
     * 
     * @param _tmpPath
     *            Location where to put temporary files.
     */
    public static void initHelper(GL gl, String _tmpPath) {
        Log.debug(">> GLShaderHelper.initHelper(GL gl, String _tmpPath) > Initialize helper functions");
        tmpPath = _tmpPath;

        Log.debug(">> GLShaderHelper.initHelper(GL gl, String _tmpPath) > temp path: " + tmpPath);
        int tmp[] = new int[1];
        gl.glGetProgramivARB(GL.GL_FRAGMENT_PROGRAM_ARB, GL.GL_MAX_PROGRAM_TEX_INDIRECTIONS_ARB, tmp, 0);
        maxTextureIndirections = tmp[0];
        Log.debug(">> GLShaderHelper.initHelper(GL gl, String _tmpPath) > max texture indirections: " + maxTextureIndirections);
    }

    /**
     * Returns maximum number of texture indirections supported by OpenGL
     * shaders.
     * 
     * @return Maximum number of texture indirections supported by OpenGL
     *         shaders
     */
    public static int getMaxTextureIndirections() {
        return maxTextureIndirections;
    }

    /**
     * Generates a new shader.
     * 
     * @param gl
     *            Valid reference to the current gl object
     * @return new shader id
     */
    public int genShaderID(GL gl) {
        int[] tmp = new int[1];
        gl.glGenProgramsARB(1, tmp, 0);
        allShaders.add(tmp[0]);
        return tmp[0];
    }

    /**
     * Deletes an existing shader.
     * 
     * It is not possible to delete a shader that has not been generated by this
     * helper.
     * 
     * @param gl
     *            Valid reference to the current gl object
     * @param shaderID
     *            Shader id to delete
     */
    public void delShaderID(GL gl, int shaderID) {
        if (!allShaders.contains(shaderID))
            return;

        if (gl == null) {
            gl = GLU.getCurrentGL();
        }

        allShaders.remove(allShaders.indexOf(shaderID));

        int[] tmp = new int[1];
        tmp[0] = shaderID;
        gl.glDeleteProgramsARB(1, tmp, 0);
    }

    /**
     * Deletes all textures generates by this helper.
     * 
     * This might be necessary to clean up after not using OpenGL any more.
     * 
     * @param gl
     *            Valid reference to the current gl object
     */
    public void delAllShaderIDs(GL gl) {
        GLCommonRenderGraphics.clearShader();
        GLSceneSaver.clearShader();
        for (int i = allShaders.size() - 1; i >= 0; i--) {
            delShaderID(gl, allShaders.get(i));
        }
    }

    /**
     * Activates a shader.
     * 
     * @param gl
     *            Valid reference to the current gl object
     * @param target
     *            Shader type, has to be GL_VERTEX_PROGRAM_ARB or
     *            GL_FRAGMENT_PROGRAM_ARB
     * @param shader
     *            Shader id
     */
    public void bindShader(GL gl, int target, int shader) {
        if (shader != shaderCurrentlyBound) {
            shaderCurrentlyBound = shader;
            gl.glBindProgramARB(target, shader);
        }
    }

    /**
     * Compiles a program and loads the result to a given shader.
     * 
     * <p>
     * Note, that the only mechanism to display errors is the general check for
     * OpenGL errors during the rendering process. If the given program contains
     * errors, "invalid operation" should be displayed on the console. To verify
     * the exact error, try calling the Cg stand- alone compiler from the
     * console.
     * 
     * @param gl
     *            Valid reference to the current gl object
     * @param programType
     *            Shader type, has to be GL_VERTEX_PROGRAM_ARB or
     *            GL_FRAGMENT_PROGRAM_ARB
     * @param source
     *            Location of the source file.
     * @param target
     *            Shader id to put the compiled program
     */
    public void compileProgram(GL gl, int programType, URL source, int target) {
        compileProgram(gl, programType, getContents(source), target);
    }

    /**
     * Compiles a program and loads the result to a given shader.
     * 
     * <p>
     * Note, that the only mechanism to display errors is the general check for
     * OpenGL errors during the rendering process. If the given program contains
     * errors, "invalid operation" should be displayed on the console. To verify
     * the exact error, try calling the Cg stand- alone compiler from the
     * console.
     * 
     * @param gl
     *            Valid reference to the current gl object
     * @param programType
     *            Shader type, has to be GL_VERTEX_PROGRAM_ARB or
     *            GL_FRAGMENT_PROGRAM_ARB
     * @param source
     *            Complete program code, given in Cg.
     * @param target
     *            Shader id to put the compiled program
     */
    public void compileProgram(GL gl, int programType, String source, int target) {
        File tmpOut = new File(tmpPath + "tmp.cg");
        File tmpIn = new File(tmpPath + "tmp.asm");

        if (tmpIn.exists()) {
            tmpIn.delete();
        }
        tmpIn.deleteOnExit();
        tmpOut.deleteOnExit();

        putContents(tmpOut, source);

        String profile = programType == GL.GL_FRAGMENT_PROGRAM_ARB ? "arbfp1" : "arbvp1";
        List<String> args = new LinkedList<String>();
        args.add("-profile");
        args.add(profile);
        args.add("-o");
        args.add(tmpPath + "tmp.asm");
        args.add(tmpPath + "tmp.cg");

        try {
            Process p = FileUtils.invokeExecutable("cgc", args);
            FileUtils.logProcessOutput(p, "cgc", Level.DEBUG, true);
            p.waitFor();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        tmpIn = new File(tmpPath + "tmp.asm");

        if (!tmpIn.exists()) {
            Log.error("Error while compiling shader program:");
            Log.error(source);
            return;
        }

        String compiledProgram = getContents(tmpIn);

        gl.glBindProgramARB(programType, target);

        CharBuffer programBuffer = CharBuffer.wrap(compiledProgram);
        gl.glProgramStringARB(programType, GL.GL_PROGRAM_FORMAT_ASCII_ARB, compiledProgram.length(), programBuffer.toString());

    }

    /**
     * Reads the contents of a file and puts them to a String.
     * 
     * @param aFile
     *            Location of the file to read
     * @return contents of the file
     */
    private String getContents(URL aFile) {

        StringBuilder contents = new StringBuilder();

        try {
            BufferedReader input = new BufferedReader(new InputStreamReader(aFile.openStream()));
            try {
                String line = null; // not declared within while loop

                while ((line = input.readLine()) != null) {
                    contents.append(line);
                    contents.append(System.getProperty("line.separator"));
                }
            } finally {
                input.close();
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }

        return contents.toString();
    }

    /**
     * Reads the contents of a file and puts them to a String.
     * 
     * @param aFile
     *            File to read
     * @return contents of the file
     */
    private String getContents(File aFile) {

        StringBuilder contents = new StringBuilder();

        try {
            BufferedReader input = new BufferedReader(new FileReader(aFile));
            try {
                String line = null; // not declared within while loop

                while ((line = input.readLine()) != null) {
                    contents.append(line);
                    contents.append(System.getProperty("line.separator"));
                }
            } finally {
                input.close();
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }

        return contents.toString();
    }

    /**
     * Writes String to a File.
     * 
     * @param aFile
     *            Output file
     * @param content
     *            Data to write
     */
    private void putContents(File aFile, String content) {
        try {
            BufferedWriter output = new BufferedWriter(new FileWriter(aFile));
            output.write(content);
            output.close();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
}
