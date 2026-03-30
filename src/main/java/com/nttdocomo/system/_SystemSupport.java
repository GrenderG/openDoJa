package com.nttdocomo.system;

import com.nttdocomo.device.location.Degree;
import com.nttdocomo.device.location.Location;
import com.nttdocomo.device.location.LocationProvider;
import com.nttdocomo.lang.XString;
import com.nttdocomo.ui.AvatarData;
import com.nttdocomo.ui.EncodedImage;
import com.nttdocomo.ui.IApplication;
import com.nttdocomo.ui.ImageEncoder;
import com.nttdocomo.ui.MediaImage;
import com.nttdocomo.ui.MediaResource;
import com.nttdocomo.ui.MediaSound;
import com.nttdocomo.ui.MApplication;
import com.nttdocomo.ui.UIException;
import com.nttdocomo.util.ScheduleDate;
import opendoja.host.DoJaEncoding;
import opendoja.host.DoJaRuntime;
import opendoja.host.system.DoJaSystemRegistry;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Shared host-side implementation for the DoJa system package.
 */
final class _SystemSupport {
    static final ZoneId JST = ZoneId.of("Asia/Tokyo");
    static final int MAIL_SUBJECT_MAX_BYTES = 200;
    static final int MAIL_ADDRESS_MAX_BYTES = 50;
    static final int MAIL_BODY_MAX_BYTES = 10000;
    static final int MESSAGE_TOTAL_MAX_BYTES = 10000;
    static final int RECIPIENT_MAX = 5;
    static final int PHONE_BOOK_ITEM_MAX = 3;
    static final int BOOKMARK_URL_MAX_BYTES = 1024;
    static final int TORUCA_URL_MAX_BYTES = 1024;
    static final int IMAGE_ARRAY_MAX_LEN = 16;
    static final int CERTIFICATE_UIM_ID = 0;
    static final int PROFILE_ITEM_MIN = 1;
    static final int PROFILE_ITEM_MAX = 20;

    private static final Object LOCK = new Object();

    private static final AtomicInteger applicationIds = new AtomicInteger(1);
    private static final AtomicInteger bookmarkIds = new AtomicInteger(1);
    private static final AtomicInteger templateIds = new AtomicInteger(1);
    private static final AtomicInteger phoneBookGroupIds = new AtomicInteger(1);
    private static final AtomicInteger phoneBookIds = new AtomicInteger(1);
    private static final AtomicInteger locationRecordIds = new AtomicInteger(1);
    private static final AtomicInteger dataBoxFolderIds = new AtomicInteger(1000);
    private static final AtomicInteger themeEntryIds = new AtomicInteger(10000);
    private static final AtomicInteger torucaIds = new AtomicInteger(20000);
    private static final AtomicInteger scheduleIds = new AtomicInteger(30000);
    private static final AtomicInteger callRecordIds = new AtomicInteger(40000);
    private static final AtomicInteger messageReceivedIds = new AtomicInteger(1);
    private static final AtomicInteger messageSentIds = new AtomicInteger(1);
    private static final AtomicInteger messageUnsentIds = new AtomicInteger(1);

    private static final LinkedHashMap<Integer, ApplicationEntry> applications = new LinkedHashMap<>();
    private static final LinkedHashMap<Integer, BookmarkEntry> bookmarks = new LinkedHashMap<>();
    private static final LinkedHashMap<Integer, String> decomailTemplates = new LinkedHashMap<>();
    private static final LinkedHashMap<Integer, PhoneBookGroupEntry> phoneBookGroups = new LinkedHashMap<>();
    private static final LinkedHashMap<Integer, PhoneBookEntry> phoneBooks = new LinkedHashMap<>();
    private static final LinkedHashMap<Integer, LocationRecord> locationRecords = new LinkedHashMap<>();
    private static final LinkedHashMap<Integer, DataBoxFolderEntry> dataBoxFolders = new LinkedHashMap<>();
    private static final LinkedHashMap<Integer, ImageEntry> imageEntries = new LinkedHashMap<>();
    private static final LinkedHashMap<Integer, ImageEntry> movieEntries = new LinkedHashMap<>();
    private static final LinkedHashMap<Integer, MediaSound> soundEntries = new LinkedHashMap<>();
    private static final LinkedHashMap<Integer, AvatarData> avatarEntries = new LinkedHashMap<>();
    private static final LinkedHashMap<Integer, Toruca> torucaEntries = new LinkedHashMap<>();
    private static final LinkedHashMap<Integer, ScheduleEntry> schedules = new LinkedHashMap<>();
    private static final LinkedHashMap<Integer, ScheduleEntry> alarms = new LinkedHashMap<>();
    private static final LinkedHashMap<Integer, DtvScheduleEntry> dtvSchedules = new LinkedHashMap<>();
    private static final List<CallRecord> incomingCallRecords = new ArrayList<>();
    private static final List<CallRecord> outgoingCallRecords = new ArrayList<>();
    private static final LinkedHashMap<Integer, MessageReceived> receivedMessages = new LinkedHashMap<>();
    private static final LinkedHashMap<Integer, MessageSent> sentMessages = new LinkedHashMap<>();
    private static final LinkedHashMap<Integer, MessageSent> unsentMessages = new LinkedHashMap<>();

    private static volatile DTVParameter lastDtvParameter;
    private static volatile Mail lastIncomingMail;
    private static volatile MessageFolderListener messageFolderListener;

    static {
        createDefaultFolder(DataBoxFolder.FOLDER_MY_PICTURE, "openDoJa");
        createDefaultFolder(DataBoxFolder.FOLDER_I_MOTION, "openDoJa");
    }

    private _SystemSupport() {
    }

    static void ensureRuntimeActive() {
        DoJaRuntime runtime = DoJaRuntime.current();
        if (runtime != null && runtime.application() instanceof MApplication application && !application.isActive()) {
            throw new IllegalStateException("Application is inactive");
        }
    }

    static void ensureAccessUserInfo() {
        ensureRuntimeParameter("AccessUserInfo");
    }

    static void ensureLaunchAppPermission() {
        ensureRuntimeParameter("LaunchApp");
    }

    private static void ensureRuntimeParameter(String key) {
        DoJaRuntime runtime = DoJaRuntime.current();
        if (runtime == null) {
            throw new SecurityException(key + " requires an active DoJa runtime");
        }
        String value = runtime.parameters().get(key);
        if (value == null || value.isBlank()) {
            throw new SecurityException("ADF " + key + " permission is required");
        }
    }

    static int supportedScheduleTypes() {
        return ScheduleDate.ONETIME
                | ScheduleDate.DAILY
                | ScheduleDate.WEEKLY
                | ScheduleDate.MONTHLY
                | ScheduleDate.YEARLY;
    }

    static String normalizeMailBody(String body) {
        if (body == null) {
            return null;
        }
        String normalized = body.replace("\r\n", "\n").replace('\r', '\n');
        return normalized.replace("\n", "\r\n");
    }

    static String normalizeDecomailBody(String body) {
        if (body == null) {
            return null;
        }
        return body.replace("\r", "").replace("\n", "");
    }

    static String addressPart(String address, int part) {
        validateAddressPart(part);
        if (address == null) {
            return null;
        }
        if (part == MailConstants.ADDRESS_FULL) {
            return address;
        }
        int at = address.indexOf('@');
        if (part == MailConstants.ADDRESS_USER) {
            return at <= 0 ? "" : address.substring(0, at);
        }
        return at < 0 || at + 1 >= address.length() ? "" : address.substring(at + 1);
    }

