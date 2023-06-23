import settings.UnityAndroidSettings

class UnityAndroidJenkinsBuilder extends UnityJenkinsBuilder<UnityAndroidJenkinsBuilder, UnityAndroidSettings> {

    private long buildSizeApk
    private long buildSizeAab
    private String uploadApkUrl
    private String uploadAabUrl

    UnityAndroidJenkinsBuilder(Object workflowScript) {
        super(workflowScript)
    }

    @Override
    UnityAndroidJenkinsBuilder importBuildSettings(Object buildSetting) throws Exception {
        super.importBuildSettings(buildSetting)

        this.settings.platform = 'android'

        return this
    }

    @Override
    void build() throws Exception {
        // Run Unity build
        this.ws.dir(this.settings.unityBinaryPathAbsolute) {
            def buildCommand = [this.ws.isUnix() ? "./Unity" : ".\\Unity.exe",
                                " -batchmode -quit -executeMethod Build.BuildFromCommandLine",
                                "-platforms ${this.settings.platform}",
                                "-scriptingBackend ${this.settings.scriptingBackend}",
                                "-projectPath '${this.settings.unityProjectPathAbsolute}'",
                                "-logFile '${this.getLogPath { "Build-Client.${this.settings.platform}.log" }}'",
                                "-outputPath '${this.settings.buildName}-${this.settings.buildVersion}-${this.settings.buildNumber}'",
                                "-scriptingDefineSymbols '${this.settings.unityScriptingDefineSymbols}'",].join(' ')

            if (this.settings.isBuildDevelopment) {
                buildCommand += ' -development'
            }

            if (this.settings.isOptimizeBuildSize) {
                buildCommand += ' -optimizeSize'
            }

            if (this.settings.isBuildAppBundle) {
                this.jenkinsUtils.runCommand(
                        "${this.ws.isUnix() ? './Unity' : '.\\Unity.exe'} -nographics -batchmode -username %UNITY_ID_EMAIL% -password %UNITY_ID_PASSWORD% -serial %UNITY_ID_LICENSE% -quit"
                )
                buildCommand += ' -buildAppBundle'
                buildCommand += " -keyStoreFileName '${this.settings.keystoreName}'"
                buildCommand += " -keyStorePassword '${this.settings.keystorePass}'"
                buildCommand += " -keyStoreAliasName '${this.settings.keystoreAliasName}'"
                buildCommand += " -keyStoreAliasPassword '${this.settings.keystoreAliasPass}'"
            }

            this.jenkinsUtils.runCommand(buildCommand)
        }

        if (!this.ws.fileExists(this.getBuildPathRelative { "${this.settings.buildName}.apk" })) {
            this.ws.error("No apk found after build")
        }

        if (this.settings.isBuildAppBundle && !this.ws.fileExists(this.getBuildPathRelative { "${this.settings.buildName}.aab" })) {
            this.ws.error("No aab found after build")
        }
    }

    @Override
    void uploadBuild() throws Exception {
        def buildName = "${this.settings.buildName}-${this.settings.buildVersion}-${this.settings.buildNumber}"
        def uploadUrl = this.getUploadUrl { buildName }

        this.ws.dir(this.getBuildPathRelative { '' }) {
            String apkFile = "${buildName}.apk"

            this.buildSizeApk = this.jenkinsUtils.fileSizeInMB(apkFile)
            this.jenkinsUtils.uploadToS3(apkFile, "$uploadUrl/${apkFile}")
            this.uploadApkUrl = "${this.settings.uploadDomain}/$uploadUrl/${apkFile}"
            this.ws.echo "Apk build url: ${this.uploadApkUrl} - ${this.buildSizeApk}MB"

            if (this.settings.isBuildAppBundle) {
                String aabFile = "${buildName}.aab"
                this.buildSizeAab = this.jenkinsUtils.fileSizeInMB(aabFile)
                this.jenkinsUtils.uploadToS3(aabFile, "$uploadUrl/${aabFile}")
                this.uploadAabUrl = "${this.settings.uploadDomain}/$uploadUrl/${aabFile}"
                this.ws.echo "Aab build url: ${this.uploadAabUrl} - ${this.buildSizeAab}MB"
            }
        }
    }

    @Override
    void notifyToChatChannel() throws Exception {
        if (!this.settings.isNotifyToChatChannel) {
            return
        }

        String message = "__version: ${this.settings.buildVersion} - number: ${this.settings.buildNumber}__ was built failed!!!"

        if (this.jenkinsUtils.isCurrentBuildSuccess()) {
            message = """\
                __version: ${this.settings.buildVersion} - number: ${this.settings.buildNumber}__ was built successfully !!!__
                ${this.settings.platform} (${this.settings.jobName}) Build
                Apk: ${this.uploadApkUrl} - ${this.buildSizeApk}MB
                ${this.settings.isBuildAppBundle ? "Aab: ${this.uploadAabUrl} - ${this.buildSizeAab}MB" : "App bundle build is disabled"}
            """.stripMargin()
        }

        this.ws.discordSend(description: message,
                enableArtifactsList: true,
                footer: "------TheOneStudio-------",
                link: this.ws.env.BUILD_URL,
                result: this.ws.currentBuild.currentResult,
                showChangeset: true,
                thumbnail: 'https://user-images.githubusercontent.com/9598614/205434501-dc9d4c7a-caad-48de-8ec2-ca586f320f87.png',
                title: "${this.settings.jobName} - ${this.settings.buildNumber}",
                webhookURL: this.settings.discordWebhookUrl)
    }

    @Override
    void notifyToGithub() throws Exception {}

    String getBuildPathRelative(Closure closure) {
        return "Build/Client/${this.settings.platform}/${this.settings.buildName}/${closure(this)}"
    }
}