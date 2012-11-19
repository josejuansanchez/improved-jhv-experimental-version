package org.helioviewer.viewmodel.view;

import java.util.AbstractMap;

/**
 * View to synchronize different view chains.
 * 
 * <p>
 * It is possible to use multiple view chains side by side without any problems,
 * but there usually, there is no connection between them. Sometimes, it might
 * be useful have the ability to react on changes in another view chain.
 * Therefore, the SynchronizeView can be build into a view chain and listen to
 * multiple another view chains. On changes these observed view chains, it can
 * propagate these changes into the own view chain, if necessary.
 * 
 * @author Stephan Pagel
 * 
 */
public interface SynchronizeView extends ModifiableInnerViewView {

    /**
     * Sets the reference of a view of another view chain which shall be
     * observed.
     * 
     * The synchronize view registers itself as a listener in the given view.
     * 
     * To remove the observedView, call setObservedView(null).
     * 
     * @param aView
     *            View to observe
     */
    public void setObservedView(View aView);

    /**
     * Returns the observed view of another view chain.
     * 
     * @return Observed view of another view chain.
     * @see #setObservedView(View)
     */
    public View getObservedView();

    /**
     * Gets the corresponding view for a given view from the observed view
     * chain.
     * 
     * @param aView
     *            view from the observed view chain
     * @return corresponding image info view
     */
    public ImageInfoView getCorrespondingView(ImageInfoView aView);

    /**
     * Sets the view mapping.
     * 
     * The view mapping is used to find corresponding views from the observed
     * view chain within the own view chain.
     * 
     * <p>
     * <b>Only use this function, if you know exactly what you are doing!</b>
     * 
     * @param map
     *            New view mapping
     * @see #getViewMapping()
     */
    public void setViewMapping(AbstractMap<ImageInfoView, ImageInfoView> map);

    /**
     * Gets the view mapping.
     * 
     * The view mapping is used to find corresponding views from the observed
     * view chain within the own view chain.
     * 
     * <p>
     * <b>Only use this function, if you know exactly what you are doing!</b>
     * 
     * @return Current view mapping
     * @see #setViewMapping
     */
    public AbstractMap<ImageInfoView, ImageInfoView> getViewMapping();
}
