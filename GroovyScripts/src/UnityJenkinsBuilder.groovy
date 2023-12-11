import settings.UnitySettings
import utils.JenkinsUtils
import utils.UnityProjectSettings

abstract class UnityJenkinsBuilder<TBuildSetting extends UnitySettings> {

    protected TBuildSetting settings
    protected def ws
    protected def env
    protected JenkinsUtils jenkinsUtils

    UnityJenkinsBuilder(def ws) {
        this.ws = ws
        this.env = this.ws.env
    }

    void loadResource() throws Exception {
        this.ws.echo "Load resource..."
        this.jenkinsUtils = new JenkinsUtils(this.ws).loadResource()
    }

    void setupParameters(List params) throws Exception {
        def listParams = [
                this.ws.string(name: 'PARAM_CHECKOUT_JENKINS_REVISION', defaultValue: '', description: 'Checkout to jenkins revision or branch, if empty will do nothing'),
                this.ws.booleanParam(name: 'PARAM_SHOULD_RESET_JENKINS_PARAMS', defaultValue: false, description: 'Should reset jenkins params'),
                this.ws.string(name: 'PARAM_BUILD_FILE_NAME', defaultValue: this.jenkinsUtils.defaultValues['build-settings']['build-file-name'], description: 'Build file name, this must be the unity project folder name (without prefix "Unity")'),
                this.ws.string(name: 'PARAM_BUILD_VERSION', defaultValue: this.jenkinsUtils.defaultValues['build-settings']['build-version'], description: 'Build version. Ex: 1.0.0'),
                this.ws.string(name: 'PARAM_UNITY_TOOL_NAME', defaultValue: this.jenkinsUtils.defaultValues['build-settings']['unity-tool-name'], description: 'Unity tool name'),
                this.ws.choice(name: 'PARAM_UNITY_SCRIPTING_BACKEND', choices: this.jenkinsUtils.defaultValues['build-settings']['unity-scripting-backend'], description: 'Unity scripting backend'),
                this.ws.string(name: 'PARAM_UNITY_SCRIPTING_DEFINE_SYMBOLS', defaultValue: this.jenkinsUtils.defaultValues['build-settings']['unity-scripting-define-symbols'], description: 'Unity scripting define symbols'),
                this.ws.string(name: 'PARAM_DISCORD_WEBHOOK_URL', defaultValue: this.jenkinsUtils.defaultValues['build-settings']['discord-webhook-url'], description: 'Discord webhook url'),
                this.ws.booleanParam(name: 'PARAM_SHOULD_BUILD_DEVELOPMENT', defaultValue: this.jenkinsUtils.defaultValues['build-settings']['should-build-development'], description: 'Should build development'),
                this.ws.booleanParam(name: 'PARAM_SHOULD_OPTIMIZE_BUILD_SIZE', defaultValue: this.jenkinsUtils.defaultValues['build-settings']['should-optimize-build-size'], description: 'Should optimize build size'),
                this.ws.booleanParam(name: 'PARAM_SHOULD_NOTIFY_TO_CHAT_CHANNEL', defaultValue: this.jenkinsUtils.defaultValues['build-settings']['should-notify-to-chat-channel'], description: 'Should notify to chat channel'),
        ]

        listParams.addAll(params)

        //noinspection GroovyAssignabilityCheck
        if (this.env.PARAM_SHOULD_RESET_JENKINS_PARAMS == 'false') {
            return
        }

        // reset parameters
        this.ws.properties([this.ws.parameters(listParams)])
        this.ws.error("Resetting Jenkins parameters...")
    }