    static XString xAddressPart(String address, int part) {
        String value = addressPart(address, part);
        return value == null ? null : new XString(value);
    }

    static XString[] xAddressParts(String[] addresses, int part, boolean filterEmpty) {
        validateAddressPart(part);
        if (addresses == null || addresses.length == 0) {
            return null;
        }
        List<XString> converted = new ArrayList<>(addresses.length);
        for (String address : addresses) {
            if (address == null) {
                continue;
            }
            String partValue = addressPart(address, part);
            if (partValue == null) {
                continue;
            }
            if (filterEmpty && part == MailConstants.ADDRESS_FULL && partValue.isEmpty()) {
                continue;
            }
            converted.add(new XString(partValue));
        }
        return converted.isEmpty() ? null : converted.toArray(new XString[0]);
    }

    static String[] copyStrings(String[] values) {
        return values == null ? null : values.clone();
    }

    static byte[] copyBytes(byte[] value) {
        return value == null ? null : value.clone();
    }

    static byte[][] copyBytes2(byte[][] value) {
        if (value == null) {
            return null;
        }
        byte[][] copy = new byte[value.length][];
        for (int i = 0; i < value.length; i++) {
            copy[i] = copyBytes(value[i]);
        }
        return copy;
    }

    static int[][] copyIntMatrix(int[][] value) {
        if (value == null) {
            return null;
        }
        int[][] copy = new int[value.length][];
        for (int i = 0; i < value.length; i++) {
            copy[i] = value[i] == null ? null : value[i].clone();
        }
        return copy;
    }

    static boolean[] copyBooleans(boolean[] value) {
        return value == null ? null : value.clone();
    }

    static XString[] copyXStrings(XString[] value) {
        return value == null ? null : value.clone();
    }

    static Location copyLocation(Location location) {
        if (location == null) {
            return null;
        }
        return new Location(new Degree(location.getLatitude().getFloatingPointNumber()),
                new Degree(location.getLongitude().getFloatingPointNumber()),
                location.getAltitude(), location.getDatum(), location.getTimestamp(), location.getAccuracy());
    }

    static String combineName(String single, String family, String given) {
        if (single != null) {
            return single;
        }
        String left = family == null ? "" : family;
        String right = given == null ? "" : given;
        return left + right;
    }

    static void validateAddressPart(int part) {
        if (part != MailConstants.ADDRESS_FULL
                && part != MailConstants.ADDRESS_USER
                && part != MailConstants.ADDRESS_DOMAIN) {
            throw new IllegalArgumentException("part out of range: " + part);
        }
    }

    static void validatePhoneBookPart(int part) {
        if (part != PhoneBookConstants.FAMILY_NAME && part != PhoneBookConstants.GIVEN_NAME) {
            throw new IllegalArgumentException("part out of range: " + part);
        }
    }

    static void validateProfileItems(int[] items) {
        if (items == null) {
            return;
        }
        for (int item : items) {
            if (item < PROFILE_ITEM_MIN || item > PROFILE_ITEM_MAX) {
                throw new IllegalArgumentException("Invalid profile item: " + item);
            }
        }
    }

    static void validateScheduleDate(ScheduleDate date) {
        if (date == null) {
            return;
        }
        int type = date.getType();
        if ((supportedScheduleTypes() & type) == 0) {
            throw new IllegalArgumentException("Unsupported schedule type: " + type);
        }
        int year = date.get(java.util.Calendar.YEAR);
        if (year > 0 && year < 1970) {
            throw new IllegalArgumentException("Unsupported schedule year: " + year);
        }
    }

    static void validateMailText(String value, boolean multiline) {
        if (value == null) {
            return;
        }
        validateControlCharacters(value, multiline);
        if (byteLength(value) > MAIL_BODY_MAX_BYTES) {
            throw new IllegalArgumentException("Text exceeds supported size");
        }
    }

    static void validateMailSubject(String subject) {
        if (subject == null) {
            return;
        }
        validateControlCharacters(subject, false);
        if (byteLength(subject) > MAIL_SUBJECT_MAX_BYTES) {
            throw new IllegalArgumentException("Subject exceeds supported size");
        }
    }

    static void validateMailAddress(String address) {
        if (address == null || address.isEmpty()) {
            throw new IllegalArgumentException("Address must not be empty");
        }
        validateControlCharacters(address, false);
        if (byteLength(address) > MAIL_ADDRESS_MAX_BYTES) {
            throw new IllegalArgumentException("Address exceeds supported size");
        }
    }

    static void validateMailAddressArray(String[] addresses) {
        if (addresses == null) {
            return;
        }
        if (addresses.length > RECIPIENT_MAX) {
            throw new IllegalArgumentException("Too many addresses: " + addresses.length);
        }
        for (String address : addresses) {
            if (address == null) {
                throw new NullPointerException("addresses");
            }
            if (!address.isEmpty()) {
                validateMailAddress(address);
            }
        }
    }

    static void validateMessageAddressArray(String[] addresses) {
        if (addresses == null) {
            throw new NullPointerException("addresses");
        }
        if (addresses.length == 0 || addresses.length > RECIPIENT_MAX) {
            throw new IllegalArgumentException("Invalid address count: " + addresses.length);
        }
        for (String address : addresses) {
            if (address == null) {
                throw new NullPointerException("addresses");
            }
            validateMailAddress(address);
        }
    }

    static void validateMessageData(byte[] data) {
        if (data != null && data.length > MESSAGE_TOTAL_MAX_BYTES) {
            throw new IllegalArgumentException("Data exceeds supported size");
        }
    }

    static void validatePhoneNumbers(String[] phoneNumbers) {
        if (phoneNumbers == null) {
            return;
        }
        if (phoneNumbers.length > PHONE_BOOK_ITEM_MAX) {
            throw new IllegalArgumentException("Too many phone numbers: " + phoneNumbers.length);
        }
        for (String number : phoneNumbers) {
            if (number == null) {
                throw new NullPointerException("phoneNumbers");
            }
            validatePhoneNumber(number);
        }
    }

    static void validateMailAddressesForPhoneBook(String[] mailAddresses) {
        if (mailAddresses == null) {
            return;
        }
        if (mailAddresses.length > PHONE_BOOK_ITEM_MAX) {
            throw new IllegalArgumentException("Too many mail addresses: " + mailAddresses.length);
        }
        for (String address : mailAddresses) {
            if (address == null) {
                throw new NullPointerException("mailAddresses");
            }
            if (!address.isEmpty()) {
                validateMailAddress(address);
            }
        }
    }

    static void validatePhoneNumber(String phoneNumber) {
        if (phoneNumber == null) {
            throw new NullPointerException("phoneNumber");
        }
        if (phoneNumber.isEmpty()) {
            return;
        }
        if (phoneNumber.indexOf('/') >= 0 || phoneNumber.indexOf(',') >= 0) {
            throw new IllegalArgumentException("Illegal phone number: " + phoneNumber);
        }
        for (int i = 0; i < phoneNumber.length(); i++) {
            char ch = phoneNumber.charAt(i);
            if (Character.isISOControl(ch)) {
                throw new IllegalArgumentException("Illegal phone number: " + phoneNumber);
            }
        }
        if (byteLength(phoneNumber) > MAIL_ADDRESS_MAX_BYTES) {
            throw new IllegalArgumentException("Phone number exceeds supported size");
        }
    }

