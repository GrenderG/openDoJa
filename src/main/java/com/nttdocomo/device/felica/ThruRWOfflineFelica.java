package com.nttdocomo.device.felica;

import java.io.IOException;

/**
 * Represents offline processing via an external reader/writer.
 */
public final class ThruRWOfflineFelica extends OfflineFelica {
    /** Represents 212 kbps communication speed (=212). */
    public static final int BAUDRATE_212_KBPS = 212;
    /** Represents 424 kbps communication speed (=424). */
    public static final int BAUDRATE_424_KBPS = 424;

    private int baudRate = BAUDRATE_212_KBPS;

    ThruRWOfflineFelica(int card, int systemCode, byte[] idm) {
        super(card, systemCode, idm);
    }

    ThruRWOfflineFelica() {
        this(CARD_EXTERNAL, 0, FelicaSupport.idmFor(CARD_EXTERNAL, 0));
    }

    @Override
    public PINAttributeData[] checkPIN(CheckPINParameters parameters) throws FelicaException, IOException {
        return super.checkPIN(parameters);
    }

    @Override
    public void executePIN(PINParameters parameters) throws FelicaException, IOException {
        super.executePIN(parameters);
    }

    @Override
    public FelicaData[] read(ReadParameters parameters) throws FelicaException, IOException {
        return super.read(parameters);
    }

    @Override
    public FelicaData[] read(InputPINParameters pinParameters, ReadParameters readParameters) {
        throw unsupported("com.nttdocomo.device.felica.ThruRWOfflineFelica.read(com.nttdocomo.device.felica.InputPINParameters, com.nttdocomo.device.felica.ReadParameters)");
    }

    @Override
    public void write(WriteParameters parameters) throws FelicaException, IOException {
        super.write(parameters);
    }

    @Override
    public void write(InputPINParameters pinParameters, WriteParameters writeParameters) {
        throw unsupported("com.nttdocomo.device.felica.ThruRWOfflineFelica.write(com.nttdocomo.device.felica.InputPINParameters, com.nttdocomo.device.felica.WriteParameters)");
    }

    @Override
    public byte[] getKeyVersion(int serviceCode) throws FelicaException, IOException {
        return super.getKeyVersion(serviceCode);
    }

    @Override
    public byte[] getContainerIssueInfo() throws FelicaException, IOException {
        return super.getContainerIssueInfo();
    }

    @Override
    public void setParameter(long parameter) throws FelicaException, IOException {
        super.setParameter(parameter);
    }

    public int negotiateBaudRate(int baudRate) throws FelicaException, IOException {
        FelicaSupport.requireOpen();
        if (baudRate != BAUDRATE_212_KBPS && baudRate != BAUDRATE_424_KBPS) {
            throw new IllegalArgumentException("baudRate");
        }
        boolean supportsDiscovery = !opendoja.host.OpenDoJaLaunchArgs.getBoolean(opendoja.host.OpenDoJaLaunchArgs.FELICA_EXTERNAL_NO_SPEED_DISCOVERY);
        boolean supports212 = opendoja.host.OpenDoJaLaunchArgs.getBoolean(opendoja.host.OpenDoJaLaunchArgs.FELICA_EXTERNAL_SUPPORTS212);
        boolean supports424 = opendoja.host.OpenDoJaLaunchArgs.getBoolean(opendoja.host.OpenDoJaLaunchArgs.FELICA_EXTERNAL_SUPPORTS424);
        byte[] polledIdm = getIDm();
        String overrideIdm = opendoja.host.OpenDoJaLaunchArgs.get(opendoja.host.OpenDoJaLaunchArgs.FELICA_EXTERNAL_POLLED_IDM).trim();
        if (!overrideIdm.isEmpty()) {
            polledIdm = decodeIdm(overrideIdm);
        }
        if (!java.util.Arrays.equals(polledIdm, getIDm())) {
            throw new FelicaException(
                    FelicaException.ID_NEGOTIATE_BAUDRATE_ERROR,
                    FelicaException.TYPE_IDM_MISMATCH_ERROR,
                    "The polled IDm does not match the cached IDm",
                    null
            );
        }
        if (!supportsDiscovery) {
            this.baudRate = BAUDRATE_212_KBPS;
            return this.baudRate;
        }
        if (baudRate == BAUDRATE_212_KBPS && supports212) {
            this.baudRate = BAUDRATE_212_KBPS;
            return this.baudRate;
        }
        if (baudRate == BAUDRATE_424_KBPS && supports424) {
            this.baudRate = BAUDRATE_424_KBPS;
            return this.baudRate;
        }
        if (supports212) {
            this.baudRate = BAUDRATE_212_KBPS;
            return this.baudRate;
        }
        if (supports424) {
            this.baudRate = BAUDRATE_424_KBPS;
            return this.baudRate;
        }
        throw new FelicaException(
                FelicaException.ID_NEGOTIATE_BAUDRATE_ERROR,
                FelicaException.TYPE_UNDEFINED_ERROR,
                "The external card does not support 212 kbps or 424 kbps",
                null
        );
    }

    @Override
    public void setTimeout(int timeout) {
        if (timeout < 201 || timeout > 60200) {
            throw new IllegalArgumentException("timeout");
        }
        super.setTimeout(timeout);
    }

    void resetBaudRate() {
        baudRate = BAUDRATE_212_KBPS;
    }

    private static byte[] decodeIdm(String value) {
        String normalized = value.replace(" ", "").replace(":", "");
        if (normalized.length() != 16) {
            return new byte[0];
        }
        byte[] result = new byte[8];
        for (int i = 0; i < 8; i++) {
            result[i] = (byte) Integer.parseInt(normalized.substring(i * 2, i * 2 + 2), 16);
        }
        return result;
    }
}
