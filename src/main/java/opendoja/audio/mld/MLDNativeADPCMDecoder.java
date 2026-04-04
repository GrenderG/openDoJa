package opendoja.audio.mld;

final class MLDNativeADPCMDecoder {
    private static final int INPUT_SAMPLE_RATE_8K = 8000;
    private static final int INPUT_SAMPLE_RATE_16K = 16000;
    private static final int CODED_BITS_2 = 2;
    private static final int CODED_BITS_4 = 4;
    private static final int MONO_CHANNELS = 1;
    private static final int LIVE_DEFAULT_LEVEL_Q12 = 4095;
    private static final int LIVE_DEFAULT_LEFT_PAN_Q12 = 4095;
    private static final int LIVE_DEFAULT_SHIFT_BITS = 24;

    private static final LiveProfile LIVE_PROFILE_8K_2BIT = new LiveProfile(
            INPUT_SAMPLE_RATE_8K,
            CODED_BITS_2,
            3,
            new double[] { 0.375, -0.00390625, 0.375, 1.4150390625, -0.6875 },
            0.04296875,
            new double[] { 0.0654296875, 0.04296875, 1.439453125, -0.54296875 },
            0.306640625,
            new double[] { -0.171875, 0.306640625, 1.4462890625, -0.8896484375 });

    private static final LiveProfile LIVE_PROFILE_8K_4BIT = new LiveProfile(
            INPUT_SAMPLE_RATE_8K,
            CODED_BITS_4,
            3,
            new double[] { 0.375, -0.00390625, 0.375, 1.4150390625, -0.6875 },
            0.04296875,
            new double[] { 0.0654296875, 0.04296875, 1.439453125, -0.54296875 },
            0.306640625,
            new double[] { -0.171875, 0.306640625, 1.4462890625, -0.8896484375 });

    private static final LiveProfile LIVE_PROFILE_16K_2BIT = new LiveProfile(
            INPUT_SAMPLE_RATE_16K,
            CODED_BITS_2,
            1,
            new double[] { 0.207692, 0.36527, 0.207692, 0.892037, -0.282364 },
            0.35532,
            new double[] { 0.289359, 0.35532, 0.588666, -0.588666 },
            0.631808,
            new double[] { 0.226858, 0.631808, 0.385442, -0.875916 });

    private static final LiveProfile LIVE_PROFILE_16K_4BIT = new LiveProfile(
            INPUT_SAMPLE_RATE_16K,
            CODED_BITS_4,
            1,
            new double[] { 0.207692, 0.36527, 0.207692, 0.892037, -0.282364 },
            0.35532,
            new double[] { 0.289359, 0.35532, 0.588666, -0.588666 },
            0.631808,
            new double[] { 0.226858, 0.631808, 0.385442, -0.875916 });

    // `MFiSoundLibMFi5.dll` `.data` tables at 0x100A3F98..0x100A4065.
    private static final int[] DQLN_4BIT = new int[] {
            2048, 4, 135, 213, 273, 323, 373, 425,
            425, 373, 323, 273, 213, 135, 4, 2048
    };
    private static final int[] DQLN_2BIT = new int[] { 116, 365, 365, 116 };
    private static final int[] WI_4BIT = new int[] { 4084, 18, 41, 64, 112, 198, 355, 1122 };
    private static final int[] WI_2BIT = new int[] { 4074, 439 };
    private static final int[] FI_4BIT = new int[] { 0, 0, 0, 1, 1, 1, 3, 7 };
    private static final int[] FI_2BIT = new int[] { 0, 7 };

    private MLDNativeADPCMDecoder() {
    }

    static boolean supportsLivePath(int sampleRate, int codedBits, int channelCount) {
        return profileFor(sampleRate, codedBits, channelCount) != null;
    }

