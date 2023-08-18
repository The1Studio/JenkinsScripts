import settings.UnityAndroidSettings

class UnityAndroidJenkinsBuilder extends UnityJenkinsBuilder<UnityAndroidSettings> {

    private float buildSizeApk
    private float buildSizeAab
    private float buildSizeSymbol
    private String uploadApkUrl
    private String uploadAabUrl
    private String uploadSymbolUrl

    UnityAndroidJenkinsBuilder(Object workflowScript) {
        super(workflowScript)
    }

    @Override
    void setupParameters(List params) throws Exception {
        params.addAll([
                this.ws.string(name: 'PARAM_KEYSTORE_NAME', defaultValue: this.jenkinsUtils.defaultValues["build-settings"]["android"]["keystore-name"], description: 'Keystore name'),
                this.ws.password(name: 'PARAM_KEYSTORE_PASSWORD', description: 'Keystore password'),
                this.ws.string(name: 'PARAM_KEYSTORE_ALIAS_NAME', defaultValue: this.jenkinsUtils.defaultValues["build-settings"]["android"]["keystore-alias-name"], description: 'Keystore alias name'),
                this.ws.password(name: 'PARAM_KEYSTORE_ALIAS_PASSWORD', description: 'Keystore alias password'),
                this.ws.booleanParam(name: 'PARAM_SHOULD_BUILD_APP_BUNDLE', defaultValue: this.jenkinsUtils.defaultValues["build-settings"]["android"]["should-build-app-bundle"], description: 'Should build app bundle'),
        ])

        super.setupParameters(params)
    }

    @Override
    void importBuildSettings() throws Exception {
        this.settings = new UnityAndroidSettings()

        super.importBuildSettings()

        this.settings.platform = 'android'
        this.settings.keystoreName = this.env.PARAM_KEYSTORE_NAME
        this.settings.keystorePass = this.env.PARAM_KEYSTORE_PASSWORD
        this.settings.keystoreAliasName = this.env.PARAM_KEYSTORE_ALIAS_NAME
        this.settings.keystoreAliasPass = this.env.PARAM_KEYSTORE_ALIAS_PASSWORD
        this.settings.isBuildAppBundle = this.env.PARAM_SHOULD_BUILD_APP_BUNDLE == 'true'
    }

