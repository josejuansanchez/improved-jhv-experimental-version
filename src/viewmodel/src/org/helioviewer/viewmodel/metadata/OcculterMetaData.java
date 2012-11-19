package org.helioviewer.viewmodel.metadata;

/**
 * Meta data providing informations about the occulter used to take the picture.
 * 
 * <p>
 * Some solar images are taken using an occulter, to shield the sun itself. That
 * kind of images appear to have a black disc in the center of the image. This
 * interface provides informations about the size of the occulter in physical
 * coordinates, so they can be easily compared to the region information.
 * 
 * @author Markus Langenberg
 * 
 */
public interface OcculterMetaData {

    /**
     * Returns the physical inner radius of the occulter.
     * 
     * @return Physical inner radius of the occulter
     */
    public double getInnerPhysicalOcculterRadius();

    /**
     * Returns the physical outer radius of the occulter.
     * 
     * @return Physical outer radius of the occulter
     */
    public double getOuterPhysicalOcculterRadius();
}