    static int[] decodeLiveMonoNativeLane0(int sampleRate, int codedBits, byte[] payload) {
        LiveProfile profile = profileFor(sampleRate, codedBits, MONO_CHANNELS);
        if (profile == null) {
            throw new IllegalArgumentException(
                    "Unsupported 0x8001 live-path subset: " + sampleRate + " Hz / " + codedBits + "-bit / mono.");
        }

        int codesPerByte = 8 / codedBits;
        int totalFrames = payload.length * codesPerByte * (profile.upsamplePeriodMinus1 + 1);
        int[] lane0 = new int[totalFrames];

        LaneState lane = new LaneState();
        RenderState render = new RenderState();
        int emitted = 0;

        while (emitted < totalFrames && !render.stop) {
            double stage0 = dot5(
                    render.sampleHist0,
                    render.sampleHist1,
                    render.sampleHist2,
                    render.stage1Hist0,
                    render.stage1Hist1,
                    profile.stage0);
            double stage1 = stage0 * profile.stage1Gain
                    + dot4(render.stage1Hist0, render.stage1Hist1, render.stage2Hist0, render.stage2Hist1, profile.stage1);
            double stage2 = stage1 * profile.stage2Gain
                    + dot4(render.stage2Hist0, render.stage2Hist1, render.stage3Hist0, render.stage3Hist1, profile.stage2);

            lane0[emitted++] = quantizeNativeLane0(stage2);
            advanceProfile(payload, lane, render, profile, codedBits, stage0, stage1, stage2);
        }

        if (emitted == lane0.length) {
            return lane0;
        }
        int[] truncated = new int[emitted];
        System.arraycopy(lane0, 0, truncated, 0, emitted);
        return truncated;
    }

    private static LiveProfile profileFor(int sampleRate, int codedBits, int channelCount) {
        if (channelCount != MONO_CHANNELS) {
            return null;
        }
        if (sampleRate == INPUT_SAMPLE_RATE_8K) {
            if (codedBits == CODED_BITS_2) {
                return LIVE_PROFILE_8K_2BIT;
            }
            if (codedBits == CODED_BITS_4) {
                return LIVE_PROFILE_8K_4BIT;
            }
            return null;
        }
        if (sampleRate == INPUT_SAMPLE_RATE_16K) {
            if (codedBits == CODED_BITS_2) {
                return LIVE_PROFILE_16K_2BIT;
            }
            if (codedBits == CODED_BITS_4) {
                return LIVE_PROFILE_16K_4BIT;
            }
            return null;
        }
        return null;
    }

    private static void advanceProfile(
            byte[] payload,
            LaneState lane,
            RenderState render,
            LiveProfile profile,
            int codedBits,
            double stage0,
            double stage1,
            double stage2) {
        render.stage3Hist1 = render.stage3Hist0;
        render.stage3Hist0 = stage2;
        render.stage2Hist1 = render.stage2Hist0;
        render.stage2Hist0 = stage1;
        render.stage1Hist1 = render.stage1Hist0;
        render.stage1Hist0 = stage0;

        render.sampleHist2 = render.sampleHist1;
        render.sampleHist1 = render.sampleHist0;

        if (render.subframePhase == 0) {
            int code = readPackedCode(payload, render, codedBits);
            if (code < 0) {
                render.stop = true;
            } else {
                render.sampleHist0 = stepUpdateCore(code, codedBits, lane);
            }
        } else {
            render.sampleHist0 = 0.0;
        }

        if (!render.stop) {
            if (render.subframePhase == profile.upsamplePeriodMinus1) {
                render.subframePhase = 0;
            } else {
                render.subframePhase++;
            }
        }
    }

    private static int readPackedCode(byte[] payload, RenderState render, int codedBits) {
        if (render.bitsRemaining < codedBits) {
            if (render.inputByteCursor >= payload.length) {
                return -1;
            }
            render.latchedByte = payload[render.inputByteCursor++] & 0xFF;
            render.bitsRemaining = 8;
        }
        int codeMask = (1 << codedBits) - 1;
        int code = render.latchedByte & codeMask;
        render.latchedByte >>>= codedBits;
        render.bitsRemaining -= codedBits;
        return code;
    }

