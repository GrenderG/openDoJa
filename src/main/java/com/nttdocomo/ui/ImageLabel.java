package com.nttdocomo.ui;

/**
 * Defines an image label.
 */
public final class ImageLabel extends Component {
    private Image image;

    /**
     * Creates a label without specifying a label image.
     * When created, it is visible.
     */
    public ImageLabel() {
    }

    /**
     * Creates a label with the specified label image.
     * When created, it is visible.
     *
     * @param image the label image
     * @throws NullPointerException if {@code image} is {@code null}
     */
    public ImageLabel(Image image) {
        setImage(image);
    }

    /**
     * Sets the label image of the component.
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

    Image hostImage() { return image; }
}
