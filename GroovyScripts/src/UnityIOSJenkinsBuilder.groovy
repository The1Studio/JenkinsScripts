import settings.UnityAndroidSettings
import settings.UnityIOSSettings
import utils.UnityProjectSettings

class UnityIOSJenkinsBuilder extends UnityJenkinsBuilder<UnityIOSSettings> {

    protected String uploadIpaUrl
    protected String uploadArchiveUrl

    protected long buildSizeIpa
    protected long buildSizeArchive

    UnityIOSJenkinsBuilder(Object workflowScript) {
        super(workflowScript)
    }

    @Override
    void setupParameters(List params) throws Exception {
        params.addAll([
                this.ws.string(name: 'PARAM_SIGNING_TEAM_ID', defaultValue: this.jenkinsUtils.defaultValues["build-settings"]["ios"]["signing-key-id"], description: 'Signing Team ID'),
        ])

        super.setupParameters(params)
    }

    @Override
    void importBuildSettings() throws Exception {
        this.settings = new UnityIOSSettings()

        super.importBuildSettings()

        this.settings.platform = 'ios'
        this.settings.signingTeamId = env.PARAM_SIGNING_TEAM_ID
    }

    @Override
    void build() throws Exception {
        this.setupScriptDefineSymbols {}

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
                this.ws.sh 'pod install --silent --allow-root'
            }

            // Archive and export ipa
            this.ws.sh "xcodebuild -quiet -scheme Unity-iPhone -configuration Release -sdk iphoneos -workspace Unity-iPhone.xcworkspace archive -archivePath ${this.settings.buildName}.xcarchive"
            this.ws.sh "xcodebuild -quiet -exportArchive -archivePath ${this.settings.buildName}.xcarchive -exportOptionsPlist info.plist -exportPath ${this.settings.buildName}.ipa"

            // Check if we have ipa
            if (!this.ws.fileExists("${this.settings.buildName}.ipa")) {
                this.ws.error("No executable found after build")
            }
        }
    }

    @Override
    void uploadBuild() throws Exception {
        def buildName = this.settings.buildName
        def uploadUrl = this.getUploadUrl { buildName }

        this.ws.dir(this.getBuildPathRelative { '' }) {
            String archiveDirectory = "${buildName}.xcarchive"
            String ipaDirectory = "${buildName}.ipa"

            String archiveZipFile = "${buildName}-${this.settings.buildVersion}-${this.settings.buildNumber}.xcarchive.zip"
            String ipaZipFile = "${buildName}-${this.settings.buildVersion}-${this.settings.buildNumber}.ipa.zip"

            this.ws.sh "zip -6 -q -r '${archiveZipFile}' '${archiveDirectory}'"
            this.ws.sh "zip -6 -q -r '${ipaZipFile}' '${ipaDirectory}'"

            this.buildSizeArchive = this.jenkinsUtils.fileSizeInMB(archiveZipFile)
            this.buildSizeIpa = this.jenkinsUtils.fileSizeInMB(ipaZipFile)

            this.ws.echo "Archive build size: ${this.buildSizeArchive} MB"
            this.ws.echo "IPA build size: ${this.buildSizeIpa} MB"

            this.jenkinsUtils.uploadToS3(archiveZipFile, "$uploadUrl/${archiveZipFile}")
            this.jenkinsUtils.uploadToS3(ipaZipFile, "$uploadUrl/${ipaZipFile}")

            this.uploadArchiveUrl = "${this.settings.uploadDomain}/$uploadUrl/${archiveZipFile}"
            this.uploadIpaUrl = "${this.settings.uploadDomain}/$uploadUrl/${ipaZipFile}"

            this.ws.echo "Archive build url: ${this.uploadArchiveUrl}"
            this.ws.echo "IPA build url: ${this.uploadIpaUrl}"
        }
    }

    @Override
    void notifyToChatChannel() throws Exception {
        if (!this.settings.isNotifyToChatChannel) {
            return
        }

        String message = "__version: ${this.settings.buildVersion} - number: ${this.settings.buildNumber}__ was built failed!!!"

        if (this.jenkinsUtils.isCurrentBuildSuccess()) {
            message = """
                __version: ${this.settings.buildVersion} - number: ${this.settings.buildNumber}__ was built successfully !!!__
                ${this.settings.platform} (${this.settings.jobName}) Build 
                IPA: ${this.uploadIpaUrl} - ${this.buildSizeIpa} MB
                XCArchive: ${this.uploadArchiveUrl} - ${this.buildSizeArchive} MB
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
    String getBuildPathRelative(Closure closure) {
        return this.jenkinsUtils.combinePath('Build', 'Client', this.settings.platform, this.settings.buildName, closure(this) as String)
    }

    @Override
    void setupScriptDefineSymbols(Closure closure) {
        super.setupScriptDefineSymbols({
            UnityProjectSettings it ->
                it.setScriptDefineSymbols(
                        UnityProjectSettings.PlatformType.iPhone,
                        this.settings.unityScriptingDefineSymbols,
                )
                return closure(it)
        })
    }
}