    private static int stepUpdateCore(int code, int codedBits, LaneState state) {
        int oldSr1Packed = state.sr1Packed;
        int oldSr2Packed = state.sr2Packed;
        int[] oldDqPacked = new int[] {
                state.dqPacked[0], state.dqPacked[1], state.dqPacked[2],
                state.dqPacked[3], state.dqPacked[4], state.dqPacked[5]
        };

        int zeroPredictorWord = predictorZeroWord(state, oldDqPacked);
        int polePredictorWord = predictorPoleWord(state, oldSr1Packed, oldSr2Packed);
        int sezWord = (zeroPredictorWord >>> 1) & 0xFFFF;
        int seWord = ((zeroPredictorWord + polePredictorWord) & 0xFFFF) >>> 1;

        int y = computeStepSize(state.ap, state.yu, state.yl);
        int signBit = 1 << (codedBits - 1);
        int dq = reconstructSignMagnitude((code & signBit) != 0, dqlnTable(codedBits)[code], y);
        int srWord = combineSignMagnitudeAndPredictor(dq, seWord);
        int dqsezWord = combineSignMagnitudeAndPredictor(dq, sezWord);
        int sr = sign16(srWord);
        int pk0 = (dqsezWord >>> 15) & 1;
        boolean dqsezZero = dqsezWord == 0;

        int fi = lookupFoldedTable(code, codedBits, fiTable(codedBits));
        state.dms = (state.dms + (((fi << 9) - state.dms) >> 5)) & 0x0FFF;
        state.dml = (state.dml + (((fi << 11) - state.dml) >> 7)) & 0x3FFF;

        int wi = lookupFoldedTable(code, codedBits, wiTable(codedBits));
        state.yu = updateYu(y, wi);
        state.yl = updateYl(state.yl, state.yu);

        int output = clamp(-8192, 8191, sr) * 4;

        int tr = computeTr(state.td, state.yl, dq);
        int oldPk1 = state.pk1;
        int oldPk2 = state.pk2;
        int oldA1 = state.a1;
        int oldA2 = state.a2;

        state.sr2Packed = oldSr1Packed;
        state.sr1Packed = packSr(sr);
        state.dqPacked[0] = oldDqPacked[1];
        state.dqPacked[1] = oldDqPacked[2];
        state.dqPacked[2] = oldDqPacked[3];
        state.dqPacked[3] = oldDqPacked[4];
        state.dqPacked[4] = oldDqPacked[5];
        state.dqPacked[5] = packDq(dq);
        state.pk2 = oldPk1;
        state.pk1 = pk0;

        int a2Candidate = updateA2Candidate(pk0, oldPk1, oldPk2, oldA2, oldA1, dqsezZero);
        int a2Clamped = clamp(-0x3000, 0x3000, a2Candidate);
        int a1Candidate = updateA1Candidate(pk0, oldPk1, oldA1, dqsezZero);
        int a1Clamped = clamp(oldA2BandMin(a2Clamped), oldA2BandMax(a2Clamped), a1Candidate);
        int tone = a2Clamped < -0x2E00 ? 1 : 0;
        int subtc = classifySubtc(state.dms, state.dml, tone, y);
        int apCandidate = updateAp(subtc, state.ap);

        state.a2 = tr != 0 ? 0 : sign16(a2Clamped);
        state.a1 = tr != 0 ? 0 : sign16(a1Clamped);
        state.td = tr != 0 ? 0 : tone;
        state.ap = tr != 0 ? 256 : apCandidate;

        state.b[0] = tr != 0 ? 0 : updateBCoefficient(state.b[0], oldDqPacked[5], dq);
        state.b[1] = tr != 0 ? 0 : updateBCoefficient(state.b[1], oldDqPacked[4], dq);
        state.b[2] = tr != 0 ? 0 : updateBCoefficient(state.b[2], oldDqPacked[3], dq);
        state.b[3] = tr != 0 ? 0 : updateBCoefficient(state.b[3], oldDqPacked[2], dq);
        state.b[4] = tr != 0 ? 0 : updateBCoefficient(state.b[4], oldDqPacked[1], dq);
        state.b[5] = tr != 0 ? 0 : updateBCoefficient(state.b[5], oldDqPacked[0], dq);

        return output;
    }

