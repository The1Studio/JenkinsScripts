import utils.JenkinsUtils

abstract class UnityJenkinsBuilder<TThis extends UnityJenkinsBuilder, TBuildSetting> {

    protected TBuildSetting settings
    protected def ws
    protected def env
    protected JenkinsUtils jenkinsUtils

    UnityJenkinsBuilder(def ws) {
        this.ws = ws
        this.env = this.ws.env
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

        if (this.settings.uploadDomain == null || this.settings.uploadDomain.isBlank()) {
            this.settings.uploadDomain = this.jenkinsUtils.defaultValues['s3-settings']['domain']
        }

        return this as TThis
    }

    void clean() throws Exception {
        this.ws.echo "---- Start cleaning ----"

        this.jenkinsUtils.runCommand("git clean -fd")
        this.jenkinsUtils.runCommand("git reset --hard")
        this.jenkinsUtils.runCommand("git submodule foreach --recursive git clean -fd")
        this.jenkinsUtils.runCommand("git submodule foreach --recursive git reset --hard")

        if (this.ws.isUnix()) {
            this.ws.sh "rm -rf ./Build/"
        } else {
            this.ws.powershell "Remove-Item -Recurse -Force .\\Build\\"
        }


        this.jenkinsUtils.runCommand("git submodule update --init")
        this.jenkinsUtils.runCommand("git submodule update")

        // set build version and other info
        if (this.ws.isUnix()) {
            this.ws.sh "JenkinsScripts/BashScripts/set-game-version.sh '${env.BUILD_VERSION}' '${env.BUILD_NUMBER}' '${env.GIT_COMMIT}' '${env.PARAM_BUILD_FILE_NAME}'"
        } else {
            this.ws.powershell "JenkinsScripts\\Scripts\\SetGameVersion.ps1 -BuildVersion ${env.BUILD_VERSION} -BuildNumber ${env.BUILD_NUMBER} -CommitHash ${env.GIT_COMMIT} -ProjectName ${BUILD_FILE_NAME}"
        }

        this.ws.echo "---- End cleaning ----"
    }

    abstract void build() throws Exception

    abstract void uploadBuild() throws Exception

    abstract void notifyToChatChannel() throws Exception

    abstract void notifyToGithub() throws Exception

    String getLogPath(boolean absolute = true, Closure closure) {
        String relative = "Build/Logs/${closure(this)}"

        if (absolute) {
            return "${this.settings.rootPathAbsolute}/${relative}"
        }

        return relative
    }

    String getUploadUrl(Closure closure, boolean stripDomain = true) {
        String url = "jobs/${this.settings.jobName}/${this.settings.buildNumber}/Build/Client/${settings.platform}/${closure(this)}"

        if (stripDomain) {
            return url
        }

        return "${this.settings.uploadDomain}/${url}"
    }
}