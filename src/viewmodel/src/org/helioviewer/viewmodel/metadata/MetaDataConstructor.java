package org.helioviewer.viewmodel.metadata;

/**
 * Factory for creating meta data out of a meta data container.
 * 
 * <p>
 * This factory ensures, that the correct type of meta data is generated.
 * Currently, it supports {@link HelioviewerMetaData}, its extension
 * {@link HelioviewerOcculterMetaData} and {@link PixelBasedMetaData} as a
 * fallback solution.
 * 
 * @author Ludwig Schmidt
 * 
 */
public class MetaDataConstructor {

    /**
     * Returns an implementation of MetaData.
     * 
     * The function tries to search which implementation matches the image
     * contents best.
     * 
     * @param mdc
     *            Meta data container serving as a base for the construction
     * @return Implementation of MetaData
     */
    public static MetaData getMetaData(MetaDataContainer mdc) {

        // Try occulter meta data
        HelioviewerOcculterMetaData occulterMetaData = new HelioviewerOcculterMetaData(mdc);

        // If the inner radius is 0, then there wasn't any
        // supported meta data available
        if (occulterMetaData.getInnerPhysicalOcculterRadius() != 0.0)
            return occulterMetaData;

        // Try helioviewer meta data
        HelioviewerMetaData hvMetaData = new HelioviewerMetaData(mdc);

        // If the sun radius is -1, there wasn't any
        // supported meta data available
        if (hvMetaData.getSunPixelRadius() == -1) {
            return new PixelBasedMetaData(mdc.getPixelWidth(), mdc.getPixelHeight());
        } else {
            return hvMetaData;
        }
    }
}
