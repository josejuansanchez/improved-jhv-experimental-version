package org.helioviewer.viewmodel.view;

import org.helioviewer.viewmodel.changeevent.ChangeEvent;
import org.helioviewer.viewmodel.changeevent.FilterChangedReason;
import org.helioviewer.viewmodel.changeevent.SubImageDataChangedReason;
import org.helioviewer.viewmodel.changeevent.ViewChainChangedReason;
import org.helioviewer.viewmodel.filter.Filter;
import org.helioviewer.viewmodel.filter.FilterListener;
import org.helioviewer.viewmodel.filter.MetaDataFilter;
import org.helioviewer.viewmodel.filter.ObservableFilter;
import org.helioviewer.viewmodel.filter.RegionFilter;
import org.helioviewer.viewmodel.filter.StandardFilter;
import org.helioviewer.viewmodel.imagedata.ImageData;

/**
 * Implementation of FilterView, providing the capability to apply filters on
 * the image.
 * 
 * <p>
 * This view allows to filter the image data by using varies filters. Every time
 * the image data changes, the view calls the filter to calculate the new image
 * data. Apart from that, it feeds the filter with all other informations to do
 * its job, such as the current region, meta data or the full image.
 * 
 * <p>
 * For further information on how to use filters, see
 * {@link org.helioviewer.viewmodel.filter}
 * 
 * @author Ludwig Schmidt
 * @author Markus Langenberg
 * 
 */
public class StandardFilterView extends AbstractSubimageDataView implements FilterView, ViewListener, FilterListener {

    protected Filter filter;

    protected RegionView regionView;
    protected MetaDataView metaDataView;

    /**
     * {@inheritDoc}
     */
    public Filter getFilter() {
        return filter;
    }

    /**
     * {@inheritDoc}
     */
    public void setFilter(Filter f) {
        if (filter != null && (filter instanceof ObservableFilter)) {
            ((ObservableFilter) filter).removeFilterListener(this);
        }

        filter = f;

        if (filter != null && (filter instanceof ObservableFilter)) {
            ((ObservableFilter) filter).addFilterListener(this);
        }

        subimageDataValid = false;

        // join change reasons to a change event
        ChangeEvent event = new ChangeEvent();

        event.addReason(new FilterChangedReason(this, filter));
        event.addReason(new SubImageDataChangedReason(this));

        notifyViewListeners(event);
    }

    /**
     * {@inheritDoc}
     */
    public ImageData getSubimageData(boolean readOnly) {
        if (filter instanceof StandardFilter) {
            return super.getSubimageData(readOnly);
        } else if (subimageDataView != null) {
            return subimageDataView.getSubimageData(readOnly);
        } else
            return null;
    }

    /**
     * Prepares the actual filter process.
     * 
     * This function feeds the filter with all the additional informations it
     * needs to do its job, such as the region, meta data and the full image.
     */
    protected void refilterPrepare() {
        if (filter instanceof RegionFilter && regionView != null) {
            ((RegionFilter) filter).setRegion(regionView.getRegion());
        }
        if (filter instanceof MetaDataFilter && metaDataView != null) {
            ((MetaDataFilter) filter).setMetaData(metaDataView.getMetaData());
        }
    }

    /**
     * Refilters the image.
     * 
     * Calls the filter and fires a ChangeEvent afterwards.
     */
    protected ImageData performImageOperation(ImageData source) {
        ImageData result = null;
        if (filter != null) {
            refilterPrepare();
            if (filter instanceof StandardFilter) {
                result = ((StandardFilter) filter).apply(source);
            } else {
                throw new RuntimeException("Unexpected state in" + this.getClass() + ": Filter not StandardFilter in method performImageOperation(IamgeData)");
            }
        }
        return result;
    }

    /**
     * {@inheritDoc}
     * 
     * In this case, refilters the image, if there is one.
     */
    protected void setViewSpecificImplementation(View newView, ChangeEvent changeEvent) {
        super.setViewSpecificImplementation(newView, changeEvent);
        updatePrecomputedViews();
    }

    /**
     * {@inheritDoc}
     * 
     * In case the image data has changed, applies the filter.
     */
    public void viewChanged(View sender, ChangeEvent aEvent) {
        if (aEvent.reasonOccurred(ViewChainChangedReason.class)) {
            updatePrecomputedViews();
        }
        super.viewChanged(sender, aEvent);

        notifyViewListeners(aEvent);
    }

    /**
     * {@inheritDoc}
     */
    public void filterChanged(Filter f) {
        subimageDataValid = false;

        ChangeEvent event = new ChangeEvent();

        event.addReason(new FilterChangedReason(this, filter));
        event.addReason(new SubImageDataChangedReason(this));

        notifyViewListeners(event);
    }

    /**
     * Updates the precomputed results for different view adapters.
     * 
     * This adapters are precomputed to avoid unnecessary overhead appearing
     * when doing this every frame.
     */
    protected void updatePrecomputedViews() {
        regionView = ViewHelper.getViewAdapter(view, RegionView.class);
        metaDataView = ViewHelper.getViewAdapter(view, MetaDataView.class);
        subimageDataView = ViewHelper.getViewAdapter(view, SubimageDataView.class);
    }
}
