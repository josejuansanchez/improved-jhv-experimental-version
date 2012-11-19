package org.helioviewer.viewmodel.view;

import org.helioviewer.base.math.Vector2dInt;

public abstract class AbstractComponentView extends AbstractBasicView implements ComponentView {

    protected volatile Vector2dInt mainImagePanelSize;

    public void updateMainImagePanelSize(Vector2dInt size) {
        mainImagePanelSize = size;
    }
}
