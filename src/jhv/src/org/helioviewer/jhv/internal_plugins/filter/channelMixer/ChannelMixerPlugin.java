package org.helioviewer.jhv.internal_plugins.filter.channelMixer;

import org.helioviewer.viewmodel.filter.Filter;
import org.helioviewer.viewmodel.view.FilterView;
import org.helioviewer.viewmodelplugin.filter.FilterPanel;
import org.helioviewer.viewmodelplugin.filter.FilterTabDescriptor;
import org.helioviewer.viewmodelplugin.filter.SimpleFilterContainer;

/**
 * Plugin for modifying the color mask of an image
 * 
 * <p>
 * The plugin manages a filter for modifying the color mask of an image and
 * three check boxes to change it.
 * 
 * @author Markus Langenberg
 * 
 */
public class ChannelMixerPlugin extends SimpleFilterContainer {

    /**
     * {@inheritDoc}
     */
    @Override
    protected Filter getFilter() {
        return new ChannelMixerFilter();
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
    @Override
    protected FilterPanel getPanel() {
        return new ChannelMixerPanel();
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
        return "Channel Mixer";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected FilterTabDescriptor getFilterTab() {
        return new FilterTabDescriptor(FilterTabDescriptor.Type.COMPACT_FILTER, "");
    }
}
