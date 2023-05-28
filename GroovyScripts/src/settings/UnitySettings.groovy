package settings

class UnitySettings {

    public String jobName
    public String buildName
    public String buildNumber
    public String platform
    public String scriptingBackend
    public String unityScriptingDefineSymbols

    public String unityIdEmail
    public String unityIdPassword
    public String unityIdLicense

    public String uploadUrl
    public String discordWebhookUrl

    public String rootPathAbsolute
    public String unityProjectPathAbsolute
    public String unityBinaryPathAbsolute

    public Boolean isBuildDevelopment
    public Boolean isOptimizeBuildSize
    public Boolean isUploadBuild
    public Boolean isNotifyToChatChannel

    String getLogPath(Closure closure) {
        return "${this.rootPathAbsolute}/Build/Logs/${closure(this)}"
    }

    String getBuildPathRelative(Closure closure) {
        return "Build/Client/ios/${this.buildName}/${closure(this)}"
    }

    String getUploadUrl(Closure closure) {
        return "${this.uploadUrl}/jobs/${this.jobName}/${this.buildNumber}/Build/Client/${this.platform}/${closure(this)}"
    }
}
