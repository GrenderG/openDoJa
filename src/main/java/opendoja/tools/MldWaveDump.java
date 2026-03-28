package opendoja.tools;

import opendoja.audio.mld.MLD;
import opendoja.audio.mld.MldSynth;
import opendoja.audio.mld.MLDPlayer;
import opendoja.audio.mld.SamplerProvider;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public final class MldWaveDump {
    private MldWaveDump() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2 || args.length > 3) {
            throw new IllegalArgumentException("usage: MldWaveDump <mld-file> <wav-file> [iterations]");
        }
        byte[] source = Files.readAllBytes(Path.of(args[0]));
        byte[] mldBytes = firstMeloChunk(source);
        MLD mld = new MLD(mldBytes);
        int iterations = args.length >= 3 ? Integer.parseInt(args[2]) : 800;
        if (iterations < 0) {
            throw new IllegalArgumentException("iterations must be non-negative");
        }

        MldSynth synth = MldSynth.resolveConfigured();
        SamplerProvider provider = synth.createSamplerProvider();
        float sampleRate = synth.defaultSampleRate;

        MLDPlayer player = new MLDPlayer(mld, provider, sampleRate);
        float[] samples = new float[256 * 2];
        short[] pcm = new short[iterations * 256 * 2];
        int pcmLength = 0;
        for (int i = 0; i < iterations; i++) {
            int frames = player.render(samples, 0, 256);
            if (frames < 0) {
                break;
            }
            int required = pcmLength + frames * 2;
            if (required > pcm.length) {
                pcm = Arrays.copyOf(pcm, Math.max(required, pcm.length * 2));
            }
            for (int s = 0; s < frames * 2; s++) {
                int value = Math.round(samples[s] * 32767.0f);
                if (value < Short.MIN_VALUE) {
                    value = Short.MIN_VALUE;
                } else if (value > Short.MAX_VALUE) {
                    value = Short.MAX_VALUE;
                }
                pcm[pcmLength++] = (short) value;
            }
        }

        writeWave(Path.of(args[1]), pcm, pcmLength, Math.round(sampleRate));
        System.out.println("samples=" + pcmLength);
    }

    private static byte[] firstMeloChunk(byte[] source) {
        for (int i = 0; i <= source.length - 8; i++) {
            if (source[i] != 'm' || source[i + 1] != 'e' || source[i + 2] != 'l' || source[i + 3] != 'o') {
                continue;
            }
            int length = ((source[i + 4] & 0xFF) << 24)
                    | ((source[i + 5] & 0xFF) << 16)
                    | ((source[i + 6] & 0xFF) << 8)
                    | (source[i + 7] & 0xFF);
            int end = Math.min(source.length, i + 8 + Math.max(length, 0));
            return Arrays.copyOfRange(source, i, end);
        }
        return source;
    }

    private static void writeWave(Path path, short[] pcm, int pcmLength, int sampleRate) throws IOException {
        int dataSize = pcmLength * 2;
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(path)))) {
            writeAscii(out, "RIFF");
            writeLe32(out, 36 + dataSize);
            writeAscii(out, "WAVE");
            writeAscii(out, "fmt ");
            writeLe32(out, 16);
            writeLe16(out, 1);
            writeLe16(out, 2);
            writeLe32(out, sampleRate);
            writeLe32(out, sampleRate * 4);
            writeLe16(out, 4);
            writeLe16(out, 16);
            writeAscii(out, "data");
            writeLe32(out, dataSize);
            for (int i = 0; i < pcmLength; i++) {
                writeLe16(out, pcm[i]);
            }
        }
    }

    private static void writeAscii(DataOutputStream out, String value) throws IOException {
        out.writeBytes(value);
    }

    private static void writeLe16(DataOutputStream out, int value) throws IOException {
        out.writeByte(value & 0xFF);
        out.writeByte((value >>> 8) & 0xFF);
    }

    private static void writeLe32(DataOutputStream out, int value) throws IOException {
        out.writeByte(value & 0xFF);
        out.writeByte((value >>> 8) & 0xFF);
        out.writeByte((value >>> 16) & 0xFF);
        out.writeByte((value >>> 24) & 0xFF);
    }
}
