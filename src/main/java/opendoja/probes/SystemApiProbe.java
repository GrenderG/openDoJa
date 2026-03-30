package opendoja.probes;

import com.nttdocomo.device.location.Degree;
import com.nttdocomo.device.location.Location;
import com.nttdocomo.lang.XString;
import com.nttdocomo.system.Alarm;
import com.nttdocomo.system.ApplicationStore;
import com.nttdocomo.system.AvatarStore;
import com.nttdocomo.system.Bookmark;
import com.nttdocomo.system.CallRecord;
import com.nttdocomo.system.CertificateStore;
import com.nttdocomo.system.DTVParameter;
import com.nttdocomo.system.DTVSchedule;
import com.nttdocomo.system.DTVScheduleParam;
import com.nttdocomo.system.DataBoxFolder;
import com.nttdocomo.system.DecomailDraft;
import com.nttdocomo.system.DecomailTemplateStore;
import com.nttdocomo.system.ImageStore;
import com.nttdocomo.system.InterruptedOperationException;
import com.nttdocomo.system.LocationRecord;
import com.nttdocomo.system.MailAgent;
import com.nttdocomo.system.MailConstants;
import com.nttdocomo.system.MailDraft;
import com.nttdocomo.system.MessageAgent;
import com.nttdocomo.system.MessageDraft;
import com.nttdocomo.system.MessageSent;
import com.nttdocomo.system.MovieStore;
import com.nttdocomo.system.OwnerProfile;
import com.nttdocomo.system.PhoneBook;
import com.nttdocomo.system.PhoneBookGroup;
import com.nttdocomo.system.PhoneBookParam;
import com.nttdocomo.system.Schedule;
import com.nttdocomo.system.SoundStore;
import com.nttdocomo.system.StoreException;
import com.nttdocomo.system.Toruca;
import com.nttdocomo.system.TorucaStore;
import com.nttdocomo.ui.Graphics;
import com.nttdocomo.ui.IApplication;
import com.nttdocomo.ui.MApplication;
import com.nttdocomo.ui.MediaManager;
import com.nttdocomo.ui.MediaResource;
import com.nttdocomo.ui.PhoneSystem;
import com.nttdocomo.ui.UIException;
import com.nttdocomo.util.ScheduleDate;
import com.nttdocomo.util.Timer;
import opendoja.host.DoJaEncoding;
import opendoja.host.DoJaRuntime;
import opendoja.host.LaunchConfig;
import opendoja.host.system.DoJaSystemRegistry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Calendar;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Headless verification probe for the recreated {@code com.nttdocomo.system} package.
 */
public final class SystemApiProbe {
    private SystemApiProbe() {
    }

    /**
     * Executes the probe and prints one summary line on success.
     *
     * @param args ignored
     * @throws Exception if any assertion fails
     */
    public static void main(String[] args) throws Exception {
        System.setProperty(opendoja.host.OpenDoJaLaunchArgs.OWNER_PHONE1, "09012345678");
        System.setProperty(opendoja.host.OpenDoJaLaunchArgs.OWNER_EMAIL1, "owner@example.com");

        LaunchConfig config = LaunchConfig.builder(DummyApp.class)
                .launchType(IApplication.LAUNCHED_FROM_MENU)
                .parameter("AccessUserInfo", "true")
                .parameter("LaunchApp", "true")
                .scratchpadRoot(Path.of("/tmp/opendoja-system-probe"))
                .build();
        DoJaRuntime.prepareLaunch(config);
        DoJaRuntime runtime = DoJaRuntime.bootstrap(config);
        try {
            DummyApp app = new DummyApp();
            runtime.attachApplication(app);
            runProbe();
        } finally {
            runtime.shutdown();
            DoJaRuntime.clearPreparedLaunch();
        }
    }

