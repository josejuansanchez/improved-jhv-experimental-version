package org.helioviewer.viewmodel.view;

import org.helioviewer.viewmodel.changeevent.ChangeEvent;
import org.helioviewer.viewmodel.changeevent.SubImageDataChangedReason;
import org.helioviewer.viewmodel.changeevent.ViewChainChangedReason;
import org.helioviewer.viewmodel.imagedata.ImageData;
import org.helioviewer.viewmodel.imagedata.JavaBufferedImageData;

/**
 * This class represents the structure of a normal SubimageDataView (except
 * InfoImageViews and LayeredViews).
 * 
 * A SubimageDataView as implemented here may allow other views to overwrite the
 * result thereby invalidating it. The result is passed upwards in the chain and
 * may be overwritten in order to save memory. If the image is needed again it
 * has to be recalculated.
 * 
 * Each buffered image view which manipulates the image data (and therefore
 * should implement SubimageDataView) should use the DataBufferPool object
 * associated with the ImageData to get new buffer instances.
 * 
 * @see org.helioviewer.viewmodel.view.bufferedimage.DataBufferPool
 * 
 * @author Andre Dau
 * 
 */
public abstract class AbstractSubimageDataView extends AbstractBasicView implements SubimageDataView {

    protected boolean subimageDataValid = false;
    protected ImageData imageData = null;
    protected SubimageDataView subimageDataView = null;

    public void viewChanged(View sender, ChangeEvent aEvent) {
        if (aEvent.reasonOccurred(ViewChainChangedReason.class)) {
            if (subimageDataValid && imageData != null && imageData instanceof JavaBufferedImageData) {
                JavaBufferedImageData javaImageData = (JavaBufferedImageData) imageData;
                (javaImageData).getDataBufferPool().releaseBufferedImage(javaImageData.getBufferedImage());
            }
            subimageDataView = getView().getAdapter(SubimageDataView.class);
            subimageDataValid = false;
            imageData = null;
        }
        if (aEvent.reasonOccurred(SubImageDataChangedReason.class)) {
            if (subimageDataValid && imageData != null && imageData instanceof JavaBufferedImageData) {
                JavaBufferedImageData javaImageData = (JavaBufferedImageData) imageData;
                (javaImageData).getDataBufferPool().releaseBufferedImage(javaImageData.getBufferedImage());
            }
            subimageDataValid = false;
            imageData = null;
        }
    }

    public ImageData getSubimageData(boolean readOnly) {
        if (subimageDataValid && imageData != null) {
            subimageDataValid = readOnly;
            return imageData;
        } else if (subimageDataView != null) {
            ImageData source = subimageDataView.getSubimageData(false);
            if (source != null) {
                imageData = null;
                imageData = performImageOperation(source);
                if (imageData == null || !readOnly) {
                    subimageDataValid = false;
                    ImageData data = imageData;
                    imageData = null;
                    return data;
                }
                subimageDataValid = true;
                return imageData;
            }
        }
        return null;
    }

    public boolean isSubimageDataValid() {
        return subimageDataValid;
    }

    @Override
    protected void setViewSpecificImplementation(View newView, ChangeEvent changeEvent) {
        if (getView() != null) {
            subimageDataView = getView().getAdapter(SubimageDataView.class);
            changeEvent.addReason(new SubImageDataChangedReason(this));
        }
        imageData = null;
        subimageDataValid = false;
    }

    abstract protected ImageData performImageOperation(ImageData source);

}