    void importBuildSettings() throws Exception {
        this.ws.echo "Importing build settings..."

        String workingDir = this.ws.pwd()

        this.settings = this.settings ?: [] as TBuildSetting

        this.settings.jobName = this.env.JOB_NAME
        this.settings.buildName = this.env.PARAM_BUILD_FILE_NAME
        this.settings.buildNumber = this.getBuildNumber()
        this.settings.buildVersion = this.env.PARAM_BUILD_VERSION
        this.settings.scriptingBackend = this.env.PARAM_UNITY_SCRIPTING_BACKEND
        this.settings.unityScriptingDefineSymbols = this.env.PARAM_UNITY_SCRIPTING_DEFINE_SYMBOLS
        this.settings.unityIdEmail = this.ws.credentials('jenkins-id-for-unity-email')
        this.settings.unityIdPassword = this.ws.credentials('jenkins-id-for-unity-password')
        this.settings.unityIdLicense = this.ws.credentials('jenkins-id-for-unity-license')
        this.settings.uploadDomain = this.env.PARAM_BUILD_DOWNLOAD_URL
        this.settings.discordWebhookUrl = this.env.PARAM_DISCORD_WEBHOOK_URL
        this.settings.rootPathAbsolute = workingDir
        this.settings.unityEditorName = this.env.PARAM_UNITY_TOOL_NAME
        this.settings.unityProjectPathAbsolute = this.jenkinsUtils.combinePath(workingDir, "Unity${env.PARAM_BUILD_FILE_NAME}")
        this.settings.unityBinaryPathAbsolute = this.ws.tool(name: env.PARAM_UNITY_TOOL_NAME)
        this.settings.isBuildDevelopment = this.env.PARAM_SHOULD_BUILD_DEVELOPMENT == 'true'
        this.settings.isOptimizeBuildSize = this.env.PARAM_SHOULD_OPTIMIZE_BUILD_SIZE == 'true'
        this.settings.isNotifyToChatChannel = this.env.PARAM_SHOULD_NOTIFY_TO_CHAT_CHANNEL == 'true'

        if (this.settings.uploadDomain == null || this.settings.uploadDomain.isBlank()) {
            this.settings.uploadDomain = this.jenkinsUtils.defaultValues['s3-settings']['domain']
        }

        if (this.settings.buildName.isBlank()) {
            this.ws.error('Missing param: PARAM_BUILD_FILE_NAME')
        }

        if (this.settings.buildNumber.isBlank()) {
            this.ws.error('Missing param: BUILD_NUMBER')
        }

        if (this.settings.buildVersion.isBlank()) {
            this.ws.error('Missing param: PARAM_BUILD_VERSION')
        }

        if (this.settings.unityScriptingDefineSymbols.isBlank()) {
            this.ws.error('Missing param: PARAM_UNITY_SCRIPTING_DEFINE_SYMBOLS')
        }
    }

    void clean() throws Exception {
        this.ws.echo "---- Start cleaning ----"

        this.jenkinsUtils.runCommand("git clean -fd")
        this.jenkinsUtils.runCommand("git add .")
        this.jenkinsUtils.runCommand("git reset --hard")
        this.jenkinsUtils.runCommand("git submodule foreach --recursive git clean -fd")
        this.jenkinsUtils.runCommand("git submodule foreach --recursive git reset --hard")

        try {
            if (this.ws.isUnix()) {
                this.ws.sh "rm -rf ./Build/"
            } else {
                this.ws.powershell "Remove-Item -Recurse -Force .\\Build\\"
            }
        } catch (Exception ignored) {
        }


        this.jenkinsUtils.runCommand("git submodule update --init")
        this.jenkinsUtils.runCommand("git submodule update")

        // set build version and other info
        if (this.ws.isUnix()) {
            this.ws.sh "JenkinsScripts/BashScripts/set-game-version.sh '${this.settings.buildVersion}' '${this.settings.buildNumber}' '${env.GIT_COMMIT}' '${this.settings.buildName}'"
        } else {
            this.ws.powershell "JenkinsScripts\\Scripts\\SetGameVersion.ps1 -BuildVersion ${this.settings.buildVersion} -BuildNumber ${this.settings.buildNumber} -CommitHash ${env.GIT_COMMIT} -ProjectName ${this.settings.buildName}"
        }

        this.ws.echo "---- End cleaning ----"

        String revision = this.env.PARAM_CHECKOUT_JENKINS_REVISION
        if (revision && !revision.isEmpty()) {
            String jenkinsPath = 'JenkinsScripts'

            this.ws.echo "Checking out jenkins to ${revision}..."

            this.ws.dir(jenkinsPath) {
                this.jenkinsUtils.runCommand("git fetch")
                this.jenkinsUtils.runCommand("git reset --hard ${revision}")
            }

            this.jenkinsUtils.runCommand("git add ${jenkinsPath}")
            this.jenkinsUtils.runCommand("git commit -m \"Checkout JenkinsScripts to ${revision}\"")
            this.jenkinsUtils.runCommand("git push -f origin HEAD:checkout-new-jenkins-submodule")

            this.ws.echo "Pushing new jenkins submodule..."
            this.ws.error("Check out JenkinsScripts to ${revision} successfully! Please merge branch 'checkout-new-jenkins-submodule' into this branch then build again!")
        }
    }

