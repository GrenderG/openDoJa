package com.nttdocomo.ui;

/**
 * Defines an image button.
 */
public final class ImageButton extends Component implements Interactable {
    private Image image;
    private boolean enabled = true;

    /**
     * Creates an image button whose label image is empty.
     * When created, it is visible and user-operable.
     */
    public ImageButton() {
    }

    /**
     * Creates an image button with the specified label image.
     * When created, it is visible and user-operable.
     *
     * @param image the label image
     * @throws NullPointerException if {@code image} is {@code null}
     */
    public ImageButton(Image image) {
        setImage(image);
    }

    /**
     * Sets the label image of the image button.
     *
     * @param image the label image
     * @throws NullPointerException if {@code image} is {@code null}
     */
    public void setImage(Image image) {
        if (image == null) {
            throw new NullPointerException("image");
        }
        _ImageInternalAccess.requireUsable(image);
        this.image = image;
        hostChanged();
    }

    /**
     * Sets the component to the enabled state or the disabled state.
     *
     * @param b {@code true} to enable, or {@code false} to disable
     */
    @Override
    public void setEnabled(boolean b) {
        this.enabled = b;
        if (!b && ownerPanel() != null) ownerPanel().componentBecameUnavailable(this);
        hostChanged();
    }

    /**
     * Requests that focus be set to this component.
     */
    @Override
    public void requestFocus() {
        requestFocusFromOwnerPanel();
    }

    @Override
    boolean acceptsFocusOn(Panel panel) {
        return enabled && super.acceptsFocusOn(panel);
    }
    Image hostImage() { return image; }
    boolean hostEnabled() { return enabled; }
}
