package opendoja.launcher;

public final class LauncherUpdateServiceProbe {
    private LauncherUpdateServiceProbe() {
    }

    public static void main(String[] args) throws Exception {
        verifyNewerReleaseIsDetected();
        verifySameStableReleaseIsNotReportedAsNew();
        verifyVersionPrefixesAndMissingSegmentsCompareCorrectly();
        verifyJsonParsingExtractsTagName();

        System.out.println("Launcher update service probe OK");
    }

    private static void verifyNewerReleaseIsDetected() throws Exception {
        LauncherUpdateService service = new LauncherUpdateService(
                () -> new LauncherUpdateService.ReleaseInfo("v0.1.3"),
                OpenDoJaLauncher.LATEST_RELEASE_URL);

        LauncherUpdateService.UpdateCheckResult result = service.checkForUpdates("0.1.2");
        check(result.updateAvailable(), "newer release should be reported");
        check("v0.1.3".equals(result.latestVersion()), "latest version should come from GitHub tag_name");
        check(OpenDoJaLauncher.LATEST_RELEASE_URL.equals(result.latestReleaseUrl()), "download URL should use the shared constant");
    }

    private static void verifySameStableReleaseIsNotReportedAsNew() throws Exception {
        LauncherUpdateService service = new LauncherUpdateService(
                () -> new LauncherUpdateService.ReleaseInfo("0.1.2"),
                OpenDoJaLauncher.LATEST_RELEASE_URL);

        LauncherUpdateService.UpdateCheckResult result = service.checkForUpdates("0.1.2");
        check(!result.updateAvailable(), "same stable release should not be reported as new");
    }

    private static void verifyVersionPrefixesAndMissingSegmentsCompareCorrectly() {
        check(LauncherUpdateService.compareVersions("v0.1.3", "0.1.2") > 0,
                "v-prefixed tags should compare numerically");
        check(LauncherUpdateService.compareVersions("0.1.2", "0.1.2.0") == 0,
                "trailing zero segments should not matter");
        check(LauncherUpdateService.compareVersions("release-1.0", "0.9.9") > 0,
                "embedded numeric versions should still compare");
    }

    private static void verifyJsonParsingExtractsTagName() throws Exception {
        LauncherUpdateService.ReleaseInfo release = LauncherUpdateService.parseLatestReleaseResponse("""
                {
                  "tag_name": "v1.2.3",
                  "html_url": "https://github.com/GrenderG/openDoJa/releases/tag/v1.2.3"
                }
                """);
        check("v1.2.3".equals(release.version()), "GitHub response parser should read tag_name");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
