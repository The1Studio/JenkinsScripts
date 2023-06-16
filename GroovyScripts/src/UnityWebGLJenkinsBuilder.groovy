import settings.UnityWebGLSettings
import utils.FacebookAppAPI

class UnityWebGLJenkinsBuilder extends UnityJenkinsBuilder<UnityWebGLJenkinsBuilder, UnityWebGLSettings> {

    private String accessUrl
    private String accessZipUrl

    private long folderSize
    private long zipSize
    private long wasmSize
    private long dataSize
    private long streamingAssetsSize

    UnityWebGLJenkinsBuilder(Object workflowScript) {
        super(workflowScript)
    }

    @Override
    UnityWebGLJenkinsBuilder importBuildSettings(Object buildSetting) throws Exception {
        super.importBuildSettings(buildSetting)

        if (this.settings.uploadDomain == null || this.settings.uploadDomain.isBlank()) {
            this.settings.uploadDomain = this.jenkinsUtils.defaultValues['s3-settings']['domain']
        }

        this.settings.platform = 'webgl'

        if (this.settings.isUploadToFacebook) {
            this.settings.isUploadToFacebook = this.settings.facebookAppId != null && !this.settings.facebookAppId.isBlank() && this.settings.facebookAppSecret != null && !this.settings.facebookAppSecret.isBlank()
        }

        // Replace settings before build
        this.replaceFacebookAppConfigJson()

        return this
    }

    @Override
    void build() throws Exception {
        // Run Unity build
        this.ws.dir(this.settings.unityBinaryPathAbsolute) {
            def setScriptingDefineSymbolsCommand = [this.ws.isUnix() ? "./Unity" : "./Unity.exe",
                                                    " -batchmode -quit -executeMethod Build.SetScriptingDefineSymbols",
                                                    "-platforms ${this.settings.platform}",
                                                    "-projectPath '${this.settings.unityProjectPathAbsolute}'",
                                                    "-logFile '${this.getLogPath { "Build-Client.${this.settings.platform}.log" }}'",
                                                    "-scriptingDefineSymbols '${this.settings.unityScriptingDefineSymbols}'",].join(' ')

            def buildCommand = [this.ws.isUnix() ? "./Unity" : "./Unity.exe",
                                " -batchmode -quit -executeMethod Build.BuildFromCommandLine",
                                "-platforms ${this.settings.platform}",
                                "-scriptingBackend ${this.settings.scriptingBackend}",
                                "-projectPath '${this.settings.unityProjectPathAbsolute}'",
                                "-logFile '${this.getLogPath { "Build-Client.${this.settings.platform}.log" }}'",
                                "-outputPath '${this.settings.buildName}'",
                                "-scriptingDefineSymbols '${this.settings.unityScriptingDefineSymbols}'",].join(' ')

            if (this.settings.isBuildDevelopment) {
                buildCommand += ' -development'
            }

            if (this.settings.isOptimizeBuildSize) {
                buildCommand += ' -optimizeSize'
            }

            this.jenkinsUtils.runCommand(setScriptingDefineSymbolsCommand)
            this.jenkinsUtils.runCommand(buildCommand)
        }

        if (!this.ws.fileExists(this.getBuildPathRelative { 'index.html' })) {
            this.ws.error("No executable found after build")
        }
    }

    @Override
    void uploadBuild() throws Exception {
        this.ws.dir(this.getBuildPathRelative { '' }) {
            String uploadUrl = this.getUploadUrl({ "${this.settings.buildName}" })
            this.accessUrl = "${this.settings.uploadDomain}/${uploadUrl}/index.html"

            this.jenkinsUtils.uploadToS3('', uploadUrl)
            this.folderSize = this.jenkinsUtils.fileSizeInMB('.')

            if (this.ws.fileExists("Build/${this.settings.buildName}.data")) {
                this.dataSize = this.jenkinsUtils.fileSizeInMB("Build/${this.settings.buildName}.data")
            }

            if (this.ws.fileExists("Build/${this.settings.buildName}.wasm")) {
                this.wasmSize = this.jenkinsUtils.fileSizeInMB("Build/${this.settings.buildName}.wasm")
            }

            if (this.ws.fileExists("StreamingAssets")) {
                this.streamingAssetsSize = this.jenkinsUtils.fileSizeInMB("StreamingAssets")
            }

            if (this.settings.isUploadToFacebook) {
                String zipFile = "${this.settings.buildName}-${this.settings.buildVersion}-${this.settings.buildNumber}.zip"
                String uploadZipUrl = this.getUploadUrl({ zipFile })
                this.accessZipUrl = "${this.settings.uploadDomain}/${uploadZipUrl}"

                this.ws.zip dir: '', zipFile: zipFile

                this.jenkinsUtils.uploadToS3(zipFile, uploadZipUrl)
                this.uploadBuildToFacebook(zipFile)

                this.zipSize = this.jenkinsUtils.fileSizeInMB(zipFile)
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
                Access URL: ${this.accessUrl} - ${this.folderSize}MB
                ${this.settings.isUploadToFacebook ? "Access Zip URL: ${this.accessZipUrl} - ${this.zipSize}MB" : "This build is not uploaded to Facebook"}
                -----------------------------------------------------------
                Data: ${this.dataSize ? "${this.dataSize}MB" : "N/A"}
                WASM: ${this.wasmSize ? "${this.wasmSize}MB" : "N/A"}
                Streaming Assets: ${this.streamingAssetsSize ? "${this.streamingAssetsSize}MB" : "N/A"}
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

    void uploadBuildToFacebook(String zipFile) throws Exception {
        String appId = this.settings.facebookAppId
        String appSecret = this.settings.facebookAppSecret

        if (appId == null || appId.isBlank() || appSecret == null || appSecret.isBlank()) {
            this.ws.echo "Facebook App ID or App Secret is not set"
            return
        }

        new FacebookAppAPI(this.jenkinsUtils, appId, appSecret)
                .loadAccessToken()
                .uploadBundleAssets(zipFile, "The One Studio WebGL Build#${this.settings.buildNumber} - Build Version ${this.settings.buildVersion}")
    }

    String getBuildPathRelative(Closure closure) {
        return "Build/Client/${this.settings.platform}/${this.settings.buildName}/${closure(this)}"
    }

    void replaceFacebookAppConfigJson() {
        def variables = ['ORIENTATION': this.settings.orientation,]

        for (def file : this.ws.findFiles(glob: "**/Assets/**/fbapp-config.json")) {
            this.jenkinsUtils.replaceWithJenkinsVariables(file.path as String, variables)
        }
    }
}