    private static int reconstructSignMagnitude(boolean negative, int dqln, int y) {
        int dql = dqln + (y >> 2);
        if (dql < 0) {
            return negative ? 0xFFFF8000 : 0;
        }
        int dex = (dql >> 7) & 0x0F;
        int dqt = 128 + (dql & 0x7F);
        int mag = (dqt << 7) >> (14 - dex);
        return negative ? (mag - 0x8000) : mag;
    }

    private static int combineSignMagnitudeAndPredictor(int dqSignMagnitude, int predictorWord) {
        int dqSignedWord = dqSignMagnitude & 0xFFFF;
        if ((dqSignedWord & 0x8000) != 0) {
            dqSignedWord = (-(dqSignedWord & 0x7FFF)) & 0xFFFF;
        }
        int predictorSigned = sign16(predictorWord);
        if ((predictorWord & 0xC000) != 0) {
            predictorSigned += 0x8000;
        }
        return (predictorSigned + dqSignedWord) & 0xFFFF;
    }

    private static int predictorZeroWord(LaneState state, int[] oldDqPacked) {
        return (predictorFmult(state.b[0], oldDqPacked[5])
                + predictorFmult(state.b[1], oldDqPacked[4])
                + predictorFmult(state.b[2], oldDqPacked[3])
                + predictorFmult(state.b[3], oldDqPacked[2])
                + predictorFmult(state.b[4], oldDqPacked[1])
                + predictorFmult(state.b[5], oldDqPacked[0])) & 0xFFFF;
    }

    private static int predictorPoleWord(LaneState state, int oldSr1Packed, int oldSr2Packed) {
        return (predictorFmult(state.a1, oldSr1Packed) + predictorFmult(state.a2, oldSr2Packed)) & 0xFFFF;
    }

    private static int[] dqlnTable(int codedBits) {
        return (codedBits == CODED_BITS_2 ? DQLN_2BIT : DQLN_4BIT);
    }

    private static int[] wiTable(int codedBits) {
        return (codedBits == CODED_BITS_2 ? WI_2BIT : WI_4BIT);
    }

    private static int[] fiTable(int codedBits) {
        return (codedBits == CODED_BITS_2 ? FI_2BIT : FI_4BIT);
    }

    private static int lookupFoldedTable(int code, int codedBits, int[] table) {
        int rankMask = (1 << (codedBits - 1)) - 1;
        int signBit = 1 << (codedBits - 1);
        int rank = code & rankMask;
        if ((code & signBit) != 0) {
            rank = rankMask - rank;
        }
        return table[rank];
    }

    private static int updateA2Candidate(int pk0, int oldPk1, int oldPk2, int oldA2, int oldA1, boolean dqsezZero) {
        int a11 = oldA1 & 0xFFFF;
        int a21 = oldA2 & 0xFFFF;
        int pks1 = pk0 ^ oldPk1;
        int pks2 = pk0 ^ oldPk2;
        int uga2a = pks2 == 0 ? 16384 : 114688;
        int fa1;
        if ((oldA1 >> 15) == 0) {
            fa1 = a11 <= 8191 ? (a11 << 2) : (8191 << 2);
        } else {
            fa1 = a11 >= 57345 ? ((a11 << 2) & 131071) : (24577 << 2);
        }
        int fa = pks1 != 0 ? fa1 : ((131072 - fa1) & 131071);
        int uga2b = (uga2a + fa) & 131071;
        int uga2 = dqsezZero ? 0 : (((uga2b >> 16) != 0) ? ((uga2b >> 7) + 64512) : (uga2b >> 7));
        int ula2 = ((oldA2 >> 15) == 0)
                ? ((65536 - (a21 >> 7)) & 0xFFFF)
                : ((65536 - ((a21 >> 7) + 65024)) & 0xFFFF);
        int ua2 = (uga2 + ula2) & 0xFFFF;
        return sign16((a21 + ua2) & 0xFFFF);
    }

