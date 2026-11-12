package opendoja.probes;

import com.docomostar.media.MediaException;
import com.docomostar.media.MediaManager;
import com.docomostar.system.ContentsException;

/** Ensures media failures use STAR types without changing DoJa error behavior. */
public final class StarMediaExceptionProbe {
    public static void main(String[] args) {
        byte[] invalidImage = {1, 2, 3};
        try { MediaManager.getImage(invalidImage); throw new AssertionError("Invalid STAR image accepted"); }
        catch (MediaException expected) {
            check(expected.getStatus() == MediaException.UNSUPPORTED_FORMAT, "STAR media status");
            check(expected.getCause() instanceof com.nttdocomo.ui.UIException, "Original media failure retained");
        }
        try { com.nttdocomo.ui.MediaManager.getImage(invalidImage); throw new AssertionError("Invalid DoJa image accepted"); }
        catch (com.nttdocomo.ui.UIException expected) {
            check(expected.getStatus() == com.nttdocomo.ui.UIException.UNSUPPORTED_FORMAT, "DoJa error type and status unchanged");
        }
        var contents = new ContentsException(ContentsException.RESPONSE_ERROR, "response failed", "body");
        check(contents.getStatus() == 1 && "body".equals(contents.getResponseBody()), "Contents error details");
        check("A+B%2B".equals(com.docomostar.net.URLEncoder.encode("A B+")), "Shared URL encoder");
        System.out.println("star-media-exception-probe-ok");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