    @Override
    void build() throws Exception {
        this.setupScriptDefineSymbols()

        String outputPath = "${this.settings.buildName}-${this.settings.buildVersion}-${this.settings.buildNumber}.${this.settings.isBuildAppBundle ? 'aab' : 'apk'}"
        String apkPath = "${this.settings.buildName}-${this.settings.buildVersion}-${this.settings.buildNumber}.apk"

        // Run Unity build
        this.ws.dir(this.settings.unityBinaryPathAbsolute) {
            String buildCommand = [this.ws.isUnix() ? "./Unity" : ".\\Unity.exe",
                                   " -batchmode -quit -executeMethod Build.BuildFromCommandLine",
                                   "-buildTarget Android",
                                   "-platforms ${this.settings.platform}",
                                   "-scriptingBackend ${this.settings.scriptingBackend}",
                                   "-projectPath \"${this.settings.unityProjectPathAbsolute}\"",
                                   "-logFile \"${this.getLogPath { "Build-Client.${this.settings.platform}.log" }}\"",
                                   "-outputPath \"$outputPath\"",
                                   "-scriptingDefineSymbols \"${this.settings.unityScriptingDefineSymbols}\"",].join(' ')

            if (this.settings.isBuildDevelopment) {
                buildCommand += ' -development'
            }

            if (this.settings.isOptimizeBuildSize) {
                buildCommand += ' -optimizeSize'
            }

            if (this.settings.isBuildAppBundle) {
                // Login to Unity ID to build app without splash screen
                this.ws.withEnv([
                        "UNITY_USERNAME=${this.settings.unityIdEmail}",
                        "UNITY_PASSWORD=${this.settings.unityIdPassword}",
                        "UNITY_SERIAL=${this.settings.unityIdLicense}"
                ]) {
                    if (this.ws.isUnix()) {
                        this.ws.sh([
                                "./Unity",
                                "-nographics -batchmode -quit",
                                "-username \$UNITY_USERNAME",
                                "-password \$UNITY_PASSWORD",
                                "-serial \$UNITY_SERIAL"
                        ].join(' '))
                    } else {
                        this.ws.powershell([
                                ".\\Unity.exe",
                                "-nographics -batchmode -quit",
                                "-username %UNITY_USERNAME%",
                                "-password %UNITY_PASSWORD%",
                                "-serial %UNITY_SERIAL%"
                        ].join(' '))
                    }
                }

                buildCommand += ' -buildAppBundle'
                buildCommand += " -keyStoreFileName \"${this.settings.keystoreName}\""
                buildCommand += " -keyStorePassword \"${this.settings.keystorePass}\""
                buildCommand += " -keyStoreAliasName \"${this.settings.keystoreAliasName}\""
                buildCommand += " -keyStoreAliasPassword \"${this.settings.keystoreAliasPass}\""
            }

            this.jenkinsUtils.runCommand(buildCommand)
        }

        // Extract apk from aab
        if (this.settings.isBuildAppBundle) {
            this.ws.echo "Extract apk from aab"
            this.extractApkFromAab(outputPath, apkPath)
        }

        if (!this.ws.fileExists(this.getBuildPathRelative { apkPath })) {
            this.ws.error("No apk found after build")
        }

        if (this.settings.isBuildAppBundle && !this.ws.fileExists(this.getBuildPathRelative { outputPath })) {
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
                //aab
                String aabFile = "${buildName}.aab"
                this.buildSizeAab = this.jenkinsUtils.fileSizeInMB(aabFile)
                this.jenkinsUtils.uploadToS3(aabFile, "$uploadUrl/${aabFile}")
                this.uploadAabUrl = "${this.settings.uploadDomain}/$uploadUrl/${aabFile}"
                this.ws.echo "Aab build url: ${this.uploadAabUrl} - ${this.buildSizeAab}MB"

                //symbol file
                String symbolFile = "${buildName}-${this.settings.buildVersion}-v${this.settings.buildNumber}-IL2CPP.symbols.zip"
                this.buildSizeSymbol = this.jenkinsUtils.fileSizeInMB(symbolFile)
                this.jenkinsUtils.uploadToS3(symbolFile, "$uploadUrl/${symbolFile}")
                this.uploadSymbolUrl = "${this.settings.uploadDomain}/$uploadUrl/${symbolFile}"
                this.ws.echo "Symbol build url: ${this.uploadSymbolUrl} - ${this.buildSizeSymbol}MB"
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
                ${this.settings.isBuildAppBundle ? "Symbol: ${this.uploadSymbolUrl} - ${this.buildSizeSymbol}MB" : "App bundle build is disabled"}
                -----------------------------------------------------------
                Define Symbols: \n```\n${this.getDefineSymbols().join('\n')}\n```
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

    void extractApkFromAab(String aabFile, String apkFile) {
        this.ws.dir(this.getBuildPathRelative { '' }) {
            String apksFile = aabFile.replace(".aab", ".apks")
            String keystorePath = this.jenkinsUtils.combinePath(this.settings.unityProjectPathAbsolute, this.settings.keystoreName)
            String bundleToolPath = this.jenkinsUtils.combinePath(this.settings.rootPathAbsolute, "JenkinsScripts", "bundletool-all.jar")

            this.jenkinsUtils.runCommand([
                    "java -jar \"$bundleToolPath\" build-apks",
                    "--bundle=$aabFile",
                    "--output=$apksFile",
                    "--ks=\"$keystorePath\"",
                    "--ks-pass=pass:\"${this.settings.keystorePass}\"",
                    "--ks-key-alias=${this.settings.keystoreAliasName}",
                    "--key-pass=pass:\"${this.settings.keystoreAliasPass}\"",
                    "--mode=universal"
            ].join(' '))

            // uncompress apks
            this.ws.unzip zipFile: apksFile, dir: "."
            this.ws.fileOperations([this.ws.fileRenameOperation(destination: apkFile, source: "universal.apk")])
        }
    }
}