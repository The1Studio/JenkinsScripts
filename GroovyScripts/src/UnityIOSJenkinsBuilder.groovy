import com.android.io.FileWrapper
import settings.UnityIOSSettings
import utils.JenkinsUtils
import utils.ManifestPlistBuilder

class UnityIOSJenkinsBuilder extends UnityJenkinsBuilder<UnityIOSSettings> {

    protected String uploadPlistUrl
    protected String uploadIpaUrl
    protected String uploadArchiveUrl

    protected float buildSizeIpa
    protected float buildSizeArchive

    UnityIOSJenkinsBuilder(Object workflowScript) {
        super(workflowScript)
    }

    @Override
    void setupParameters(List params) throws Exception {
        params.addAll([
                this.ws.string(name: 'PARAM_SIGNING_TEAM_ID', defaultValue: this.jenkinsUtils.defaultValues["build-settings"]["ios"]["signing-key-id"], description: 'Signing Team ID\n-TheOne: 786EB2FXYT\n-Commando: G4GS5MQRN7'),
        ])

        super.setupParameters(params)
    }

    @Override
    void importBuildSettings() throws Exception {
        this.settings = new UnityIOSSettings()

        super.importBuildSettings()

        this.settings.platform = 'ios'
        this.settings.signingTeamId = env.PARAM_SIGNING_TEAM_ID

        if (this.settings.signingTeamId.isBlank()) {
            this.ws.error("Missing Param: PARAM_SIGNING_TEAM_ID")
        }
    }

    @Override
    void build() throws Exception {
        this.setupScriptDefineSymbols()

        // Run Unity build
        this.ws.dir(this.settings.unityBinaryPathAbsolute) {
            def command = ["./Unity -batchmode -quit -executeMethod Build.BuildFromCommandLine",
                           "-buildTarget iOS",
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
            def podInstallLogPath = '../../../Logs/Pod-Install-Client.ios.log'
            def archiveLogPath = '../../../Logs/Archive-Client.ios.log'
            def exportLogPath = '../../../Logs/Export-Client.ios.log'

            // If we have Podfile, and we don't have xcworkspace, we need to run pod install
            if (this.ws.fileExists('Podfile') && !this.ws.fileExists('Unity-iPhone.xcworkspace')) {
                this.ws.sh "pod install --silent --allow-root > $podInstallLogPath 2>&1"
            }

            // Archive and export ipa
            this.ws.sh "xcodebuild -quiet -scheme Unity-iPhone -configuration Release -sdk iphoneos -workspace Unity-iPhone.xcworkspace archive -archivePath ${this.settings.buildName}.xcarchive ENABLE_BITCODE=NO > $archiveLogPath 2>&1"
            this.ws.sh "xcodebuild -quiet -exportArchive -archivePath ${this.settings.buildName}.xcarchive -exportOptionsPlist info.plist -exportPath ${this.settings.buildName}.ipa > $exportLogPath 2>&1"

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
            String archiveZipFile = "${buildName}-${this.settings.buildVersion}-${this.settings.buildNumber}.xcarchive.zip"

            this.ws.sh "zip -6 -q -r '${archiveZipFile}' '${archiveDirectory}'"

            this.buildSizeArchive = this.jenkinsUtils.fileSizeInMB(archiveZipFile)

            this.ws.echo "Archive build size: ${this.buildSizeArchive} MB"

            this.jenkinsUtils.uploadToS3(archiveZipFile, "$uploadUrl/${archiveZipFile}")

            this.uploadArchiveUrl = "${this.settings.uploadDomain}/$uploadUrl/${archiveZipFile}"

            this.ws.dir("${buildName}.ipa") {
                String ipaFile = this.ws.findFiles(excludes: '', glob: '*.ipa')[0].name

                this.buildSizeIpa = this.jenkinsUtils.fileSizeInMB(ipaFile)
                this.ws.echo "IPA build size: ${this.buildSizeIpa} MB"

                this.jenkinsUtils.uploadToS3(ipaFile, "$uploadUrl/${ipaFile}")
                this.uploadIpaUrl = "${this.settings.uploadDomain}/$uploadUrl/${ipaFile}"

                String manifest = new ManifestPlistBuilder()
                        .setTitle(buildName)
                        .setIPA(this.uploadIpaUrl)
                        .setBundleId("com.car.climb.draw.bridge")
                        .setVersion(this.settings.buildVersion)
                        .build()

                this.ws.writeFile encoding: 'utf-8', file: 'manifest.plist', text: manifest
                this.jenkinsUtils.uploadToS3('manifest.plist', "$uploadUrl/manifest.plist")

                this.uploadPlistUrl = "${this.settings.uploadDomain}/$uploadUrl/manifest.plist"
            }

            this.ws.echo "Archive build url: ${this.uploadArchiveUrl}"
            this.ws.echo "IPA build url: ${this.uploadIpaUrl}"
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
                IPA: ${this.uploadIpaUrl} - ${this.buildSizeIpa} MB
                XCArchive: ${this.uploadArchiveUrl} - ${this.buildSizeArchive} MB
                -----------------------------------------------------------
                Define Symbols: \\n```\\n${this.getDefineSymbols().join('\\n')}\\n```
                Unity editor: ${this.settings.unityEditorName}
            """.stripMargin()

            this.ws.discordSend(
                    description: message,
                    enableArtifactsList: true,
                    footer: "------TheOneStudio-------",
                    link: this.ws.env.BUILD_URL,
                    result: this.ws.currentBuild.currentResult,
                    showChangeset: true,
                    thumbnail: this.settings.discordThumbnailPath,
                    title: "${this.settings.jobName} - ${this.settings.buildNumber}",
                    webhookURL: this.settings.discordWebhookUrl,
                    "image": "https://api.qrserver.com/v1/create-qr-code/?size=150x150&data=itms-services%3A%2F%2F%3Faction%3Ddownload-manifest%26url%3D${this.uploadPlistUrl}"
            )
            return;
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
                webhookURL: this.settings.discordWebhookUrl,
        )
    }

    @Override
    String getBuildPathRelative(Closure closure) {
        return this.jenkinsUtils.combinePath('Build', 'Client', this.settings.platform, this.settings.buildName, closure(this) as String)
    }
}