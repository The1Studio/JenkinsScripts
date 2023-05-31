import utils.JenkinsUtils

abstract class UnityJenkinsBuilder<TThis extends UnityJenkinsBuilder, TBuildSetting> {

    protected TBuildSetting settings
    protected def ws
    protected JenkinsUtils jenkinsUtils

    UnityJenkinsBuilder(def ws) {
        this.ws = ws
    }

    TThis loadResource() throws Exception {
        this.jenkinsUtils = new JenkinsUtils(this.ws).loadResource()
        return this as TThis
    }

    TThis importBuildSettings(def buildSetting) throws Exception {
        this.ws.echo "Importing build settings..."
        if (buildSetting instanceof HashMap) {
            for (def pair : buildSetting) {
                this.ws.echo "${pair.key} = ${pair.value}"
            }
        }

        this.settings = buildSetting as TBuildSetting
        return this as TThis
    }

    abstract void build() throws Exception

    abstract void uploadBuild() throws Exception

    abstract void notifyToChatChannel() throws Exception

    abstract void notifyToGithub() throws Exception

    String getLogPath(Closure closure) {
        return "${this.settings.rootPathAbsolute}/Build/Logs/${closure(this)}"
    }

    String getUploadUrl(Closure closure, boolean stripDomain = true) {
        String url = "jobs/${this.settings.jobName}/${this.settings.buildNumber}/Build/Client/${settings.platform}/${closure(this)}"

        if (stripDomain) {
            return url
        }

        return "${this.settings.uploadDomain}/${url}"
    }
}