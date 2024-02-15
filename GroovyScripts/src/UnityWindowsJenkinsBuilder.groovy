import settings.UnityWindowsSettings

class UnityWindowsJenkinsBuilder extends UnityJenkinsBuilder<UnityWindowsSettings> {

    private def uploadUrl
    private def buildSize

    UnityWindowsJenkinsBuilder(Object workflowScript) {
        super(workflowScript)
    }

    @Override
    void setupParameters(List params) throws Exception {
        super.setupParameters(params)
    }

    @Override
    void importBuildSettings() throws Exception {
        this.settings = new UnityWindowsSettings()

        super.importBuildSettings()

        this.settings.platform = 'windows'

        this.settings.unityProjectPathAbsolute = this.ws.pwd()
    }

    @Override
    void clean() throws Exception {
    }

    @Override
    void cleanBuildReport() {
    }

    @Override
    void cleanOnError() throws Exception {
    }

    @Override
    void build() throws Exception {
        this.setupScriptDefineSymbols()

        String outputPath = this.getBuildPathRelative { "Unity.exe" }

        // Run Unity build
        this.ws.dir(this.settings.unityBinaryPathAbsolute) {
            String buildCommand = [this.ws.isUnix() ? "./Unity" : ".\\Unity.exe",
                                   " -batchmode -quit -executeMethod Editor.Build.BuildPipelineHost.Build",
                                   "-buildTarget StandaloneWindows",
                                   "-platforms ${this.settings.platform}",
                                   "-scriptingBackend ${this.settings.scriptingBackend}",
                                   "-projectPath \"${this.settings.unityProjectPathAbsolute}\"",
                                   "-logPath \"${this.getLogPath { "Build-Client.${this.settings.platform}.log" }}\"",
                                   "-outputPath \"$outputPath\"",
                                   "-scriptingDefineSymbols \"${this.settings.unityScriptingDefineSymbols}\"",].join(' ')

            if (this.settings.isBuildDevelopment) {
                buildCommand += ' -development'
            }

            if (this.settings.isOptimizeBuildSize) {
                buildCommand += ' -optimizeSize'
            }

            this.jenkinsUtils.runCommand(buildCommand)
        }

        if (!this.ws.fileExists(outputPath)) {
            this.ws.error("No exe found after build")
        }
    }

    @Override
    void uploadBuild() throws Exception {
        def buildName = "${this.settings.buildName}-${this.settings.buildVersion}-${this.settings.buildNumber}"
        def uploadUrl = this.getUploadUrl { buildName }

        this.ws.dir(this.getBuildPathRelative { '' }) {
            String zipFile = "${buildName}.zip"

            this.ws.zip dir: '', zipFile: zipFile

            this.buildSize = this.jenkinsUtils.fileSizeInMB(zipFile)
            this.jenkinsUtils.uploadToS3(zipFile, "$uploadUrl/${zipFile}")
            this.uploadUrl = "${this.settings.uploadDomain}/$uploadUrl/${zipFile}"
            this.ws.echo "Build url: ${this.uploadUrl} - ${this.buildSize}MB"
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
                Build: ${this.uploadUrl} - ${this.buildSize}MB
                -----------------------------------------------------------
                Define Symbols: \n```\n${this.getDefineSymbols().join('\n')}\n```
                Unity editor: ${this.settings.unityEditorName}
            """.stripMargin()
        }

        this.ws.discordSend(
                description: message,
                enableArtifactsList: true,
                footer: "------TheOneStudio-------",
                link: this.ws.env.BUILD_URL,
                result: this.ws.currentBuild.currentResult,
                showChangeset: true,
                thumbnail: this.settings.discordThumbnailPath,
                title: "${this.settings.jobName} - ${this.settings.buildNumber}",
                webhookURL: this.settings.discordWebhookUrl
        )
    }
}