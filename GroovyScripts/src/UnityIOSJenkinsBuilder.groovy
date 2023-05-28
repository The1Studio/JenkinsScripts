import settings.UnityIOSSettings

class UnityIOSJenkinsBuilder extends BaseJenkinsBuilder<UnityIOSJenkinsBuilder, UnityIOSSettings> {

    protected String uploadIpaUrl
    protected String uploadArchiveUrl

    protected long buildSizeIpa
    protected long buildSizeArchive

    UnityIOSJenkinsBuilder(Object workflowScript) {
        super(workflowScript)
    }

    @Override
    void build() throws Exception {
        // Run Unity build
        this.ws.dir(this.settings.unityBinaryPathAbsolute) {
            def command = ["./Unity -batchmode -quit -executeMethod Build.BuildFromCommandLine",
                           "-platforms ${this.settings.platform}",
                           "-scriptingBackend ${this.settings.scriptingBackend}",
                           "-projectPath '${this.settings.unityProjectPathAbsolute}'",
                           "-logFile '${this.getLogPath { "Build-Client.${this.settings.platform}.log" }}'",
                           "-iosSigningTeamId '${this.settings.signingTeamId}'",
                           "-outputPath '${this.settings.buildName}'",
                           "-scriptingDefineSymbols '${this.settings.unityScriptingDefineSymbols}'",].join(' ')

            if (this.settings.isBuildDevelopment) {
                command += ' -development'
            }

            if (this.settings.isOptimizeBuildSize) {
                command += ' -optimizeSize'
            }

            this.ws.sh command
        }

        this.ws.dir(this.getBuildPathRelative { '' }) {
            // If we have Podfile, and we don't have xcworkspace, we need to run pod install
            if (this.ws.fileExists('Podfile') && !this.ws.fileExists('Unity-iPhone.xcworkspace')) {
                this.ws.sh 'pod install --allow-root'
            }

            // Archive and export ipa
            this.ws.sh "xcodebuild -scheme Unity-iPhone -configuration Release -sdk iphoneos -workspace Unity-iPhone.xcworkspace archive -archivePath ${this.settings.buildName}.xcarchive"
            this.ws.sh "xcodebuild -exportArchive -archivePath ${this.settings.buildName}.xcarchive -exportOptionsPlist info.plist -exportPath ${this.settings.buildName}.ipa"

            // Check if we have ipa
            if (!this.ws.fileExists("${this.settings.buildName}.ipa")) {
                this.ws.error("No executable found after build")
            }
        }
    }

    @Override
    void uploadBuild() throws Exception {
        if (!this.settings.isUploadBuild) {
            return
        }

        def buildName = this.settings.buildName
        def uploadUrl = this.getUploadUrl { buildName }

        this.ws.dir(this.getBuildPathRelative { '' }) {
            String archiveDirectory = "${buildName}.xcarchive"
            String ipaDirectory = "${buildName}.ipa"

            String archiveZipFile = "${buildName}-${this.settings.buildNumber}.ipa.zip"
            String ipaZipFile = "${buildName}-${this.settings.buildNumber}.xcarchive.zip"

            this.ws.sh "zip -6 -q -r '${archiveZipFile}' '${archiveDirectory}'"
            this.ws.sh "zip -6 -q -r '${ipaZipFile}' '${ipaDirectory}'"

            this.buildSizeArchive = JenkinsUtils.findSizeInMB(this.ws, "${archiveZipFile}")
            this.buildSizeIpa = JenkinsUtils.findSizeInMB(this.ws, "${ipaZipFile}")

            this.uploadArchiveUrl = "$uploadUrl/${archiveZipFile}"
            this.uploadIpaUrl = "$uploadUrl/${ipaZipFile}"

            JenkinsUtils.uploadToS3(this.ws, archiveZipFile, this.uploadArchiveUrl)
            JenkinsUtils.uploadToS3(this.ws, ipaZipFile, this.uploadIpaUrl)
        }
    }

    @Override
    void notifyToChatChannel() throws Exception {
        if (!this.settings.isNotifyToChatChannel) {
            return
        }

        String message = "__version: ${this.settings.buildVersion} - number: ${this.settings.buildNumber}__ was built failed!!!"

        if (this.ws.currentBuild.currentResult) {
            message = """
                __version: ${this.settings.buildVersion} - number: ${this.settings.buildNumber}__ was built successfully !!!__
                ${this.settings.platform} (${this.settings.jobName}) Build 
                IPA: ${this.uploadIpaUrl}.ipa.zip - ${this.buildSizeIpa} MB
                XCArchive: ${this.uploadArchiveUrl}.xcarchive.zip - ${this.buildSizeArchive} MB
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
    void notifyToGithub() throws Exception {
    }

    String getLogPath(Closure closure) {
        return "${this.settings.rootPathAbsolute}/Build/Logs/${closure(this)}"
    }

    String getBuildPathRelative(Closure closure) {
        return "Build/Client/ios/${this.settings.buildName}/${closure(this)}"
    }

    String getUploadUrl(Closure closure) {
        return "${this.settings.uploadUrl}/jobs/${this.settings.jobName}/${this.settings.buildNumber}/Build/Client/${this.settings.platform}/${closure(this)}"
    }
}