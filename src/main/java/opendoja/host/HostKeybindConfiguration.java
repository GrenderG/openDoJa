package opendoja.host;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record HostKeybindConfiguration(List<HostKeybindProfile> profiles, List<String> profileNames, int activeProfileIndex) {
    public static final int MIN_PROFILES = 1;
    public static final int MAX_PROFILES = 8;
    public static final String DEFAULT_PROFILE_NAME = "Default";

    public HostKeybindConfiguration {
        ArrayList<HostKeybindProfile> normalizedProfiles = new ArrayList<>(MAX_PROFILES);
        if (profiles != null) {
            for (HostKeybindProfile profile : profiles) {
                if (profile == null) {
                    continue;
                }
                normalizedProfiles.add(profile);
                if (normalizedProfiles.size() >= MAX_PROFILES) {
                    break;
                }
            }
        }
        if (normalizedProfiles.isEmpty()) {
            normalizedProfiles.add(HostKeybindProfile.defaults());
        }
        profiles = List.copyOf(normalizedProfiles);
        profileNames = normalizeProfileNames(profileNames, profiles.size());
        activeProfileIndex = Math.clamp(activeProfileIndex, 0, profiles.size() - 1);
    }

    public static HostKeybindConfiguration defaults() {
        return new HostKeybindConfiguration(List.of(HostKeybindProfile.defaults()), List.of(DEFAULT_PROFILE_NAME), 0);
    }

    public HostKeybindProfile activeProfile() {
        return profiles.get(activeProfileIndex);
    }

    public boolean canAddProfile() {
        return profiles.size() < MAX_PROFILES;
    }

    public boolean canDeleteProfile(int profileIndex) {
        return profiles.size() > MIN_PROFILES && profileIndex > 0 && profileIndex < profiles.size();
    }

    public String profileLabel(int profileIndex) {
        return profileNames.get(profileIndex);
    }

    public HostKeybindConfiguration withActiveProfileIndex(int profileIndex) {
        return new HostKeybindConfiguration(profiles, profileNames, profileIndex);
    }

    public HostKeybindConfiguration withProfile(int profileIndex, HostKeybindProfile profile) {
        Objects.requireNonNull(profile, "profile");
        ArrayList<HostKeybindProfile> updated = new ArrayList<>(profiles);
        updated.set(profileIndex, profile);
        return new HostKeybindConfiguration(updated, profileNames, activeProfileIndex);
    }

    public HostKeybindConfiguration addProfile(String requestedName) {
        if (!canAddProfile()) {
            return this;
        }
        ArrayList<HostKeybindProfile> updated = new ArrayList<>(profiles);
        ArrayList<String> updatedNames = new ArrayList<>(profileNames);
        updated.add(HostKeybindProfile.defaults());
        updatedNames.add(normalizeProfileName(requestedName, updated.size() - 1));
        return new HostKeybindConfiguration(updated, updatedNames, updated.size() - 1);
    }

    public HostKeybindConfiguration deleteProfile(int profileIndex) {
        if (!canDeleteProfile(profileIndex)) {
            return this;
        }
        ArrayList<HostKeybindProfile> updated = new ArrayList<>(profiles);
        ArrayList<String> updatedNames = new ArrayList<>(profileNames);
        updated.remove(profileIndex);
        updatedNames.remove(profileIndex);
        int nextActiveProfileIndex = activeProfileIndex;
        if (profileIndex == activeProfileIndex) {
            nextActiveProfileIndex = 0;
        } else if (profileIndex < activeProfileIndex) {
            nextActiveProfileIndex--;
        }
        return new HostKeybindConfiguration(updated, updatedNames, nextActiveProfileIndex);
    }

    public static String normalizeProfileName(String candidate, int profileIndex) {
        if (profileIndex == 0) {
            return DEFAULT_PROFILE_NAME;
        }
        String normalized = candidate == null ? "" : candidate.trim();
        if (normalized.isEmpty()) {
            return "Profile " + (profileIndex + 1);
        }
        return normalized;
    }

    private static List<String> normalizeProfileNames(List<String> profileNames, int profileCount) {
        ArrayList<String> normalized = new ArrayList<>(profileCount);
        for (int index = 0; index < profileCount; index++) {
            String requestedName = profileNames != null && index < profileNames.size() ? profileNames.get(index) : null;
            normalized.add(normalizeProfileName(requestedName, index));
        }
        return List.copyOf(normalized);
    }
}