    private static int updateA1Candidate(int pk0, int oldPk1, int oldA1, boolean dqsezZero) {
        int a11 = oldA1 & 0xFFFF;
        int uga1 = dqsezZero ? 0 : (pk0 == oldPk1 ? 192 : 65344);
        int ash = a11 >> 8;
        int ula1 = ((oldA1 >> 15) == 0)
                ? ((65536 - ash) & 0xFFFF)
                : ((65536 - (ash + 65280)) & 0xFFFF);
        int ua1 = (uga1 + ula1) & 0xFFFF;
        return sign16((a11 + ua1) & 0xFFFF);
    }

    private static int updateAp(int subtc, int ap) {
        int raw = (subtc << 9) - ap;
        int delta11 = raw & 0x7FF;
        int result;
        if ((delta11 & 0x400) != 0) {
            result = (delta11 >> 4) + 896 + ap;
        } else {
            result = (delta11 >> 4) + ap;
        }
        return result & 0x3FF;
    }

    private static int classifySubtc(int dms, int dml, int tone, int y) {
        int delta = ((4 * dms) - dml) & 0x7FFF;
        if ((delta & 0x4000) != 0) {
            delta = (-delta) & 0x3FFF;
        }
        return (y < 1536 || delta >= (dml >> 3) || tone != 0) ? 1 : 0;
    }

    private static int updateBCoefficient(int oldB, int packedDqHistory, int dq) {
        int u = ((packedDqHistory >> 10) ^ ((dq >> 15) & 1)) & 1;
        int bb = oldB & 0xFFFF;
        int ugb = (dq & 0x7FFF) == 0 ? 0 : (u == 0 ? 128 : 65408);
        int ulb = ((oldB >> 15) == 0)
                ? ((65536 - (bb >> 8)) & 0xFFFF)
                : ((65536 - ((bb >> 8) + 65280)) & 0xFFFF);
        int ub = (ugb + ulb) & 0xFFFF;
        return sign16((bb + ub) & 0xFFFF);
    }

    private static int computeTr(int td, int yl, int dq) {
        int ylMag = (yl >> 15) <= 9
                ? ((((yl >> 10) & 0x1F) + 32) << (yl >> 15))
                : 31744;
        return td == 1 && (dq & 0x7FFF) > ((ylMag + (ylMag >> 1)) >> 1) ? 1 : 0;
    }

    private static int computeStepSize(int ap, int yu, int yl) {
        int al = ap >= 0x100 ? 0x40 : (ap >> 2);
        int base = yl >> 6;
        int delta = (yu - base) & 0x3FFF;
        int magnitude = (delta & 0x2000) != 0 ? ((-delta) & 0x1FFF) : delta;
        int scaled = (magnitude * al) >> 6;
        if ((delta & 0x2000) != 0) {
            scaled = (-scaled) & 0x3FFF;
        }
        return (base + scaled) & 0x1FFF;
    }

    private static int updateYu(int y, int wi) {
        int delta = ((wi << 5) - y) & 0x1FFFF;
        int candidate = (y + (((delta & 0x10000) != 0) ? (((delta >> 5) + 0x1000) & 0x1FFF) : ((delta >> 5) & 0x1FFF)))
                & 0x1FFF;
        return clamp(544, 5120, candidate);
    }

    private static int updateYl(int yl, int yu) {
        int delta = (yu + ((0x100000 - yl) >> 6)) & 0x3FFF;
        if ((delta & 0x2000) != 0) {
            delta = (delta + 0x7C000) & 0x7FFFF;
        }
        return (yl + delta) & 0x7FFFF;
    }

