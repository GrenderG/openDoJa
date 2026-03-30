package com.nttdocomo.opt.device;

import com.nttdocomo.device.DeviceException;

/**
 * Provides the means to access the native radio-tuner function.
 * FM radio tuners and AM radio tuners are used to play radio audio.
 * For an FM radio tuner, the frequencies for TV1, TV2, and TV3 can also be used.
 */
public class RadioTuner {
    private static final int FM_MIN_FREQUENCY = 76000;
    private static final int FM_MAX_FREQUENCY = 90000;
    private static final int AM_MIN_FREQUENCY = 522;
    private static final int AM_MAX_FREQUENCY = 1629;
    private static final int DEFAULT_VOLUME = 50;
    /** Attribute value that represents an FM radio tuner (=0). */
    public static final int TUNERTYPE_FM = 0;
    /** Attribute value that represents an AM radio tuner (=1). */
    public static final int TUNERTYPE_AM = 1;
    /** Attribute value for the TV1 frequency (=95750). */
    public static final int FREQUENCY_TV1 = 95750;
    /** Attribute value for the TV2 frequency (=101750). */
    public static final int FREQUENCY_TV2 = 101750;
    /** Attribute value for the TV3 frequency (=107750). */
    public static final int FREQUENCY_TV3 = 107750;
    /** Attribute value that represents auto stereo mode (=10). */
    public static final int MODE_AUTO = 10;
    /** Attribute value that represents monaural mode (=20). */
    public static final int MODE_MONAURAL = 20;
    /** Attribute value that represents speaker output (=100). */
    public static final int OUT_SPEAKER = 100;
    /** Attribute value that represents earphone output (=200). */
    public static final int OUT_EARPHONE = 200;
    /** Attribute value that represents auto output (=300). */
    public static final int OUT_AUTO = 300;

    private static final int[] SUPPORTED_TUNER_TYPES = {TUNERTYPE_FM, TUNERTYPE_AM};
    private static final RadioTuner INSTANCE = new RadioTuner();

    private final int[] frequencies = {FM_MIN_FREQUENCY, AM_MIN_FREQUENCY};
    private int tunerType = TUNERTYPE_FM;
    private int volume = DEFAULT_VOLUME;
    private int stereoMode = MODE_AUTO;
    private int output = OUT_AUTO;
    private boolean turnedOn;
    private boolean seeking;
    private boolean seekInterrupted;

    RadioTuner() {
    }

    /**
     * Gets a {@code RadioTuner} object.
     * Even if this method is called multiple times, the same instance is always returned.
     * The initial values of the tuner type, frequency, volume, stereo mode, and output destination
     * are device-dependent.
     * The host runtime keeps a single stateful tuner instance and initializes it to valid defaults.
     *
     * @return the {@code RadioTuner} object
     */
    public static RadioTuner getRadioTuner() {
        return INSTANCE;
    }

    /**
     * Supplies power to the tuner and starts playing radio audio.
     * If the tuner is already powered on, this method does nothing.
     *
     * @throws DeviceException if the native side fails to power on the tuner because of a race
     *                         condition or similar conflict ({@link DeviceException#RACE_CONDITION})
     */
    public synchronized void turnOn() {
        turnedOn = true;
    }

    /**
     * Cuts power to the tuner and stops radio audio.
     * If this method is called while seek processing is running, the seek processing is interrupted.
     * If the tuner is already powered off, this method does nothing.
     */
    public synchronized void turnOff() {
        turnedOn = false;
        interruptSeeking();
    }

    /**
     * Gets whether the tuner power is on or off.
     *
     * @return {@code true} if the tuner power is on, or {@code false} if it is off
     */
    public synchronized boolean isTurnedOn() {
        return turnedOn;
    }

    /**
     * Sets the tuner type.
     * If the tuner power is on, the setting takes effect immediately.
     * If the tuner power is off, the latest setting takes effect the next time the power is turned on.
     *
     * @param type the tuner type to set; {@link #TUNERTYPE_FM} or {@link #TUNERTYPE_AM}
     * @throws IllegalArgumentException if {@code type} is not a supported tuner type
     */
    public synchronized void setTunerType(int type) {
        validateTunerType(type);
        tunerType = type;
    }

    /**
     * Gets the tuner type that is currently set.
     * The latest setting can be obtained regardless of whether the tuner power is on or off.
     *
     * @return the tuner type
     */
    public synchronized int getTunerType() {
        return tunerType;
    }

