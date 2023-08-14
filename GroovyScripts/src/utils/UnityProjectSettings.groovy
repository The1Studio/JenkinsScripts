package utils

class UnityProjectSettings {

    static class PlatformType {
        public static final String Android = "Android";
        public static final String EmbeddedLinux = "EmbeddedLinux";
        public static final String GameCoreScarlett = "GameCoreScarlett";
        public static final String GameCoreXboxOne = "GameCoreXboxOne";
        public static final String LinuxHeadlessSimulation = "LinuxHeadlessSimulation";
        public static final String Lumin = "Lumin";
        public static final String NintendoSwitch = "Nintendo Switch";
        public static final String PS4 = "PS4";
        public static final String PS5 = "PS5";
        public static final String Server = "Server";
        public static final String Stadia = "Stadia";
        public static final String Standalone = "Standalone";
        public static final String WebGL = "WebGL";
        public static final String WindowsStoreApps = "Windows Store Apps";
        public static final String XboxOne = "XboxOne";
        public static final String iPhone = "iPhone";
        public static final String tvOS = "tvOS";
    }

    private String fileContent;

    UnityProjectSettings(String fileContent) {
        this.fileContent = fileContent;
    }

    UnityProjectSettings setScriptDefineSymbols(String platform, String scriptDefineSymbols) {
        def matcher = this.fileContent =~ /scriptingDefineSymbols:[\w\s;:]*(?:\s)($platform: [\w\s;]*?)\n/
        matcher.find()

        this.fileContent = matcher.replaceAll {
            it.group(0).replaceAll(it.group(1), "${platform}: ${scriptDefineSymbols}")
        }

        return this
    }

    String exportFileContent() {
        return this.fileContent
    }
}
