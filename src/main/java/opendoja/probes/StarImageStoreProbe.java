package opendoja.probes;

import com.docomostar.StarApplication;
import com.docomostar.media.MediaImage;
import com.docomostar.media.MediaManager;
import com.docomostar.system.ImageStore;
import com.docomostar.system.StoreException;
import opendoja.host.*;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

/** Confirms STAR and DoJa see the same store IDs, encoded bytes, resources and error statuses. */
public final class StarImageStoreProbe {
    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless","true");
        DesktopLauncher.launch(LaunchConfig.builder(ProbeApp.class).parameter("AppType","FullApp").parameter("AccessUserInfo","yes").build());
        try {
            var png = new java.awt.image.BufferedImage(2,2,java.awt.image.BufferedImage.TYPE_INT_ARGB);
            png.setRGB(0,0,0xff00ff00);
            var output = new ByteArrayOutputStream(); javax.imageio.ImageIO.write(png,"png",output);
            byte[] bytes=output.toByteArray();
            MediaImage image=MediaManager.getImage(bytes); image.use();
            int id=ImageStore.addEntry(image,false);
            var nativeEntry=com.nttdocomo.system.ImageStore.getEntry(id);
            byte[] stored=nativeEntry.getInputStream().readAllBytes();
            check(stored.length>0,"DoJa sees the registered managed image");
            ImageStore entry=ImageStore.getEntry(id);
            check(entry.getId()==id,"Store ID retained");
            check(Arrays.equals(stored,entry.getInputStream().readAllBytes()),"STAR reads the same native encoded snapshot bytes");
            check(entry.getImage()==entry.getImage(),"Media wrapper identity is stable per entry");
            entry.getImage().use();
            nativeEntry.getImage().use();
            var starPixels=com.docomostar.ui.Image.createImage(2,2).getGraphics();
            starPixels.drawImage(entry.getImage().getImage(),0,0);
            var nativePixels=com.nttdocomo.ui.Image.createImage(2,2).getGraphics();
            nativePixels.drawImage(nativeEntry.getImage().getImage(),0,0);
            check(starPixels.getRGBPixel(0,0)==(nativePixels.getRGBPixel(0,0)&0x00ffffff),"Managed image uses the same native snapshot decoder");
            int nativeId=com.nttdocomo.system.ImageStore.addEntry((com.nttdocomo.ui.MediaImage)MediaManager.unwrap(image),false);
            check(ImageStore.getEntry(nativeId).getId()==nativeId,"STAR sees DoJa registration");
            try { ImageStore.getEntry(Integer.MAX_VALUE); throw new AssertionError("Missing entry accepted"); }
            catch(StoreException failure) {
                check(failure.getStatus()==StoreException.NOT_FOUND,"Native status translated");
                check(failure.getCause() instanceof com.nttdocomo.system.StoreException,"Native cause retained");
            }
        } finally { DoJaRuntime.current().shutdown(); }
        System.out.println("star-image-store-probe-ok");
    }
    private static void check(boolean condition,String message) { if(!condition) throw new AssertionError(message); }
    public static final class ProbeApp extends StarApplication { @Override public void started(int reason) { } }
}