    /**
     * Sets the frequency for the current tuner type.
     * The current tuner type is the value that can be obtained from {@link #getTunerType()} at
     * the time this method is called.
     * The specified frequency is retained independently for each tuner type.
     * If the tuner power is on, the setting takes effect immediately.
     * If the tuner power is off, the latest setting takes effect the next time the power is turned on.
     *
     * @param frequency the frequency in kHz units; for an FM radio tuner, the TV channel constants
     *                  {@link #FREQUENCY_TV1}, {@link #FREQUENCY_TV2}, and {@link #FREQUENCY_TV3}
     *                  can also be specified
     * @throws IllegalArgumentException if {@code frequency} is outside the range that can be set
     */
    public synchronized void setFrequency(int frequency) {
        validateFrequency(tunerType, frequency);
        frequencies[indexOf(tunerType)] = frequency;
    }

    /**
     * Gets the frequency that is set for the current tuner type.
     * The current tuner type is the value that can be obtained from {@link #getTunerType()} at
     * the time this method is called.
     * The latest setting can be obtained regardless of whether the tuner power is on or off.
     *
     * @return the frequency value in kHz units, or the TV channel value
     */
    public synchronized int getFrequency() {
        return frequencies[indexOf(tunerType)];
    }

    /**
     * Sets the volume.
     * Specify a value from {@code 0} through {@code 100}, with {@code 100} as the maximum volume level.
     * If the tuner power is on, the setting takes effect immediately.
     * If the tuner power is off, the latest setting takes effect the next time the power is turned on.
     *
     * @param volume the volume value to set
     * @throws IllegalArgumentException if {@code volume} is less than {@code 0} or greater than {@code 100}
     */
    public synchronized void setVolume(int volume) {
        if (volume < 0 || volume > 100) {
            throw new IllegalArgumentException("volume");
        }
        this.volume = volume;
    }

    /**
     * Gets the volume that is set on the tuner.
     * The latest setting can be obtained regardless of whether the tuner power is on or off.
     *
     * @return the volume value
     */
    public synchronized int getVolume() {
        return volume;
    }

    /**
     * Sets the stereo mode.
     * If the tuner power is on, the setting takes effect immediately.
     * If the tuner power is off, the latest setting takes effect the next time the power is turned on.
     *
     * @param mode the stereo mode to set; {@link #MODE_AUTO} or {@link #MODE_MONAURAL}
     * @throws IllegalArgumentException if {@code mode} is neither {@link #MODE_AUTO} nor
     *                                  {@link #MODE_MONAURAL}
     */
    public synchronized void setStereoMode(int mode) {
        if (mode != MODE_AUTO && mode != MODE_MONAURAL) {
            throw new IllegalArgumentException("mode");
        }
        stereoMode = mode;
    }

    /**
     * Gets the stereo mode that is set on the tuner.
     * The latest setting can be obtained regardless of whether the tuner power is on or off.
     *
     * @return the stereo mode
     */
    public synchronized int getStereoMode() {
        return stereoMode;
    }

    /**
     * Sets the output destination.
     * If the tuner power is on, the setting takes effect immediately.
     * If the tuner power is off, the latest setting takes effect the next time the power is turned on.
     *
     * @param out the output destination; {@link #OUT_SPEAKER}, {@link #OUT_EARPHONE}, or {@link #OUT_AUTO}
     * @throws IllegalArgumentException if {@code out} is not one of the supported output destinations
     */
    public synchronized void setOutput(int out) {
        if (out != OUT_SPEAKER && out != OUT_EARPHONE && out != OUT_AUTO) {
            throw new IllegalArgumentException("out");
        }
        output = out;
    }

    /**
     * Gets the output destination that is set on the tuner.
     * The latest setting can be obtained regardless of whether the tuner power is on or off.
     *
     * @return the output destination
     */
    public synchronized int getOutput() {
        return output;
    }

    /**
     * Gets the maximum frequency that can be set for each tuner type.
     * TV frequencies are not included.
     *
     * @param type the tuner type; {@link #TUNERTYPE_FM} or {@link #TUNERTYPE_AM}
     * @return the maximum frequency that can be set for the specified tuner type
     * @throws IllegalArgumentException if {@code type} is not a supported tuner type
     */
    public int getMaxFrequency(int type) {
        validateTunerType(type);
        return type == TUNERTYPE_FM ? FM_MAX_FREQUENCY : AM_MAX_FREQUENCY;
    }

