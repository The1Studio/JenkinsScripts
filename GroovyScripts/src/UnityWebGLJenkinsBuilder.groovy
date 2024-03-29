import settings.UnityIOSSettings
import settings.UnityWebGLSettings
import utils.FacebookAppAPI
import utils.JenkinsUtils
import utils.UnityProjectSettings

class UnityWebGLJenkinsBuilder extends UnityJenkinsBuilder<UnityWebGLSettings> {

    private String accessUrl
    private String accessZipUrl

    private float folderSize
    private float zipSize
    private float wasmSize
    private float dataSize
    private float streamingAssetsSize

    UnityWebGLJenkinsBuilder(Object workflowScript) {
        super(workflowScript)
    }

    @Override
    void setupParameters(List params) throws Exception {
        params.addAll([
                this.ws.choice(choices: this.jenkinsUtils.defaultValues["build-settings"]["webgl"]["orientation"], name: 'PARAM_ORIENTATION'),
                this.ws.string(name: 'PARAM_FACEBOOK_APP_ID', defaultValue: this.jenkinsUtils.defaultValues["build-settings"]["webgl"]["facebook-app-id"], trim: true),
                this.ws.password(name: 'PARAM_FACEBOOK_APP_SECRET'),
                this.ws.booleanParam(name: 'PARAM_SHOULD_UPLOAD_TO_FACEBOOK', defaultValue: this.jenkinsUtils.defaultValues["build-settings"]["webgl"]["should-upload-to-facebook"])
        ])

        super.setupParameters(params)
    }

    @Override
    void importBuildSettings() throws Exception {
        this.settings = new UnityWebGLSettings()

        super.importBuildSettings()

        this.settings.platform = 'webgl'
        this.settings.scriptingBackend = 'il2cpp'
        this.settings.orientation = env.PARAM_ORIENTATION

        this.settings.facebookAppId = env.PARAM_FACEBOOK_APP_ID
        this.settings.facebookAppSecret = env.PARAM_FACEBOOK_APP_SECRET
        this.settings.isUploadToFacebook = env.PARAM_SHOULD_UPLOAD_TO_FACEBOOK == 'true'

        if (this.settings.isUploadToFacebook) {
            this.settings.isUploadToFacebook = this.settings.facebookAppId != null && !this.settings.facebookAppId.isBlank() && this.settings.facebookAppSecret != null && !this.settings.facebookAppSecret.isBlank()
        }

        if (this.settings.isUploadToFacebook) {
            if (this.settings.facebookAppId.isBlank()) {
                this.ws.error("Missing Param: PARAM_FACEBOOK_APP_ID")
            }

            if (this.settings.facebookAppSecret.isBlank()) {
                this.ws.error("Missing Param: PARAM_FACEBOOK_APP_SECRET")
            }
        }
    }

    @Override
    void build() throws Exception {
        this.setupScriptDefineSymbols()

        // Replace settings before build
        this.replaceFacebookAppConfigJson()

        // Run Unity build
        this.ws.dir(this.settings.unityBinaryPathAbsolute) {
//            def setScriptingDefineSymbolsCommand = [this.ws.isUnix() ? "./Unity" : ".\\Unity.exe",
//                                                    " -batchmode -quit -executeMethod Build.SetScriptingDefineSymbols",
//                                                    "-platforms ${this.settings.platform}",
//                                                    "-projectPath '${this.settings.unityProjectPathAbsolute}'",
//                                                    "-logFile '${this.getLogPath { "Build-Client.${this.settings.platform}.log" }}'",
//                                                    "-scriptingDefineSymbols '${this.settings.unityScriptingDefineSymbols}'",].join(' ')

            def buildCommand = [this.ws.isUnix() ? "./Unity" : ".\\Unity.exe",
                                " -batchmode -quit -executeMethod Build.BuildFromCommandLine",
                                "-buildTarget WebGL",
                                "-platforms ${this.settings.platform}",
                                "-scriptingBackend ${this.settings.scriptingBackend}",
                                "-projectPath \"${this.settings.unityProjectPathAbsolute}\"",
                                "-logFile \"${this.getLogPath { "Build-Client.${this.settings.platform}.log" }}\"",
                                "-outputPath \"${this.settings.buildName}\"",
                                "-scriptingDefineSymbols \"${this.settings.unityScriptingDefineSymbols}\"",].join(' ')

            if (this.settings.isBuildDevelopment) {
                buildCommand += ' -development'
            }

            if (this.settings.isOptimizeBuildSize) {
                buildCommand += ' -optimizeSize'
            }

//            this.jenkinsUtils.runCommand(setScriptingDefineSymbolsCommand)
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

            if (this.ws.fileExists("Build/${this.settings.buildName}.data.unityweb")) {
                this.dataSize = this.jenkinsUtils.fileSizeInMB("Build/${this.settings.buildName}.data.unityweb")
            }

            if (this.ws.fileExists("Build/${this.settings.buildName}.wasm.unityweb")) {
                this.wasmSize = this.jenkinsUtils.fileSizeInMB("Build/${this.settings.buildName}.wasm.unityweb")
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

        this.uploadBuildReport()
    }

    @Override
    void notifyToChatChannel() throws Exception {
        if (!this.settings.isNotifyToChatChannel) {
            return
        }

        String message = "__version: ${this.settings.buildVersion} - number: ${this.settings.buildNumber}__ - ${this.jenkinsUtils.getRawCurrentBuildResult()}!!!"

        if (this.jenkinsUtils.isCurrentBuildSuccess()) {
            message += """
                ${this.settings.platform} (${this.settings.jobName}) Build
                Access URL: ${this.accessUrl} - ${this.folderSize}MB
                ${this.settings.isUploadToFacebook ? "Access Zip URL: ${this.accessZipUrl} - ${this.zipSize}MB" : "This build is not uploaded to Facebook"}
                -----------------------------------------------------------
                Data: ${this.dataSize ? "${this.dataSize}MB" : "N/A"}
                WASM: ${this.wasmSize ? "${this.wasmSize}MB" : "N/A"}
                Streaming Assets: ${this.streamingAssetsSize ? "${this.streamingAssetsSize}MB" : "N/A"}
                -----------------------------------------------------------
                Define Symbols: \n```\n${this.getDefineSymbols().join('\n')}\n```
                Unity editor: ${this.settings.unityEditorName}
            """.stripMargin()
        }

        this.ws.discordSend(description: message,
                enableArtifactsList: true,
                footer: "------TheOneStudio-------",
                link: this.ws.env.BUILD_URL,
                result: this.ws.currentBuild.currentResult,
                showChangeset: true,
                thumbnail: this.settings.discordThumbnailPath,
                title: "${this.settings.jobName} - ${this.settings.buildNumber}",
                webhookURL: this.settings.discordWebhookUrl)
    }

    @Override
    String getBuildPathRelative(Closure closure) {
        return this.jenkinsUtils.combinePath('Build', 'Client', this.settings.platform, this.settings.buildName, closure(this) as String)
    }

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

    void replaceFacebookAppConfigJson() {

        for (def file : this.ws.findFiles(glob: "**/Assets/**/fbapp-config.json")) {
            this.jenkinsUtils.replaceWithJenkinsVariables(file.path as String)
        }
    }
}