    void cleanOnError() throws Exception {
        this.ws.echo "---- Start cleaning on error ----"

        this.jenkinsUtils.runCommand('git clean -fd')
        this.jenkinsUtils.runCommand('git reset --hard')
        this.jenkinsUtils.runCommand('git submodule foreach --recursive git reset --hard')

        if (this.settings.buildName) {
            if (this.ws.isUnix()) {
                this.ws.sh "rm -rf ./Unity${this.settings.buildName}/Library/Bee/"
            } else {
                this.ws.powershell "Remove-Item -Recurse -Force .\\Unity${this.settings.buildName}\\Library\\Bee\\"
            }
        }

        this.ws.echo "---- End cleaning on error ----"
    }

    abstract void build() throws Exception

    abstract void uploadBuild() throws Exception

    abstract void notifyToChatChannel() throws Exception

    String getLogPath(boolean absolute = true, Closure closure) {
        String relative = this.jenkinsUtils.combinePath('Build', 'Logs', closure(this) as String)

        if (absolute) {
            return this.jenkinsUtils.combinePath(this.settings.rootPathAbsolute, relative)
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

    String getBuildPathRelative(Closure closure) {
        return this.jenkinsUtils.combinePath('Build', 'Client', this.settings.platform, closure(this) as String)
    }

    String[] getDefineSymbols() {
        return this.settings.unityScriptingDefineSymbols.split(';')
    }

    void setupScriptDefineSymbols() {
        def filePath = this.jenkinsUtils.combinePath(
                this.settings.unityProjectPathAbsolute,
                'ProjectSettings',
                'ProjectSettings.asset'
        )

        def projectSettings = new UnityProjectSettings(this.ws.readFile(filePath) as String)
                .setScriptDefineSymbols(
                        UnityProjectSettings.PlatformType.Standalone,
                        this.settings.unityScriptingDefineSymbols
                )
                .setScriptDefineSymbols(
                        UnityProjectSettings.PlatformType.Android,
                        this.settings.unityScriptingDefineSymbols
                )
                .setScriptDefineSymbols(
                        UnityProjectSettings.PlatformType.WebGL,
                        this.settings.unityScriptingDefineSymbols
                )
                .setScriptDefineSymbols(
                        UnityProjectSettings.PlatformType.iPhone,
                        this.settings.unityScriptingDefineSymbols
                )


        this.ws.writeFile file: filePath, text: projectSettings.exportFileContent()
    }

    String getBuildNumber() {
        var result = this.env.BUILD_NUMBER

        var branchTokens = (this.env.GIT_BRANCH as String).split("/")
        var releaseIndex = branchTokens.findIndexOf { (it == "release") }

        if (releaseIndex >= 0 && releaseIndex + 1 >= branchTokens.size()) {
            return branchTokens[releaseIndex + 1]
        }

        return result
    }
}