import settings.UnityIOSSettings

class UnityIOSJenkinsBuilder extends BaseJenkinsBuilder<UnityIOSSettings> {

    @Override
    void build() throws Exception {
        // Run Unity build
        dir(this.settings.unityBinaryPathAbsolute) {
            def command =
                    """./Unity \\
                        -platforms ${this.settings.platform} \\ 
                        -scriptingBackend ${this.settings.scriptingBackend} \\ 
                        -quit \\
                        -batchmode \\ 
                        -projectPath '${this.settings.unityProjectPathAbsolute}' \\ 
                        -executeMethod Build.BuildFromCommandLine \\ 
                        -logFile '${this.settings.getLogPath { "Build-Client.${this.settings.platform}.log" }}' \\ 
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

            sh command
        }

        dir(this.settings.getBuildPathRelative { '' }) {
            // If we have Podfile, and we don't have xcworkspace, we need to run pod install
            if (fileExists('Podfile') && !fileExists('Unity-iPhone.xcworkspace')) {
                sh 'pod install --allow-root'
            }

            // Archive and export ipa
            sh "xcodebuild -scheme Unity-iPhone -configuration Release -sdk iphoneos -workspace Unity-iPhone.xcworkspace archive -archivePath ${this.settings.buildName}.xcarchive"
            sh "xcodebuild -exportArchive -archivePath ${this.settings.buildName}.xcarchive -exportOptionsPlist info.plist -exportPath ${this.settings.buildName}.ipa"

            // Check if we have ipa
            if (!fileExists("${this.settings.buildName}.ipa")) {
                error("No executable found after build")
            }
        }
    }

    @Override
    void uploadBuild() throws Exception {
        def buildName = this.settings.buildName
        def uploadUrl = this.settings.getUploadUrl { buildName }

        dir(this.settings.getBuildPathRelative { '' }) {
            def archiveDirectory = this.settings.getBuildPathRelative { "${buildName}.xcarchive" }
            def ipaDirectory = this.settings.getBuildPathRelative { "${buildName}.ipa" }

            sh "zip -6 -r '${buildName}.ipa.zip' '${buildName}.ipa'"
            sh "zip -6 -r '${buildName}.xcarchive.zip' '${buildName}.xcarchive'"

            def buildSizeArchive = JenkinsUtils.findSizeInMB("${archiveDirectory}.zip")
            def buildSizeIpa = JenkinsUtils.findSizeInMB("${ipaDirectory}.zip")

            JenkinsUtils.uploadToS3("${archiveDirectory}.zip", "$uploadUrl/${buildName}.xcarchive.zip")
            JenkinsUtils.uploadToS3("${ipaDirectory}.zip", "$uploadUrl/${buildName}.ipa.zip")
        }
    }

    @Override
    void notifyToChatChannel() throws Exception {

    }

    @Override
    void notifyToGithub() throws Exception {

    }
}