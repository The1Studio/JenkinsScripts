import settings.UnityIOSSettings

class UnityIOSJenkinsBuilder extends BaseJenkinsBuilder<UnityIOSJenkinsBuilder, UnityIOSSettings> {

    UnityIOSJenkinsBuilder(Object workflowScript) {
        super(workflowScript)
    }

    @Override
    void build() throws Exception {
        // Run Unity build
        this.ws.dir(this.settings.unityBinaryPathAbsolute) {
            def command =
                    """./Unity \\
                        -platforms ${this.settings.platform} \\ 
                        -scriptingBackend ${this.settings.scriptingBackend} \\ 
                        -quit \\
                        -batchmode \\ 
                        -projectPath '${this.settings.unityProjectPathAbsolute}' \\ 
                        -executeMethod Build.BuildFromCommandLine \\ 
                        -logFile '${this.getLogPath { "Build-Client.${this.settings.platform}.log" }}' \\ 
                        -iosSigningTeamId '${this.settings.signingTeamId}' \\
                        -outputPath '${this.settings.buildName}' \\
                        -scriptingDefineSymbols '${this.settings.unityScriptingDefineSymbols}' \\
                    """.stripMargin()

            if (this.settings.isBuildDevelopment) {
                command += '-development'
            }

            if (this.settings.isOptimizeBuildSize) {
                command += '-optimizeSize'
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
        def buildName = this.settings.buildName
        def uploadUrl = this.getUploadUrl { buildName }

        this.ws.dir(this.getBuildPathRelative { '' }) {
            def archiveDirectory = this.getBuildPathRelative { "${buildName}.xcarchive" }
            def ipaDirectory = this.getBuildPathRelative { "${buildName}.ipa" }

            this.ws.sh "zip -6 -r '${buildName}.ipa.zip' '${buildName}.ipa'"
            this.ws.sh "zip -6 -r '${buildName}.xcarchive.zip' '${buildName}.xcarchive'"

            def buildSizeArchive = JenkinsUtils.findSizeInMB(this.ws, "${archiveDirectory}.zip")
            def buildSizeIpa = JenkinsUtils.findSizeInMB(this.ws, "${ipaDirectory}.zip")

            JenkinsUtils.uploadToS3(this.ws, "${archiveDirectory}.zip", "$uploadUrl/${buildName}.xcarchive.zip")
            JenkinsUtils.uploadToS3(this.ws, "${ipaDirectory}.zip", "$uploadUrl/${buildName}.ipa.zip")
        }
    }

    @Override
    void notifyToChatChannel() throws Exception {

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