    private static int predictorFmult(int coefficient, int packedHistory) {
        int shifted = sign16(coefficient) >> 2;
        int coefficientSign = shifted < 0 ? 1 : 0;
        int coefficientMag = coefficientSign != 0 ? ((-shifted) & 0x1FFF) : (shifted & 0x1FFF);
        int coefficientExponent = coefficientMagnitudeExponent(coefficientMag);
        int coefficientMantissa = coefficientMag == 0 ? 0x20 : ((coefficientMag << 6) >> coefficientExponent);

        int historyMantissa = packedHistory & 0x3F;
        int historyExponent = (packedHistory >> 6) & 0x0F;
        int sumExponent = historyExponent + coefficientExponent;
        int productMantissa = ((historyMantissa * coefficientMantissa) + 0x30) >> 4;
        int magnitude;
        if (sumExponent > 0x1A) {
            magnitude = ((productMantissa << 7) << (sumExponent - 0x1A)) & 0x7FFF;
        } else {
            magnitude = (productMantissa << 7) >> (0x1A - sumExponent);
        }
        if ((((packedHistory >> 10) & 1) ^ coefficientSign) != 0) {
            magnitude = (-magnitude) & 0xFFFF;
        }
        return sign16(magnitude);
    }

    private static int coefficientMagnitudeExponent(int magnitude) {
        if (magnitude >= 0x1000) {
            return 0x0D;
        }
        if (magnitude >= 0x0800) {
            return 0x0C;
        }
        if (magnitude >= 0x0400) {
            return 0x0B;
        }
        if (magnitude >= 0x0200) {
            return 0x0A;
        }
        if (magnitude >= 0x0100) {
            return 0x09;
        }
        if (magnitude >= 0x0080) {
            return 0x08;
        }
        if (magnitude >= 0x0040) {
            return 0x07;
        }
        if (magnitude >= 0x0020) {
            return 0x06;
        }
        if (magnitude >= 0x0010) {
            return 0x05;
        }
        if (magnitude >= 0x0008) {
            return 0x04;
        }
        if (magnitude >= 0x0004) {
            return 0x03;
        }
        if (magnitude >= 0x0002) {
            return 0x02;
        }
        return magnitude == 0x0001 ? 0x01 : 0x00;
    }

    private static int packSr(int value) {
        int magnitude = sign16(value);
        int sign = magnitude < 0 ? 1 : 0;
        if (sign != 0) {
            magnitude = (-magnitude) & 0x7FFF;
        }
        int exponent = packExponent(magnitude);
        return packMantissa(sign, magnitude, exponent);
    }

    private static int packDq(int value) {
        int sign = (value >> 15) & 1;
        int magnitude = value & 0x7FFF;
        int exponent = packExponent(magnitude);
        return packMantissa(sign, magnitude, exponent);
    }

    private static int packExponent(int magnitude) {
        if (magnitude >= 0x4000) {
            return 15;
        }
        if (magnitude >= 0x2000) {
            return 14;
        }
        if (magnitude >= 0x1000) {
            return 13;
        }
        if (magnitude >= 0x0800) {
            return 12;
        }
        if (magnitude >= 0x0400) {
            return 11;
        }
        if (magnitude >= 0x0200) {
            return 10;
        }
        if (magnitude >= 0x0100) {
            return 9;
        }
        if (magnitude >= 0x0080) {
            return 8;
        }
        if (magnitude >= 0x0040) {
            return 7;
        }
        if (magnitude >= 0x0020) {
            return 6;
        }
        if (magnitude >= 0x0010) {
            return 5;
        }
        if (magnitude >= 0x0008) {
            return 4;
        }
        if (magnitude >= 0x0004) {
            return 3;
        }
        if (magnitude >= 0x0002) {
            return 2;
        }
        if (magnitude == 0x0001) {
            return 1;
        }
        return 0;
    }

    private static int packMantissa(int sign, int magnitude, int exponent) {
        int mantissa;
        if (magnitude != 0) {
            mantissa = (magnitude << 6) >> exponent;
        } else {
            mantissa = 0x20;
        }
        return ((sign << 10) + (exponent << 6) + mantissa) & 0xFFFF;
    }

    private static int oldA2BandMin(int a2) {
        return a2 - 0x3C00;
    }

    private static int oldA2BandMax(int a2) {
        return 0x3C00 - a2;
    }

    private static int sign16(int value) {
        return (short) value;
    }

