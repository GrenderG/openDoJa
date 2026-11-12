package opendoja.probes;

import com.docomostar.lang.MemoryManager;
import com.docomostar.util.Base64;
import com.docomostar.util.JarInflater;
import java.io.*;
import java.util.Arrays;
import java.util.zip.*;

/** Exercises real compressed script access and utility descriptor adaptation. */
public final class StarUtilityProbe {
    public static void main(String[] args) throws Exception {
        byte[] script = "STAR script data".repeat(30).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var bytes = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("Script/00.dat")); zip.write(script); zip.closeEntry();
        }
        var inflater = new JarInflater(bytes.toByteArray());
        check(inflater.getSize("Script/00.dat") == script.length, "Inflated script size");
        check(Arrays.equals(inflater.getInputStream("Script/00.dat").readAllBytes(), script), "Inflated script contents");
        check(inflater.getSize("missing") == -1 && inflater.getInputStream("missing") == null, "Missing script contract");
        var second = new JarInflater(new ByteArrayInputStream(bytes.toByteArray()));
        check(second.getSize("Script/00.dat") == script.length, "Stream constructor descriptor"); second.close();
        inflater.close();
        try { inflater.getSize("Script/00.dat"); throw new AssertionError("Closed inflater accepted"); }
        catch (com.docomostar.lang.IllegalStateException expected) { }
        check("AQID".equals(Base64.encode(new byte[]{0, 1, 2, 3, 4}, 1, 3)), "Base64 slice encoding");
        check(Arrays.equals(Base64.decode("AQID"), new byte[]{1, 2, 3}), "Base64 decoding");
        var memory = MemoryManager.getMemoryManager();
        check(memory == MemoryManager.getMemoryManager(), "Memory singleton");
        check(memory.totalMemory().length == memory.getNumPartitions()
                && memory.freeMemory().length == memory.getNumPartitions()
                && memory.maxContiguousMemory().length == memory.getNumPartitions(), "Memory partition reports");
        check(com.docomostar.opt.ui.j3d.Math.sin(1024) == 4096, "Fixed-point sine");
        check(com.docomostar.opt.ui.j3d.Math.cos(0) == 4096, "Fixed-point cosine");
        check(com.docomostar.opt.ui.j3d.Math.sqrt(81) == 9, "Integer square root");
        System.out.println("star-utility-probe-ok");
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