    private static void runProbe() throws Exception {
        ApplicationStore app1 = ApplicationStore.selectEntry();
        ApplicationStore app2 = ApplicationStore.selectEntry();
        check(app1 != null && app1.getId() > 0, "application entry");
        check(app1.getId() == app2.getId(), "application id stability");

        int bookmarkId = Bookmark.addEntry("http://example.com", "Example");
        check(bookmarkId > 0, "bookmark add");

        var avatar = MediaManager.getAvatarData(new byte[]{1, 2, 3});
        avatar.use();
        int avatarId = AvatarStore.addEntry(avatar);
        check(avatarId > 0, "avatar add");
        check(AvatarStore.selectEntryId() == avatarId, "avatar select");
        check(CertificateStore.selectEntryId() == CertificateStore.CERTIFICATE_UIM_ID, "certificate select");

        int groupId = PhoneBookGroup.addEntry("Friends");
        check(groupId > 0, "phone-book group add");
        check("Friends".equals(PhoneBookGroup.getEntry(groupId).getName().toString()), "phone-book group get");

        PhoneBookParam param = new PhoneBookParam();
        param.setName("Docomo Tester");
        param.setKana("DOCOMO TESTER");
        param.addPhoneNumber("09012345678");
        param.addMailAddress("tester@example.com");
        param.setGroupId(groupId);
        param.setLocation(new Location(new Degree(35.0), new Degree(139.0)));
        int[] phoneBookIds = PhoneBook.addEntry(param);
        check(phoneBookIds != null && phoneBookIds.length == 2, "phone-book add");

        PhoneBook phoneBook = PhoneBook.getEntry(phoneBookIds[0]);
        check(phoneBook.getId() == phoneBookIds[0], "phone-book id");
        check("Docomo Tester".equals(phoneBook.getName().toString()), "phone-book name");
        check("DOCOMO TESTER".equals(phoneBook.getKana().toString()), "phone-book kana");
        check("09012345678".equals(phoneBook.getPhoneNumber(0).toString()), "phone-book number");
        check("tester@example.com".equals(phoneBook.getMailAddress(0, MailConstants.ADDRESS_FULL).toString()),
                "phone-book mail");
        check("tester".equals(phoneBook.getMailAddress(0, MailConstants.ADDRESS_USER).toString()),
                "phone-book mail user");
        check("example.com".equals(phoneBook.getMailAddress(0, MailConstants.ADDRESS_DOMAIN).toString()),
                "phone-book mail domain");
        check(phoneBook.getGroupId() == groupId, "phone-book group id");
        check("Friends".equals(phoneBook.getGroupName().toString()), "phone-book group name");
        check(phoneBook.getLocation() != null, "phone-book location");
        check(PhoneBook.selectEntry().getId() == phoneBook.getId(), "phone-book select");

        LocationRecord locationRecord = LocationRecord.selectEntry();
        check(locationRecord != null && locationRecord.getLocation() != null, "location-record select");
        check(LocationRecord.getEntry(locationRecord.getId()).getLocation() != null, "location-record get");

        OwnerProfile owner = OwnerProfile.getProfileData(new int[]{
                OwnerProfile.NAME,
                OwnerProfile.TELEPHONE_NUMBER_1,
                OwnerProfile.EMAIL_ADDRESS_1
        });
        check(owner != null, "owner profile");
        check("09012345678".equals(owner.getData(OwnerProfile.TELEPHONE_NUMBER_1)), "owner phone");
        check("owner@example.com".equals(owner.getData(OwnerProfile.EMAIL_ADDRESS_1)), "owner mail");

        int myPictureFolderId = DataBoxFolder.selectEntryId(DataBoxFolder.FOLDER_MY_PICTURE);
        int movieFolderId = DataBoxFolder.selectEntryId(DataBoxFolder.FOLDER_I_MOTION);
        check(myPictureFolderId > 0 && movieFolderId > 0, "default folders");
        check(DataBoxFolder.addEntry(DataBoxFolder.FOLDER_MY_PICTURE, "Shots") > 0, "folder add");

        var image = MediaManager.createMediaImage(4, 4);
        Graphics imageGraphics = image.getImage().getGraphics();
        imageGraphics.setColor(Graphics.RED);
        imageGraphics.fillRect(0, 0, 4, 4);
        imageGraphics.dispose();
        image.use();
        int imageId = ImageStore.addEntry(image, false);
        check(imageId > 0, "image add");
        check(ImageStore.selectEntryId() == imageId, "image select id");
        ImageStore imageEntry = ImageStore.getEntry(imageId);
        check(imageEntry.getId() == imageId, "image get");
        check(imageEntry.getImage().getWidth() == 4 && imageEntry.getImage().getHeight() == 4, "image size");
        check(ImageStore.getEntryIds(myPictureFolderId).length >= 1, "image ids");
        check(imageEntry.getInputStream().readAllBytes().length > 0, "image stream");

        var movie = MediaManager.createMediaImage(2, 2);
        movie.use();
        int movieId = MovieStore.addEntry(movie);
        check(movieId > 0, "movie add");
        check(MovieStore.getEntry(movieId).getId() == movieId, "movie get");
        check(MovieStore.getEntryIds(movieFolderId).length >= 1, "movie ids");
        check(MovieStore.selectEntry().getId() == movieId, "movie select");

        var soundBytes = Files.readAllBytes(Path.of("resources/mhi_mld/SH900i_mh_se_02.mld"));
        var sound = MediaManager.getSound(soundBytes);
        sound.use();
        int soundId = SoundStore.addEntry(sound);
        check(soundId > 0, "sound add");

        PhoneSystem.setImageTheme(PhoneSystem.THEME_STANDBY, imageId);
        check(Integer.valueOf(imageId).equals(DoJaSystemRegistry.getImageTheme(PhoneSystem.THEME_STANDBY)),
                "image theme");
        PhoneSystem.setImageTheme(PhoneSystem.THEME_AV_CALLING, avatarId);
        check(Integer.valueOf(avatarId).equals(DoJaSystemRegistry.getImageTheme(PhoneSystem.THEME_AV_CALLING)),
                "avatar image theme");
        PhoneSystem.setSoundTheme(PhoneSystem.THEME_MESSAGE_RECEIVE, soundId);
        check(Integer.valueOf(soundId).equals(DoJaSystemRegistry.getSoundTheme(PhoneSystem.THEME_MESSAGE_RECEIVE)),
                "sound theme");
        PhoneSystem.setMovieTheme(PhoneSystem.THEME_CALL_IN, movieId);
        check(Integer.valueOf(movieId).equals(DoJaSystemRegistry.getMovieTheme(PhoneSystem.THEME_CALL_IN)),
                "movie theme");
        PhoneSystem.setMenuIcons(new int[0], new int[]{imageId, -1});
        check(DoJaSystemRegistry.getMenuIcons(new int[0])[0] == imageId, "menu icons");

        CountDownLatch timerLatch = new CountDownLatch(2);
        Timer timer = new Timer();
        timer.setRepeat(true);
        timer.setTime(10);
        timer.setListener(source -> timerLatch.countDown());
        timer.start();
        expectUiException(() -> timer.setRepeat(false), "timer setRepeat after start");
        expectUiException(timer::start, "timer restart while active");
        check(timerLatch.await(500, TimeUnit.MILLISECONDS), "timer fire");
        timer.stop();
        timer.dispose();
        expectUiException(timer::getResolution, "timer getResolution after dispose");

        int templateId = DecomailTemplateStore.addEntry("<html>template</html>");
        check(templateId > 0, "template add");
        check(DecomailTemplateStore.selectEntry().getId() == templateId, "template select");
        check(DecomailTemplateStore.getEntry(templateId).getDecomailTemplate().toString().contains("template"),
                "template get");

        MailDraft mailDraft = new MailDraft("subject", new String[]{"to@example.com"}, "line1\nline2");
        check(MailAgent.getRemainingBytes(mailDraft) < 10000, "mail remaining bytes");
        check(MailAgent.send(mailDraft), "mail send");
        DecomailDraft decomail = new DecomailDraft("subject", new String[]{"to@example.com"}, "<html>ok</html>");
        check(MailAgent.getRemainingBytes(decomail) >= 0, "decomail remaining bytes");
        check(MailAgent.send(decomail), "decomail send");
        check(MailAgent.getLastIncoming() == null, "default unread mail");

        ArrayList<Integer> folderEvents = new ArrayList<>();
        MessageAgent.setMessageFolderListener(folderEvents::add);
        MessageDraft message1 = new MessageDraft("m1", new String[]{"msg1@example.com"}, "body1", new byte[]{1});
        MessageDraft message2 = new MessageDraft("m2", new String[]{"msg2@example.com"}, "body2", new byte[]{2, 3});
        check(MessageAgent.getRemainingBytes(message1) >= 0, "message remaining bytes");
        check(MessageAgent.send(message1), "message send 1");
        check(MessageAgent.send(message2), "message send 2");
        check(MessageAgent.size(MailConstants.SENT, false) == 2, "message sent size");
        int[] sentIds = MessageAgent.getIds(MailConstants.SENT, false);
        check(sentIds.length == 2 && sentIds[0] > sentIds[1], "message id order");
        MessageSent sent = (MessageSent) MessageAgent.getMessage(MailConstants.SENT, sentIds[0]);
        check("m2".equals(sent.getSubject()), "message get");
        check(MessageAgent.send(sent), "message resend");
        check(MessageAgent.size(MailConstants.SENT, false) == 3, "message resend count");
        MessageAgent.delete(MailConstants.SENT, sentIds[1]);
        check(MessageAgent.size(MailConstants.SENT, false) == 2, "message delete");
        MessageAgent.dispatchEvent(MailConstants.RECEIVED, 0, 0);
        check(folderEvents.contains(MailConstants.SENT) && folderEvents.contains(MailConstants.RECEIVED),
                "message folder listener");
        MessageAgent.setMessageFolderListener(null);

        byte[] torucaBytes = ("VERSION=0100\n"
                + "TYPE=card\n"
                + "URL=http://example.com/card\n"
                + "DATA1=A\n"
                + "IPID=ABCDEFGH123\n"
                + "SORTID=sort42\n"
                + "COLOR=7\n"
                + "REDISTRIBUTION=4\n"
                + "MOVE=next\n"
                + "BODY=" + Base64.getEncoder().encodeToString(new byte[]{9, 8, 7}) + '\n')
                .getBytes(DoJaEncoding.DEFAULT_CHARSET);
        Toruca toruca = new Toruca(torucaBytes);
        check("ABCDEFGH123".equals(toruca.getIPID()), "toruca ipid");
        check("sort42".equals(toruca.getSortID()), "toruca sort id");
        check(toruca.getColorID() == 7, "toruca color");
        check("next".equals(toruca.getProperty(MediaResource.X_DCM_MOVE)), "toruca move property");
        int torucaId = TorucaStore.addEntry(toruca);
        check(torucaId > 0, "toruca add");
        check("ABCDEFGH123".equals(TorucaStore.getEntry(torucaId).getToruca().getIPID()), "toruca get");
        int[] foundToruca = TorucaStore.findByHostAndIpid("example.com", "ABCDEFGH999");
        check(foundToruca != null && foundToruca.length == 1 && foundToruca[0] == torucaId, "toruca find");
        check(TorucaStore.getRemainingBytes(toruca) > 0, "toruca remaining");

        ScheduleDate scheduleDate = new ScheduleDate(ScheduleDate.ONETIME);
        scheduleDate.set(Calendar.YEAR, 2026);
        scheduleDate.set(Calendar.MONTH, Calendar.MARCH);
        scheduleDate.set(Calendar.DAY_OF_MONTH, 23);
        scheduleDate.set(Calendar.HOUR_OF_DAY, 12);
        scheduleDate.set(Calendar.MINUTE, 30);
        check((Alarm.getSupportedTypes() & ScheduleDate.ONETIME) != 0, "alarm supported types");
        check(Alarm.addEntry(scheduleDate), "alarm add");
        check(Schedule.addEntry("meeting", scheduleDate, true), "schedule add");
        check(DTVSchedule.addEntry(12, 345, "svc", scheduleDate, "watch-event"), "dtv watch add");

        DTVScheduleParam dtvParam = new DTVScheduleParam();
        dtvParam.setFrequency(13);
        dtvParam.setServiceId(678);
        dtvParam.setAffiliationId(9);
        dtvParam.setServiceName("svc2");
        Calendar start = Calendar.getInstance();
        start.set(2026, Calendar.MARCH, 24, 13, 0, 0);
        start.set(Calendar.MILLISECOND, 0);
        Calendar end = (Calendar) start.clone();
        end.add(Calendar.HOUR_OF_DAY, 1);
        dtvParam.setStartTime(start);
        dtvParam.setEndTime(end);
        dtvParam.setRepeatType(ScheduleDate.ONETIME);
        dtvParam.setEventName("record-event");
        check(DTVSchedule.addEntry(DTVSchedule.TYPE_RECORD, dtvParam), "dtv record add");
        check(DTVParameter.getLastParameter().getServiceId() == 678, "dtv last parameter");

        check(CallRecord.getLastRecord(CallRecord.CALL_IN) == null, "call-record default");

        System.out.println("system-probe-ok app=" + app1.getId()
                + " group=" + groupId
                + " phonebook=" + phoneBook.getId()
                + " image=" + imageId
                + " movie=" + movieId
                + " sound=" + soundId
                + " template=" + templateId
                + " toruca=" + torucaId
                + " sent=" + MessageAgent.size(MailConstants.SENT, false));
    }

    private static void check(boolean condition, String label) {
        if (!condition) {
            throw new IllegalStateException("Probe failed: " + label);
        }
    }

    private static void expectUiException(Runnable action, String label) {
        try {
            action.run();
            throw new IllegalStateException("Probe failed: expected UIException for " + label);
        } catch (UIException expected) {
            // Expected path.
        }
    }

    private static final class DummyApp extends MApplication {
        @Override
        public void start() {
        }
    }
}