    /**
     * Gets the minimum frequency that can be set for each tuner type.
     *
     * @param type the tuner type; {@link #TUNERTYPE_FM} or {@link #TUNERTYPE_AM}
     * @return the minimum frequency that can be set for the specified tuner type
     * @throws IllegalArgumentException if {@code type} is not a supported tuner type
     */
    public int getMinFrequency(int type) {
        validateTunerType(type);
        return type == TUNERTYPE_FM ? FM_MIN_FREQUENCY : AM_MIN_FREQUENCY;
    }

    /**
     * Seeks the adjacent frequency for the current tuner type.
     * @param upward {@code true} to seek toward larger frequencies, or {@code false} to seek toward smaller ones
     * @return the found frequency, or {@code -1} if no station matched
     */
    public int seekFrequency(boolean upward) {
        int type;
        int currentFrequency;
        synchronized (this) {
            if (!turnedOn) {
                throw new DeviceException(DeviceException.ILLEGAL_STATE, "Radio tuner power is off");
            }
            if (seeking) {
                throw new DeviceException(DeviceException.BUSY_RESOURCE, "Radio tuner seek is already running");
            }
            seeking = true;
            seekInterrupted = false;
            type = tunerType;
            currentFrequency = frequencies[indexOf(tunerType)];
        }
        try {
            int delayMs = java.lang.Math.max(0, opendoja.host.OpenDoJaLaunchArgs.getInt(opendoja.host.OpenDoJaLaunchArgs.RADIOTUNER_SEEK_DELAY_MS));
            if (delayMs > 0) {
                synchronized (this) {
                    wait(delayMs);
                    if (seekInterrupted || !turnedOn) {
                        throw new DeviceException(DeviceException.INTERRUPTED, "Radio tuner seek was interrupted");
                    }
                }
            }
            int found = findStation(type, currentFrequency, upward);
            if (found >= 0) {
                synchronized (this) {
                    frequencies[indexOf(type)] = found;
                }
            }
            return found;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new DeviceException(DeviceException.INTERRUPTED, "Radio tuner seek was interrupted");
        } finally {
            synchronized (this) {
                seeking = false;
                seekInterrupted = false;
                notifyAll();
            }
        }
    }

    /**
     * Interrupts seek processing.
     * If seek processing is not running, this method does nothing.
     */
    public void interruptSeeking() {
        synchronized (this) {
            if (!seeking) {
                return;
            }
            seekInterrupted = true;
            notifyAll();
        }
    }

    /**
     * Suspends the application for power saving.
     * Whether suspension is possible while seek processing is running is device-dependent.
     *
     */
    public void savePower() {
        synchronized (this) {
            if (seeking) {
                throw new DeviceException(DeviceException.BUSY_RESOURCE, "Radio tuner seek is already running");
            }
        }
    }

    /**
     * Gets the tuner types supported by the terminal.
     * The returned array is a copy of the array held internally by this class.
     *
     * @return a one-dimensional array that stores all supported tuner types
     */
    public int[] getSupportedTunerTypes() {
        return SUPPORTED_TUNER_TYPES.clone();
    }

    private static int indexOf(int tunerType) {
        return tunerType == TUNERTYPE_FM ? 0 : 1;
    }

    private static void validateTunerType(int type) {
        if (type != TUNERTYPE_FM && type != TUNERTYPE_AM) {
            throw new IllegalArgumentException("type");
        }
    }

    private static void validateFrequency(int tunerType, int frequency) {
        if (tunerType == TUNERTYPE_FM) {
            if (frequency == FREQUENCY_TV1 || frequency == FREQUENCY_TV2 || frequency == FREQUENCY_TV3) {
                return;
            }
            if (frequency < FM_MIN_FREQUENCY || frequency > FM_MAX_FREQUENCY) {
                throw new IllegalArgumentException("frequency");
            }
            return;
        }
        if (frequency < AM_MIN_FREQUENCY || frequency > AM_MAX_FREQUENCY) {
            throw new IllegalArgumentException("frequency");
        }
    }

    private int findStation(int type, int currentFrequency, boolean upward) {
        int[] stations = _OptionalDeviceSupport.stationsForTuner(type);
        if (stations.length == 0) {
            return -1;
        }
        if (upward) {
            for (int station : stations) {
                if (station > currentFrequency) {
                    return station;
                }
            }
            return stations[0];
        }
        for (int i = stations.length - 1; i >= 0; i--) {
            if (stations[i] < currentFrequency) {
                return stations[i];
            }
        }
        return stations[stations.length - 1];
    }
}