    private static int quantizeNativeLane0(double sample) {
        int clamped = clamp16(sample);
        int leveled = q12MultiplyTrunc(clamped, LIVE_DEFAULT_LEVEL_Q12);
        int left = q12MultiplyTrunc(leveled, LIVE_DEFAULT_LEFT_PAN_Q12);
        return shiftSignedLane(left, LIVE_DEFAULT_SHIFT_BITS);
    }

    private static int q12MultiplyTrunc(int sample, int factorQ12) {
        int product = sample * factorQ12;
        int bias = product < 0 ? 0x0FFF : 0;
        return (product + bias) >> 12;
    }

    private static int shiftSignedLane(int sample, int shiftBits) {
        if (shiftBits >= 16) {
            return sample << (shiftBits - 16);
        }
        return sample >> (16 - shiftBits);
    }

    private static int signExtend13(int value) {
        int masked = value & 0x1FFF;
        return (masked & 0x1000) != 0 ? masked - 0x2000 : masked;
    }

    private static int signExtend14(int value) {
        int masked = value & 0x3FFF;
        return (masked & 0x2000) != 0 ? masked - 0x4000 : masked;
    }

    private static short clamp16(double value) {
        int truncated = (int) value;
        if (truncated < Short.MIN_VALUE) {
            truncated = Short.MIN_VALUE;
        } else if (truncated > Short.MAX_VALUE) {
            truncated = Short.MAX_VALUE;
        }
        return (short) truncated;
    }

    private static int clamp(int min, int max, int value) {
        return Math.max(min, Math.min(max, value));
    }

    private static int floorLog2(int value) {
        return 31 - Integer.numberOfLeadingZeros(value);
    }

    private static double dot4(double a, double b, double c, double d, double[] coeffs) {
        return a * coeffs[0] + b * coeffs[1] + c * coeffs[2] + d * coeffs[3];
    }

    private static double dot5(double a, double b, double c, double d, double e, double[] coeffs) {
        return a * coeffs[0] + b * coeffs[1] + c * coeffs[2] + d * coeffs[3] + e * coeffs[4];
    }

    private static final class LiveProfile {
        final int inputSampleRate;
        final int codedBits;
        final int upsamplePeriodMinus1;
        final double[] stage0;
        final double stage1Gain;
        final double[] stage1;
        final double stage2Gain;
        final double[] stage2;

        LiveProfile(
                int inputSampleRate,
                int codedBits,
                int upsamplePeriodMinus1,
                double[] stage0,
                double stage1Gain,
                double[] stage1,
                double stage2Gain,
                double[] stage2) {
            this.inputSampleRate = inputSampleRate;
            this.codedBits = codedBits;
            this.upsamplePeriodMinus1 = upsamplePeriodMinus1;
            this.stage0 = stage0;
            this.stage1Gain = stage1Gain;
            this.stage1 = stage1;
            this.stage2Gain = stage2Gain;
            this.stage2 = stage2;
        }
    }

    private static final class LaneState {
        int sr1Packed = 32;
        int sr2Packed = 32;
        int a1 = 0;
        int a2 = 0;
        final int[] b = new int[] { 0, 0, 0, 0, 0, 0 };
        final int[] dqPacked = new int[] { 32, 32, 32, 32, 32, 32 };
        int dms = 0;
        int dml = 0;
        int ap = 0;
        int yu = 544;
        int td = 0;
        int pk1 = 0;
        int pk2 = 0;
        int yl = 34816;
    }

    private static final class RenderState {
        double sampleHist0 = 0.0;
        double sampleHist1 = 0.0;
        double sampleHist2 = 0.0;
        double stage1Hist0 = 0.0;
        double stage1Hist1 = 0.0;
        double stage2Hist0 = 0.0;
        double stage2Hist1 = 0.0;
        double stage3Hist0 = 0.0;
        double stage3Hist1 = 0.0;
        int subframePhase = 0;
        int inputByteCursor = 0;
        int latchedByte = 0;
        int bitsRemaining = 0;
        boolean stop = false;
    }
}