    static void validateBookmarkUrl(String url) {
        Objects.requireNonNull(url, "url");
        validateHttpUrl(url, BOOKMARK_URL_MAX_BYTES);
    }

    static void validateTorucaUrl(String url) {
        if (url == null) {
            return;
        }
        validateHttpUrl(url, TORUCA_URL_MAX_BYTES);
    }

    static void validateHttpUrl(String url, int maxBytes) {
        validateControlCharacters(url, false);
        if (byteLength(url) > maxBytes) {
            throw new IllegalArgumentException("URL exceeds supported size");
        }
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            if (scheme == null
                    || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
                throw new IllegalArgumentException("Unsupported URL scheme: " + url);
            }
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("Illegal URL: " + url, exception);
        }
    }

    static void validateTorucaVersion(byte[] version) {
        Objects.requireNonNull(version, "version");
        if (version.length < 2) {
            throw new IllegalArgumentException("version must contain two bytes");
        }
        if ((version[0] & 0xFF) == 0 || (version[0] & 0xFF) > 0x09 || (version[1] & 0xFF) > 0x09) {
            throw new IllegalArgumentException("Unsupported toruca version");
        }
        if ((version[0] & 0xFF) > 0x01) {
            throw new IllegalArgumentException("Unsupported toruca major version");
        }
    }

    static byte[] validateTorucaCategory(byte[] code) {
        if (code == null) {
            return null;
        }
        if (code.length < 2) {
            throw new IllegalArgumentException("Category code must contain two bytes");
        }
        return new byte[]{code[0], code[1]};
    }

    static int byteLength(String value) {
        return value == null ? 0 : value.getBytes(DoJaEncoding.DEFAULT_CHARSET).length;
    }

    static int mailRemainingBytes(MailDraft mail) {
        Objects.requireNonNull(mail, "mail");
        String normalized = normalizeMailBody(mail.body);
        return MAIL_BODY_MAX_BYTES - byteLength(normalized);
    }

    static int decomailRemainingBytes(DecomailDraft mail) {
        Objects.requireNonNull(mail, "mail");
        String normalized = normalizeDecomailBody(mail.body);
        return MAIL_BODY_MAX_BYTES - byteLength(normalized);
    }

    static int messageRemainingBytes(MessageDraft draft) {
        Objects.requireNonNull(draft, "message");
        String normalizedBody = normalizeMailBody(draft.body);
        return MESSAGE_TOTAL_MAX_BYTES - byteLength(normalizedBody) - (draft.data == null ? 0 : draft.data.length);
    }

    static void validateMediaResource(MediaResource resource, String name) {
        if (resource == null) {
            throw new NullPointerException(name);
        }
        if (!isMediaResourceUsed(resource)) {
            throw new UIException(UIException.ILLEGAL_STATE, name + " must be in use");
        }
    }

    private static boolean isMediaResourceUsed(MediaResource resource) {
        try {
            Method method = resource.getClass().getSuperclass().getDeclaredMethod("isUsed");
            method.setAccessible(true);
            Object result = method.invoke(resource);
            if (result instanceof Boolean booleanValue) {
                return booleanValue;
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return true;
    }

    private static void validateControlCharacters(String value, boolean multiline) {
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch == '\t' && multiline) {
                continue;
            }
            if ((ch == '\r' || ch == '\n') && multiline) {
                continue;
            }
            if (Character.isISOControl(ch)) {
                throw new IllegalArgumentException("Unsupported control character");
            }
        }
    }

    static ApplicationStore selectApplication() {
        ensureLaunchAppPermission();
        ensureRuntimeActive();
        synchronized (LOCK) {
            ApplicationEntry entry = currentApplicationEntry();
            return entry == null ? null : new ApplicationStore(entry.id);
        }
    }

    static int addBookmark(String url, String title) {
        ensureAccessUserInfo();
        ensureRuntimeActive();
        validateBookmarkUrl(url);
        synchronized (LOCK) {
            int id = bookmarkIds.getAndIncrement();
            bookmarks.put(id, new BookmarkEntry(id, url, title));
            return id;
        }
    }

    static int addAvatar(AvatarData avatar) {
        validateMediaResource(avatar, "avatar");
        synchronized (LOCK) {
            int id = themeEntryIds.getAndIncrement();
            avatarEntries.put(id, avatar);
            DoJaSystemRegistry.registerEntry(id, DoJaSystemRegistry.EntryKind.AVATAR);
            return id;
        }
    }

    static int selectAvatarEntryId() {
        ensureRuntimeActive();
        synchronized (LOCK) {
            return avatarEntries.isEmpty() ? -1 : lastKey(avatarEntries);
        }
    }

    static int selectCertificateEntryId() {
        ensureRuntimeActive();
        return CERTIFICATE_UIM_ID;
    }

    static boolean addAlarm(ScheduleDate date) {
        ensureAccessUserInfo();
        ensureRuntimeActive();
        validateScheduleDate(date);
        synchronized (LOCK) {
            alarms.put(scheduleIds.getAndIncrement(), new ScheduleEntry(null, copyScheduleDate(date), true));
            return true;
        }
    }

    static boolean addSchedule(String description, ScheduleDate date, boolean alarm) {
        ensureAccessUserInfo();
        ensureRuntimeActive();
        validateScheduleDate(date);
        synchronized (LOCK) {
            schedules.put(scheduleIds.getAndIncrement(), new ScheduleEntry(description, copyScheduleDate(date), alarm));
            return true;
        }
    }

    static DTVParameter getLastDtvParameter() {
        return lastDtvParameter;
    }

    static boolean addDtvSchedule(int type, DTVScheduleParam param) {
        ensureAccessUserInfo();
        ensureRuntimeActive();
        Objects.requireNonNull(param, "param");
        if (type != DTVSchedule.TYPE_WATCH && type != DTVSchedule.TYPE_RECORD) {
            throw new IllegalArgumentException("type out of range: " + type);
        }
        if (param.startTime == null) {
            throw new NullPointerException("param.startTime");
        }
        if (type == DTVSchedule.TYPE_RECORD && param.endTime == null) {
            throw new NullPointerException("param.endTime");
        }
        if (param.frequency == DTVSchedule.FREQUENCY_NONE
                && param.serviceId == DTVSchedule.SERVICE_ID_NONE
                && param.affiliationId == DTVSchedule.AFFILIATION_ID_NONE) {
            throw new IllegalArgumentException("At least one channel identifier must be specified");
        }
        if ((supportedScheduleTypes() & param.repeatType) == 0) {
            throw new IllegalArgumentException("Unsupported repeat type: " + param.repeatType);
        }
        synchronized (LOCK) {
            dtvSchedules.put(scheduleIds.getAndIncrement(), new DtvScheduleEntry(type, param.copy()));
            lastDtvParameter = new DTVParameter(param.serviceId, param.startTime.getTimeInMillis());
            return true;
        }
    }

    static int addDataBoxFolder(int folderType, String name) {
        ensureAccessUserInfo();
        ensureRuntimeActive();
        validateFolderType(folderType);
        synchronized (LOCK) {
            int id = dataBoxFolderIds.getAndIncrement();
            dataBoxFolders.put(id, new DataBoxFolderEntry(id, folderType, name == null ? "" : name));
            return id;
        }
    }

    static int selectDataBoxFolder(int folderType) {
        ensureAccessUserInfo();
        ensureRuntimeActive();
        validateFolderType(folderType);
        synchronized (LOCK) {
            for (DataBoxFolderEntry entry : dataBoxFolders.values()) {
                if (entry.folderType == folderType) {
                    return entry.id;
                }
            }
            return -1;
        }
    }

    static int addDecomailTemplate(String template) {
        ensureAccessUserInfo();
        ensureRuntimeActive();
        Objects.requireNonNull(template, "templateData");
        synchronized (LOCK) {
            int id = templateIds.getAndIncrement();
            decomailTemplates.put(id, template);
            return id;
        }
    }

    static DecomailTemplateStore selectDecomailTemplate() {
        ensureAccessUserInfo();
        ensureRuntimeActive();
        synchronized (LOCK) {
            return decomailTemplates.isEmpty() ? null : new DecomailTemplateStore(lastKey(decomailTemplates),
                    decomailTemplates.get(lastKey(decomailTemplates)));
        }
    }

    static DecomailTemplateStore getDecomailTemplate(int id) throws StoreException {
        ensureAccessUserInfo();
        synchronized (LOCK) {
            String template = decomailTemplates.get(id);
            if (template == null) {
                throw new StoreException(StoreException.NOT_FOUND, "Decomail template not found: " + id);
            }
            return new DecomailTemplateStore(id, template);
        }
    }

    static int addImage(MediaImage image, boolean exclusive) {
        ensureAccessUserInfo();
        ensureRuntimeActive();
        validateMediaResource(image, "image");
        synchronized (LOCK) {
            int id = themeEntryIds.getAndIncrement();
            imageEntries.put(id, new ImageEntry(id, snapshotImage(image), ownerKey(), exclusive,
                    defaultFolderId(DataBoxFolder.FOLDER_MY_PICTURE)));
            DoJaSystemRegistry.registerEntry(id, DoJaSystemRegistry.EntryKind.IMAGE);
            return id;
        }
    }

    static int[] addImages(MediaImage[] images) {
        ensureAccessUserInfo();
        ensureRuntimeActive();
        Objects.requireNonNull(images, "images");
        if (images.length == 0) {
            return null;
        }
        if (images.length > IMAGE_ARRAY_MAX_LEN) {
            throw new UIException(UIException.NO_RESOURCES, "Too many images");
        }
        int[] ids = new int[images.length];
        synchronized (LOCK) {
            for (int i = 0; i < images.length; i++) {
                if (images[i] == null) {
                    throw new NullPointerException("images");
                }
                validateMediaResource(images[i], "images[" + i + "]");
                int id = themeEntryIds.getAndIncrement();
                imageEntries.put(id, new ImageEntry(id, snapshotImage(images[i]), ownerKey(),
                        !images[i].isRedistributable(), defaultFolderId(DataBoxFolder.FOLDER_MY_PICTURE)));
                DoJaSystemRegistry.registerEntry(id, DoJaSystemRegistry.EntryKind.IMAGE);
                ids[i] = id;
            }
        }
        return ids;
    }

    static ImageStore selectImageEntry() {
        ensureAccessUserInfo();
        ensureRuntimeActive();
        synchronized (LOCK) {
            ImageEntry entry = lastAccessibleImageEntry(imageEntries);
            return entry == null ? null : new ImageStore(entry);
        }
    }

    static int selectImageEntryId() {
        ensureRuntimeActive();
        synchronized (LOCK) {
            ImageEntry entry = lastAccessibleImageEntry(imageEntries);
            return entry == null ? -1 : entry.id;
        }
    }

    static ImageStore getImageEntry(int id) throws StoreException {
        ensureAccessUserInfo();
        synchronized (LOCK) {
            ImageEntry entry = imageEntries.get(id);
            if (entry == null) {
                throw new StoreException(StoreException.NOT_FOUND, "Image entry not found: " + id);
            }
            if (entry.exclusive && !entry.ownerKey.equals(ownerKey())) {
                throw new SecurityException("Image entry is exclusive");
            }
            return new ImageStore(entry);
        }
    }

    static int[] getImageEntryIds(int folderId) throws StoreException {
        ensureAccessUserInfo();
        synchronized (LOCK) {
            DataBoxFolderEntry folder = dataBoxFolders.get(folderId);
            if (folder == null || folder.folderType != DataBoxFolder.FOLDER_MY_PICTURE) {
                throw new StoreException(StoreException.NOT_FOUND, "Folder not found: " + folderId);
            }
            List<Integer> ids = new ArrayList<>();
            for (ImageEntry entry : imageEntries.values()) {
                if (entry.folderId == folderId && (!entry.exclusive || entry.ownerKey.equals(ownerKey()))) {
                    ids.add(entry.id);
                }
            }
            return ids.isEmpty() ? null : ids.stream().mapToInt(Integer::intValue).toArray();
        }
    }

    static int addMovie(MediaImage movie) {
        ensureAccessUserInfo();
        ensureRuntimeActive();
        validateMediaResource(movie, "movie");
        synchronized (LOCK) {
            int id = themeEntryIds.getAndIncrement();
            movieEntries.put(id, new ImageEntry(id, snapshotImage(movie), ownerKey(), !movie.isRedistributable(),
                    defaultFolderId(DataBoxFolder.FOLDER_I_MOTION)));
            DoJaSystemRegistry.registerEntry(id, DoJaSystemRegistry.EntryKind.MOVIE);
            return id;
        }
    }

    static MovieStore selectMovieEntry() {
        ensureAccessUserInfo();
        ensureRuntimeActive();
        synchronized (LOCK) {
            ImageEntry entry = lastAccessibleImageEntry(movieEntries);
            return entry == null ? null : new MovieStore(entry);
        }
    }

    static MovieStore getMovieEntry(int id) throws StoreException {
        ensureAccessUserInfo();
        synchronized (LOCK) {
            ImageEntry entry = movieEntries.get(id);
            if (entry == null) {
                throw new StoreException(StoreException.NOT_FOUND, "Movie entry not found: " + id);
            }
            if (entry.exclusive && !entry.ownerKey.equals(ownerKey())) {
                throw new SecurityException("Movie entry is exclusive");
            }
            return new MovieStore(entry);
        }
    }

    static int[] getMovieEntryIds(int folderId) throws StoreException {
        ensureAccessUserInfo();
        synchronized (LOCK) {
            DataBoxFolderEntry folder = dataBoxFolders.get(folderId);
            if (folder == null || folder.folderType != DataBoxFolder.FOLDER_I_MOTION) {
                throw new StoreException(StoreException.NOT_FOUND, "Folder not found: " + folderId);
            }
            List<Integer> ids = new ArrayList<>();
            for (ImageEntry entry : movieEntries.values()) {
                if (entry.folderId == folderId && (!entry.exclusive || entry.ownerKey.equals(ownerKey()))) {
                    ids.add(entry.id);
                }
            }
            return ids.isEmpty() ? null : ids.stream().mapToInt(Integer::intValue).toArray();
        }
    }

    static int addSound(MediaSound sound) {
        ensureRuntimeActive();
        validateMediaResource(sound, "sound");
        synchronized (LOCK) {
            int id = themeEntryIds.getAndIncrement();
            soundEntries.put(id, sound);
            DoJaSystemRegistry.registerEntry(id, DoJaSystemRegistry.EntryKind.SOUND);
            return id;
        }
    }

    static LocationRecord selectLocationRecord() {
        ensureRuntimeActive();
        synchronized (LOCK) {
            return locationRecords.isEmpty() ? null : copyLocationRecord(locationRecords.get(lastKey(locationRecords)));
        }
    }

    static LocationRecord getLocationRecord(int id) throws StoreException {
        synchronized (LOCK) {
            LocationRecord record = locationRecords.get(id);
            if (record == null) {
                throw new StoreException(StoreException.NOT_FOUND, "Location record not found: " + id);
            }
            return copyLocationRecord(record);
        }
    }

    static Mail getLastIncomingMail() {
        ensureAccessUserInfo();
        return lastIncomingMail;
    }

    static boolean sendMail(MailDraft mail) throws StoreException {
        ensureRuntimeActive();
        Objects.requireNonNull(mail, "mail");
        if (!(mail.getClass() == MailDraft.class || mail instanceof DecomailDraft)) {
            throw new IllegalArgumentException("Unsupported MailDraft subclass: " + mail.getClass().getName());
        }
        if (mail instanceof DecomailDraft decomail) {
            return sendDecomail(decomail);
        }
        String[] addresses = mail.getRecipients();
        if (addresses == null && mail.getXRecipient() == null) {
            return false;
        }
        if (mailRemainingBytes(mail) < 0) {
            throw new IllegalArgumentException("Mail body exceeds supported size");
        }
        return true;
    }

    static boolean sendDecomail(DecomailDraft mail) throws StoreException {
        ensureRuntimeActive();
        Objects.requireNonNull(mail, "mail");
        String[] addresses = mail.getRecipients();
        if (addresses == null && mail.getXRecipient() == null) {
            return false;
        }
        if (decomailRemainingBytes(mail) < 0) {
            throw new IllegalArgumentException("Decomail body exceeds supported size");
        }
        return true;
    }

    static boolean sendMessage(MessageDraft draft) throws StoreException {
        ensureRuntimeActive();
        Objects.requireNonNull(draft, "message");
        if (draft.getRecipients() == null && draft.getXRecipient() == null && draft.getXRecipients() == null) {
            throw new IllegalArgumentException("No recipient is set");
        }
        if (messageRemainingBytes(draft) < 0) {
            throw new IllegalArgumentException("Message exceeds supported size");
        }
        synchronized (LOCK) {
            int id = messageSentIds.getAndIncrement();
            MessageSent sent = new MessageSent(MailConstants.SENT, id, Instant.now(), ZoneId.systemDefault(),
                    draft.subject, draft.body, copyBytes(draft.data),
                    copyStrings(draft.recipients), draft.xRecipient, copyXStrings(draft.xRecipients));
            sentMessages.put(id, sent);
            if (draft.sourceFolderType == MailConstants.UNSENT && draft.sourceMessageId > 0) {
                unsentMessages.remove(draft.sourceMessageId);
                fireFolderChanged(MailConstants.UNSENT);
            }
            fireFolderChanged(MailConstants.SENT);
            return true;
        }
    }

    static boolean resendMessage(MessageSent message) throws StoreException {
        ensureRuntimeActive();
        Objects.requireNonNull(message, "message");
        MessageDraft draft = new MessageDraft(message, false);
        draft.sourceFolderType = message.getType();
        draft.sourceMessageId = message.getId();
        return sendMessage(draft);
    }

    static int messageCount(int type, boolean unseen) {
        synchronized (LOCK) {
            return switch (validateMessageFolder(type)) {
                case MailConstants.RECEIVED -> unseen
                        ? (int) receivedMessages.values().stream().filter(message -> !message.isSeen()).count()
                        : receivedMessages.size();
                case MailConstants.SENT -> sentMessages.size();
                default -> unsentMessages.size();
            };
        }
    }

    static int[] messageIds(int type, boolean unseen) {
        synchronized (LOCK) {
            List<Integer> ids = new ArrayList<>();
            switch (validateMessageFolder(type)) {
                case MailConstants.RECEIVED -> {
                    for (MessageReceived message : receivedMessages.values()) {
                        if (!unseen || !message.isSeen()) {
                            ids.add(message.getId());
                        }
                    }
                }
                case MailConstants.SENT -> ids.addAll(sentMessages.keySet());
                case MailConstants.UNSENT -> ids.addAll(unsentMessages.keySet());
                default -> {
                }
            }
            Collections.reverse(ids);
            return ids.stream().mapToInt(Integer::intValue).toArray();
        }
    }

    static Message getMessage(int type, int id) {
        synchronized (LOCK) {
            return switch (validateMessageFolder(type)) {
                case MailConstants.RECEIVED -> getReceivedMessage(id);
                case MailConstants.SENT -> getSentMessage(id, sentMessages);
                case MailConstants.UNSENT -> getSentMessage(id, unsentMessages);
                default -> throw new IllegalArgumentException("type out of range: " + type);
            };
        }
    }

    static void deleteMessage(int type, int id) {
        synchronized (LOCK) {
            switch (validateMessageFolder(type)) {
                case MailConstants.RECEIVED -> {
                    if (receivedMessages.remove(id) == null) {
                        throw new IllegalArgumentException("Message not found: " + id);
                    }
                }
                case MailConstants.SENT -> {
                    if (sentMessages.remove(id) == null) {
                        throw new IllegalArgumentException("Message not found: " + id);
                    }
                }
                case MailConstants.UNSENT -> {
                    if (unsentMessages.remove(id) == null) {
                        throw new IllegalArgumentException("Message not found: " + id);
                    }
                }
                default -> throw new IllegalArgumentException("type out of range: " + type);
            }
            fireFolderChanged(type);
        }
    }

    static void setMessageSeen(int id, boolean seen) {
        synchronized (LOCK) {
            MessageReceived message = receivedMessages.get(id);
            if (message == null) {
                throw new IllegalArgumentException("Message not found: " + id);
            }
            if (seen) {
                message.setSeen(true);
            }
        }
    }

    static boolean isMessageSeen(int id) {
        synchronized (LOCK) {
            MessageReceived message = receivedMessages.get(id);
            if (message == null) {
                throw new IllegalArgumentException("Message not found: " + id);
            }
            return message.isSeen();
        }
    }

    static void setMessageFolderListener(MessageFolderListener listener) {
        messageFolderListener = listener;
    }

    static void dispatchMessageFolderEvent(int type, int unused1, int unused2) {
        fireFolderChanged(type);
    }

    static OwnerProfile getOwnerProfile(int[] items) {
        ensureAccessUserInfo();
        ensureRuntimeActive();
        validateProfileItems(items);
        int[] selectedItems = items == null ? defaultProfileItems() : items.clone();
        return new OwnerProfile(selectedItems);
    }

    static PhoneBook selectPhoneBook() {
        ensureAccessUserInfo();
        ensureRuntimeActive();
        synchronized (LOCK) {
            return phoneBooks.isEmpty() ? null : new PhoneBook(phoneBooks.get(lastKey(phoneBooks)));
        }
    }

    static PhoneBook getPhoneBook(int id) throws StoreException {
        ensureAccessUserInfo();
        synchronized (LOCK) {
            PhoneBookEntry entry = phoneBooks.get(id);
            if (entry == null) {
                throw new StoreException(StoreException.NOT_FOUND, "Phone book entry not found: " + id);
            }
            return new PhoneBook(entry);
        }
    }

    static int[] addPhoneBook(PhoneBookParam param) throws StoreException {
        ensureAccessUserInfo();
        ensureRuntimeActive();
        Objects.requireNonNull(param, "param");
        synchronized (LOCK) {
            int groupId = resolvePhoneBookGroup(param.groupId, param.groupName);
            int id = phoneBookIds.getAndIncrement();
            PhoneBookEntry entry = new PhoneBookEntry(id, param.singleName, param.nameParts.clone(),
                    param.singleKana, param.kanaParts.clone(), compactStrings(param.phoneNumbers),
                    compactStrings(param.mailAddresses), groupId, copyLocation(param.location));
            phoneBooks.put(id, entry);
            if (entry.location != null) {
                int recordId = locationRecordIds.getAndIncrement();
                locationRecords.put(recordId, new LocationRecord(recordId, entry.location));
            }
            return new int[]{id, groupId};
        }
    }

    static PhoneBookGroup selectPhoneBookGroup() {
        ensureAccessUserInfo();
        ensureRuntimeActive();
        synchronized (LOCK) {
            return phoneBookGroups.isEmpty() ? null : new PhoneBookGroup(phoneBookGroups.get(lastKey(phoneBookGroups)));
        }
    }

    static PhoneBookGroup getPhoneBookGroup(int id) throws StoreException {
        ensureAccessUserInfo();
        synchronized (LOCK) {
            PhoneBookGroupEntry entry = phoneBookGroups.get(id);
            if (entry == null) {
                throw new StoreException(StoreException.NOT_FOUND, "Phone book group not found: " + id);
            }
            return new PhoneBookGroup(entry);
        }
    }

    static int addPhoneBookGroup(String name) {
        ensureAccessUserInfo();
        ensureRuntimeActive();
        synchronized (LOCK) {
            int id = phoneBookGroupIds.getAndIncrement();
            phoneBookGroups.put(id, new PhoneBookGroupEntry(id, name));
            return id;
        }
    }

    static CallRecord getLastCallRecord(int type) {
        ensureAccessUserInfo();
        List<CallRecord> list = switch (type) {
            case CallRecord.CALL_IN -> incomingCallRecords;
            case CallRecord.CALL_OUT -> outgoingCallRecords;
            default -> throw new IllegalArgumentException("type out of range: " + type);
        };
        synchronized (LOCK) {
            return list.isEmpty() ? null : list.get(list.size() - 1);
        }
    }

    static TorucaStore selectToruca() {
        ensureAccessUserInfo();
        ensureRuntimeActive();
        synchronized (LOCK) {
            return torucaEntries.isEmpty() ? null : new TorucaStore(lastKey(torucaEntries), copyToruca(torucaEntries.get(lastKey(torucaEntries))));
        }
    }

    static TorucaStore getToruca(int id) throws StoreException {
        ensureAccessUserInfo();
        synchronized (LOCK) {
            Toruca toruca = torucaEntries.get(id);
            if (toruca == null) {
                throw new StoreException(StoreException.NOT_FOUND, "Toruca entry not found: " + id);
            }
            return new TorucaStore(id, copyToruca(toruca));
        }
    }

    static int[] findTorucaByHostAndIpid(String host, String ipid) {
        ensureAccessUserInfo();
        ensureRuntimeActive();
        if (host == null || ipid == null || ipid.getBytes(DoJaEncoding.DEFAULT_CHARSET).length <= 7) {
            return null;
        }
        String needle = truncateIpid(ipid);
        synchronized (LOCK) {
            List<Integer> ids = new ArrayList<>();
            for (Map.Entry<Integer, Toruca> entry : torucaEntries.entrySet()) {
                Toruca toruca = entry.getValue();
                if (!Toruca.TYPE_CARD.equals(toruca.getType())) {
                    continue;
                }
                String url = toruca.getURL();
                String torucaHost = hostPart(url);
                String torucaIpid = truncateIpid(toruca.getIPID());
                if (host.equals(torucaHost) && needle.equals(torucaIpid)) {
                    ids.add(entry.getKey());
                }
            }
            return ids.isEmpty() ? null : ids.stream().mapToInt(Integer::intValue).toArray();
        }
    }

    static int addToruca(Toruca toruca) {
        ensureAccessUserInfo();
        ensureRuntimeActive();
        Objects.requireNonNull(toruca, "toruca");
        if (torucaRemainingBytes(toruca) < 0) {
            throw new IllegalArgumentException("Toruca exceeds supported size");
        }
        synchronized (LOCK) {
            int id = torucaIds.getAndIncrement();
            torucaEntries.put(id, copyToruca(toruca));
            return id;
        }
    }

    static int torucaRemainingBytes(Toruca toruca) {
        Objects.requireNonNull(toruca, "toruca");
        int limit = Toruca.TYPE_CARD.equals(toruca.getType()) ? 100 * 1024 : 1024;
        return limit - encodedTorucaBytes(toruca).length;
    }

    static byte[] encodedTorucaBytes(Toruca toruca) {
        StringBuilder builder = new StringBuilder();
        builder.append("VERSION=").append(hex(toruca.getVersion())).append('\n');
        builder.append("TYPE=").append(toruca.getType()).append('\n');
        appendProperty(builder, "URL", toruca.getURL());
        appendProperty(builder, "DATA1", toruca.getData1());
        appendProperty(builder, "DATA2", toruca.getData2());
        appendProperty(builder, "DATA3", toruca.getData3());
        appendProperty(builder, "CATEGORY", hex(toruca.getCategory()));
        appendProperty(builder, "IPID", toruca.getIPID());
        appendProperty(builder, "SORTID", toruca.getSortID());
        builder.append("COLOR=").append(toruca.getColorID()).append('\n');
        builder.append("REDISTRIBUTION=").append(toruca.getRedistributionID()).append('\n');
        if (toruca.expirationDate != null) {
            builder.append("EXPIRATION=").append(toruca.expirationDate.getTime()).append('\n');
        }
        appendProperty(builder, "MOVE", toruca.getProperty(MediaResource.X_DCM_MOVE));
        appendProperty(builder, "BODY", java.util.Base64.getEncoder().encodeToString(copyBytes(toruca.getBody()) == null ? new byte[0] : toruca.getBody()));
        return builder.toString().getBytes(DoJaEncoding.DEFAULT_CHARSET);
    }

    static Toruca parseToruca(byte[] data) {
        Objects.requireNonNull(data, "data");
        String raw = new String(data, DoJaEncoding.DEFAULT_CHARSET);
        if (!raw.contains("VERSION=") || !raw.contains("TYPE=")) {
            throw new IllegalArgumentException("Unsupported toruca format");
        }
        Toruca toruca = new Toruca();
        for (String line : raw.split("\\n")) {
            int separator = line.indexOf('=');
            if (separator <= 0) {
                continue;
            }
            String key = line.substring(0, separator);
            String value = line.substring(separator + 1);
            switch (key) {
                case "VERSION" -> toruca.setVersion(fromHex(value));
                case "TYPE" -> toruca.setType(value);
                case "URL" -> toruca.setURL(emptyToNull(value));
                case "DATA1" -> toruca.setData1(emptyToNull(value));
                case "DATA2" -> toruca.setData2(emptyToNull(value));
                case "DATA3" -> toruca.setData3(emptyToNull(value));
                case "CATEGORY" -> toruca.setCategory(value.isEmpty() ? null : fromHex(value));
                case "IPID" -> toruca.ipid = emptyToNull(value);
                case "SORTID" -> toruca.sortId = emptyToNull(value);
                case "COLOR" -> toruca.colorId = value.isEmpty() ? -1 : Integer.parseInt(value);
                case "REDISTRIBUTION" -> toruca.redistributionId = value.isEmpty() ? -1 : Integer.parseInt(value);
                case "EXPIRATION" -> toruca.expirationDate = value.isEmpty() ? null : new java.util.Date(Long.parseLong(value));
                case "MOVE" -> toruca.setProperty(MediaResource.X_DCM_MOVE, emptyToNull(value));
                case "BODY" -> toruca.setBody(value.isEmpty() ? null : java.util.Base64.getDecoder().decode(value));
                default -> {
                }
            }
        }
        return toruca;
    }

    static String formatInstant(Instant instant, ZoneId zone, String pattern) {
        if (instant == null) {
            return null;
        }
        Objects.requireNonNull(pattern, "pattern");
        java.text.SimpleDateFormat format = new java.text.SimpleDateFormat(pattern);
        format.setTimeZone(TimeZone.getTimeZone(zone));
        return format.format(java.util.Date.from(instant));
    }

    static int[][] matchPhoneNumber(String phoneNumber) {
        synchronized (LOCK) {
            List<int[]> matches = new ArrayList<>();
            for (PhoneBookEntry entry : phoneBooks.values()) {
                for (int i = 0; i < entry.phoneNumbers.length; i++) {
                    if (entry.phoneNumbers[i].equals(phoneNumber)) {
                        matches.add(new int[]{entry.id, i});
                    }
                }
            }
            return matches.isEmpty() ? null : matches.toArray(int[][]::new);
        }
    }

    static int[][] matchMailOrPhone(String value, boolean smsStyle) {
        if (value == null) {
            return null;
        }
        synchronized (LOCK) {
            List<int[]> matches = new ArrayList<>();
            for (PhoneBookEntry entry : phoneBooks.values()) {
                if (smsStyle) {
                    for (int i = 0; i < entry.phoneNumbers.length; i++) {
                        if (entry.phoneNumbers[i].equals(value)) {
                            matches.add(new int[]{entry.id, i | 0x80000000});
                        }
                    }
                } else {
                    for (int i = 0; i < entry.mailAddresses.length; i++) {
                        if (entry.mailAddresses[i].equals(value)) {
                            matches.add(new int[]{entry.id, i});
                        }
                    }
                }
            }
            return matches.isEmpty() ? null : matches.toArray(int[][]::new);
        }
    }

    private static MessageReceived getReceivedMessage(int id) {
        MessageReceived message = receivedMessages.get(id);
        if (message == null) {
            throw new IllegalArgumentException("Message not found: " + id);
        }
        return message.copy();
    }

    private static MessageSent getSentMessage(int id, Map<Integer, MessageSent> source) {
        MessageSent message = source.get(id);
        if (message == null) {
            throw new IllegalArgumentException("Message not found: " + id);
        }
        return message.copy();
    }

    private static int validateMessageFolder(int type) {
        if (type != MailConstants.RECEIVED && type != MailConstants.SENT && type != MailConstants.UNSENT) {
            throw new IllegalArgumentException("type out of range: " + type);
        }
        return type;
    }

    private static void fireFolderChanged(int type) {
        MessageFolderListener listener = messageFolderListener;
        if (listener != null) {
            listener.folderChanged(type);
        }
    }

    private static int[] defaultProfileItems() {
        int[] items = new int[PROFILE_ITEM_MAX];
        for (int i = 0; i < PROFILE_ITEM_MAX; i++) {
            items[i] = i + 1;
        }
        return items;
    }

    static String ownerProfileData(int item) {
        String user = System.getProperty("user.name", "");
        return switch (item) {
            case OwnerProfile.NAME, OwnerProfile.FAMILY_NAME -> user;
            case OwnerProfile.GIVEN_NAME, OwnerProfile.KANA, OwnerProfile.FAMILY_NAME_KANA,
                    OwnerProfile.GIVEN_NAME_KANA, OwnerProfile.TELEPHONE_NUMBER_2,
                    OwnerProfile.EMAIL_ADDRESS_2, OwnerProfile.POSTAL_CODE, OwnerProfile.ADDRESS,
                    OwnerProfile.ADDRESS_REGION, OwnerProfile.ADDRESS_LOCALITY, OwnerProfile.ADDRESS_STREET,
                    OwnerProfile.ADDRESS_EXTENDED -> "";
            case OwnerProfile.TELEPHONE_NUMBER_1 -> opendoja.host.OpenDoJaLaunchArgs.get(opendoja.host.OpenDoJaLaunchArgs.OWNER_PHONE1);
            case OwnerProfile.EMAIL_ADDRESS_1 -> opendoja.host.OpenDoJaLaunchArgs.get(opendoja.host.OpenDoJaLaunchArgs.OWNER_EMAIL1);
            case OwnerProfile.BIRTH_DATE -> "00000000";
            case OwnerProfile.BIRTH_DATE_YEAR -> "0000";
            case OwnerProfile.BIRTH_DATE_MONTH, OwnerProfile.BIRTH_DATE_DAY -> "00";
            default -> null;
        };
    }

    private static int resolvePhoneBookGroup(int groupId, String groupName) throws StoreException {
        if (groupId >= 0) {
            if (!phoneBookGroups.containsKey(groupId)) {
                throw new StoreException(StoreException.NOT_FOUND, "Phone book group not found: " + groupId);
            }
            return groupId;
        }
        if (groupName == null) {
            return -1;
        }
        for (PhoneBookGroupEntry entry : phoneBookGroups.values()) {
            if (Objects.equals(entry.name, groupName)) {
                return entry.id;
            }
        }
        int id = phoneBookGroupIds.getAndIncrement();
        phoneBookGroups.put(id, new PhoneBookGroupEntry(id, groupName));
        return id;
    }

    private static String[] compactStrings(String[] values) {
        if (values == null || values.length == 0) {
            return new String[0];
        }
        List<String> compacted = new ArrayList<>(values.length);
        for (String value : values) {
            if (value != null && !value.isEmpty()) {
                compacted.add(value);
            }
        }
        return compacted.toArray(new String[0]);
    }

    private static void validateFolderType(int folderType) {
        if (folderType != DataBoxFolder.FOLDER_MY_PICTURE && folderType != DataBoxFolder.FOLDER_I_MOTION) {
            throw new IllegalArgumentException("folder out of range: " + folderType);
        }
    }

    private static int defaultFolderId(int folderType) {
        for (DataBoxFolderEntry entry : dataBoxFolders.values()) {
            if (entry.folderType == folderType) {
                return entry.id;
            }
        }
        return createDefaultFolder(folderType, "openDoJa");
    }

    private static int createDefaultFolder(int folderType, String name) {
        int id = dataBoxFolderIds.getAndIncrement();
        dataBoxFolders.put(id, new DataBoxFolderEntry(id, folderType, name));
        return id;
    }

    private static ApplicationEntry currentApplicationEntry() {
        String ownerKey = ownerKey();
        for (ApplicationEntry entry : applications.values()) {
            if (entry.ownerKey.equals(ownerKey)) {
                return entry;
            }
        }
        ApplicationEntry entry = new ApplicationEntry(applicationIds.getAndIncrement(), ownerKey);
        applications.put(entry.id, entry);
        return entry;
    }

    private static String ownerKey() {
        DoJaRuntime runtime = DoJaRuntime.current();
        if (runtime == null) {
            return "host";
        }
        String sourceUrl = runtime.sourceUrl();
        return sourceUrl == null || sourceUrl.isBlank()
                ? runtime.application().getClass().getName()
                : sourceUrl.trim();
    }

    private static String truncateIpid(String ipid) {
        if (ipid == null) {
            return null;
        }
        byte[] bytes = ipid.getBytes(DoJaEncoding.DEFAULT_CHARSET);
        if (bytes.length <= 8) {
            return ipid;
        }
        return new String(Arrays.copyOf(bytes, 8), DoJaEncoding.DEFAULT_CHARSET);
    }

    private static String hostPart(String url) {
        if (url == null) {
            return null;
        }
        try {
            return new URI(url).getHost();
        } catch (URISyntaxException exception) {
            return null;
        }
    }

    private static ImageEntry lastAccessibleImageEntry(LinkedHashMap<Integer, ImageEntry> source) {
        ImageEntry latest = null;
        for (ImageEntry entry : source.values()) {
            if (!entry.exclusive || entry.ownerKey.equals(ownerKey())) {
                latest = entry;
            }
        }
        return latest;
    }

    private static <K> K lastKey(LinkedHashMap<K, ?> map) {
        K key = null;
        for (K value : map.keySet()) {
            key = value;
        }
        return key;
    }

    private static ImageSnapshot snapshotImage(MediaImage mediaImage) {
        try {
            EncodedImage encoded = ImageEncoder.getEncoder("JPEG")
                    .encode(mediaImage.getImage(), 0, 0, mediaImage.getWidth(), mediaImage.getHeight());
            try (InputStream stream = encoded.getInputStream()) {
                return new ImageSnapshot(readAllBytes(stream));
            }
        } catch (IOException exception) {
            throw new UIException(UIException.UNSUPPORTED_FORMAT, exception.getMessage());
        }
    }

    private static byte[] readAllBytes(InputStream stream) throws IOException {
        return stream.readAllBytes();
    }

    private static LocationRecord copyLocationRecord(LocationRecord record) {
        return new LocationRecord(record.id, copyLocation(record.location));
    }

    private static Toruca copyToruca(Toruca source) {
        Toruca copy = new Toruca();
        copy.setVersion(source.getVersion());
        copy.setType(source.getType());
        copy.setURL(source.getURL());
        copy.setData1(source.getData1());
        copy.setData2(source.getData2());
        copy.setData3(source.getData3());
        copy.setCategory(source.getCategory());
        copy.setBody(source.getBody());
        String move = source.getProperty(MediaResource.X_DCM_MOVE);
        if (move != null) {
            copy.setProperty(MediaResource.X_DCM_MOVE, move);
        }
        copy.ipid = source.ipid;
        copy.colorId = source.colorId;
        copy.sortId = source.sortId;
        copy.redistributionId = source.redistributionId;
        copy.expirationDate = source.expirationDate == null ? null : new java.util.Date(source.expirationDate.getTime());
        return copy;
    }

    private static ScheduleDate copyScheduleDate(ScheduleDate date) {
        if (date == null) {
            return null;
        }
        ScheduleDate copy = new ScheduleDate(date.getType());
        copy.set(java.util.Calendar.YEAR, date.get(java.util.Calendar.YEAR));
        copy.set(java.util.Calendar.MONTH, date.get(java.util.Calendar.MONTH));
        copy.set(java.util.Calendar.DAY_OF_MONTH, date.get(java.util.Calendar.DAY_OF_MONTH));
        copy.set(java.util.Calendar.HOUR_OF_DAY, date.get(java.util.Calendar.HOUR_OF_DAY));
        copy.set(java.util.Calendar.MINUTE, date.get(java.util.Calendar.MINUTE));
        return copy;
    }

    private static void appendProperty(StringBuilder builder, String key, String value) {
        builder.append(key).append('=').append(value == null ? "" : value).append('\n');
    }

    private static String hex(byte[] data) {
        if (data == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder(data.length * 2);
        for (byte datum : data) {
            builder.append(String.format(Locale.ROOT, "%02x", datum & 0xFF));
        }
        return builder.toString();
    }

    private static byte[] fromHex(String hex) {
        if (hex == null || hex.isEmpty()) {
            return new byte[0];
        }
        if ((hex.length() & 1) != 0) {
            throw new IllegalArgumentException("Odd-length hex string");
        }
        byte[] result = new byte[hex.length() / 2];
        for (int i = 0; i < result.length; i++) {
            result[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return result;
    }

    private static String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }

    static final class ApplicationEntry {
        final int id;
        final String ownerKey;

        ApplicationEntry(int id, String ownerKey) {
            this.id = id;
            this.ownerKey = ownerKey;
        }
    }

    static final class BookmarkEntry {
        final int id;
        final String url;
        final String title;

        BookmarkEntry(int id, String url, String title) {
            this.id = id;
            this.url = url;
            this.title = title;
        }
    }

    static final class PhoneBookGroupEntry {
        final int id;
        final String name;

        PhoneBookGroupEntry(int id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    static final class PhoneBookEntry {
        final int id;
        final String singleName;
        final String[] nameParts;
        final String singleKana;
        final String[] kanaParts;
        final String[] phoneNumbers;
        final String[] mailAddresses;
        final int groupId;
        final Location location;

        PhoneBookEntry(int id, String singleName, String[] nameParts, String singleKana, String[] kanaParts,
                       String[] phoneNumbers, String[] mailAddresses, int groupId, Location location) {
            this.id = id;
            this.singleName = singleName;
            this.nameParts = nameParts;
            this.singleKana = singleKana;
            this.kanaParts = kanaParts;
            this.phoneNumbers = phoneNumbers;
            this.mailAddresses = mailAddresses;
            this.groupId = groupId;
            this.location = location;
        }
    }

    static final class DataBoxFolderEntry {
        final int id;
        final int folderType;
        final String name;

        DataBoxFolderEntry(int id, int folderType, String name) {
            this.id = id;
            this.folderType = folderType;
            this.name = name == null ? "" : name;
        }
    }

    static final class ImageSnapshot {
        final byte[] bytes;

        ImageSnapshot(byte[] bytes) {
            this.bytes = bytes == null ? new byte[0] : bytes.clone();
        }
    }

    static final class ImageEntry {
        final int id;
        final ImageSnapshot image;
        final String ownerKey;
        final boolean exclusive;
        final int folderId;

        ImageEntry(int id, ImageSnapshot image, String ownerKey, boolean exclusive, int folderId) {
            this.id = id;
            this.image = image;
            this.ownerKey = ownerKey;
            this.exclusive = exclusive;
            this.folderId = folderId;
        }
    }

    static final class ScheduleEntry {
        final String description;
        final ScheduleDate date;
        final boolean alarm;

        ScheduleEntry(String description, ScheduleDate date, boolean alarm) {
            this.description = description;
            this.date = date;
            this.alarm = alarm;
        }
    }

    static final class DtvScheduleEntry {
        final int type;
        final DTVScheduleParam param;

        DtvScheduleEntry(int type, DTVScheduleParam param) {
            this.type = type;
            this.param = param;
        }
    }
}
