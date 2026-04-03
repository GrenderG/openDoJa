package com.nttdocomo.opt.ui;

import com.nttdocomo.system.StoreException;
import com.nttdocomo.ui.UIException;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages optional illumination display data.
 */
public class IlluminationDisplay {
    /**
     * Theme identifier for package.
     */
    public static final int THEME_PACKAGE = 0;

    private static final Map<Integer, byte[]> DATA = new ConcurrentHashMap<>();
    private static volatile byte[] previewData;
    private static volatile int themeId = -1;

    /**
     * Applications cannot create this object directly.
     */
    protected IlluminationDisplay() {
    }

    /**
     * Saves illumination data to the specified native slot.
     *
     * @param illuminationDataId the target slot ID
     * @param illuminationData the data to save
     * @throws UIException if the data cannot be stored
     */
    public static void setIlluminationDisplay(int illuminationDataId, byte[] illuminationData) throws UIException {
        if (illuminationData == null) {
            throw new NullPointerException("illuminationData");
        }
        if (illuminationDataId < 0) {
            throw new UIException(UIException.UNDEFINED, "illuminationDataId");
        }
        DATA.put(illuminationDataId, illuminationData.clone());
    }

    /**
     * Starts or stops preview of the specified illumination data.
     *
     * @param illuminationData the data to preview, or {@code null} to stop the current preview
     * @throws UIException if the data format is not supported
     */
    public static void preview(byte[] illuminationData) throws UIException {
        previewData = illuminationData == null ? null : illuminationData.clone();
    }

    /**
     * Applies the specified illumination data as the package theme.
     *
     * @param target the theme target
     * @param illuminationDataId the stored data ID to apply
     * @throws StoreException if the data does not exist
     */
    public static void setIlluminationTheme(int target, int illuminationDataId) throws StoreException {
        if (target != THEME_PACKAGE) {
            throw new IllegalArgumentException("target");
        }
        if (!DATA.containsKey(illuminationDataId)) {
            throw new StoreException(StoreException.NOT_FOUND);
        }
        themeId = illuminationDataId;
    }

    /**
     * Returns the stored illumination data.
     *
     * @param illuminationDataId the data ID
     * @return the stored data
     * @throws StoreException if the data does not exist
     */
    public static byte[] getIlluminationDisplay(int illuminationDataId) throws StoreException {
        byte[] data = DATA.get(illuminationDataId);
        if (data == null) {
            throw new StoreException(StoreException.NOT_FOUND);
        }
        return data.clone();
    }

    static byte[] previewData() {
        return previewData == null ? null : previewData.clone();
    }

    static int themeId() {
        return themeId;
    }
}
