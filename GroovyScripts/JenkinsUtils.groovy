String toJenkinsBoolean(String value) {
    return value == 'true' ? 'true' : ''
}

String ifNullOrEmpty(String value, String defaultValue) {
    return value ?: defaultValue
}

String getEscapeString(String stringValue) {
    return stringValue.replace('"', '\\"')
}

void powershellx(String command) {
    String formatedCommand = getEscapeString(command)
    bat "powershell -ExecutionPolicy Bypass -NoLogo -NonInteractive -Command ${formatedCommand}"
}

Boolean hasBuildingTargetBranch() {
    return env.ghprbTargetBranch as Boolean
}

String getDestinationBranch(String overrideBranch = '') {
    return overrideBranch ?: env.ghprbTargetBranch ?: env.GIT_BRANCH
}

String getSourceBranch() {
    return env.ghprbSourceBranch ?: env.GIT_BRANCH
}

Boolean shouldNotifyGithub() {
    return env.ghprbTargetBranch as Boolean
}

Boolean shouldNotifyToChatChannel() {
    return env.ghprbTargetBranch as Boolean
}

Boolean shouldUploadBuild() {
    return env.ghprbTargetBranch as Boolean
}

String getScriptingBackend() {
    return env.ghprbTargetBranch ? 'mono' : 'il2cpp'
}

String getChatChannelName() {
    switch (env.GIT_BRANCH)
    {
        case 'origin/release':     return 'release'
        case 'origin/develop':     return 'develop'
        case 'origin/staging':     return 'staging'
        case 'origin/playtest':    return 'playtest'
        default:                   return 'build'
    }
}

String getGameVersion(String version) {
    switch (env.GIT_BRANCH)
    {
        case 'origin/release':     return env.BUILD_VERSION
        case 'origin/develop':     return "${version}-develop-${env.BUILD_NUMBER}"
        case 'origin/staging':     return "${version}-staging-${env.BUILD_NUMBER}"
        default:                   return "${version}.${env.BUILD_NUMBER}"
    }
}

String shouldBuildPlatform(String platform) {
    def map = [
        'origin/release':      ['android': true,  'ios': false, 'webgl': false],
        'origin/develop':      ['android': true,  'ios': false, 'webgl': false],
        'origin/staging':      ['android': true,  'ios': false, 'webgl': false],
        // PRs
        'pr':                  ['android': true,  'ios': false, 'webgl': false],
        // Custom
        'custom':              ['android': true,  'ios': false, 'webgl': false],
        // unknown branches
        '<unknown>':           ['android': true,  'ios': false, 'webgl': false],
    ]

    def branch = env.GIT_BRANCH
    if (env.ghprbTargetBranch != null) {
        branch = 'pr'
    }

    def branchSettings = map[branch]

    if (branchSettings == null) {
        println "Unknown branch ${branch} when determining platforms to build, building everything."
        branchSettings = map['<unknown>']
    }

    return branchSettings[platform] ? 'true' : ''
}

String stripPrefix(String str, String prefix) {
    if (str.startsWith(prefix)) {
        return str.substring(prefix.length())
    }
    return str
}

String getPlatformList() {
    def list = []

    if (env.BUILD_ANDROID)  { list.add('android') }
    if (env.BUILD_IOS)      { list.add('ios') }
    if (env.BUILD_WEBGL)    { list.add('webgl') }

    return list
}

String getOutput(String platform) {
    switch (platform)
    {
        case 'android': return env.SHOULD_BUILD_APP_BUNDLE ? "${env.BUILD_FILE_NAME}_${env.BUILD_VERSION}.aab" : "${env.BUILD_FILE_NAME}_${env.BUILD_VERSION}.apk"
        case 'ios':     return "${env.BUILD_FILE_NAME}_${env.BUILD_VERSION}"
        case 'webgl':   return "${env.BUILD_FILE_NAME}"
        default:        return 'default'
    }
}

String getDownloadURL(String platform, String output) {
    switch (platform)
    {
        case 'webgl':   return "${output}/index.html"
        default:        return output
    }
}

Boolean shouldBuildDevelopment() {
    if (env.CLIENT_PLATFORM == 'webgl') return false
    return env.SHOULD_BUILD_DEVELOPMENT
}

void notifyClientBuildResultToGithub() {
    if (env.NOTIFY_GITHUB) {
        bat 'echo Failed to build client > message.txt'
        bat '-----Client-Report log---- >> message.txt'
        powershellx "JenkinsScripts\\Scripts\\Util_AnalyseUnityLog.ps1 -LogFile \".\\Build\\Logs\\Build-Client-Report.${CLIENT_PLATFORM}.log\" >> message.txt"
        bat '-----Client log---- >> message.txt'
        powershellx "JenkinsScripts\\Scripts\\Util_AnalyseUnityLog.ps1 -LogFile \".\\Build\\Logs\\Build-Client.${CLIENT_PLATFORM}.log\" >> message.txt"
        PostStatusGithub('message.txt')
    }
}

void postStatusGithub(LogFile) {
    commit = env.ghprbActualCommit ?: env.GIT_COMMIT
    bat "echo. >> ${LogFile}"
    bat "echo Jenkins build for '${JOB_NAME}' Build: ${BUILD_ID} Commit: ${commit} >> ${LogFile}"

    // Need to escape % for bat
    url = env.BUILD_URL.replace('%', '%%')
    bat "echo To view console log: ${url}consoleFull >> ${LogFile}"
    withCredentials([string(credentialsId: 'GitHub-PersonalAccessToken', variable: 'TOKEN')]) {
        bat "powershell -ExecutionPolicy Bypass -NoLogo -NonInteractive -NoProfile .\\JenkinsScripts\\Scripts\\UpdatePRBuildStatus.ps1 -Logfile '${LogFile}' -PullRequestId ${env.ghprbPullId} -Token ${TOKEN} -GitUrlPR ${GIT_URL_PR}"
    }
}

void uploadToS3(artifact, path) {
    withAWS(region:'ap-southeast-1', credentials:'the1-s3-credential') {
        s3Upload(file: "Build/${artifact}", bucket: 'the1studio-builds', path: path)
    }
}

long getBuildSize(artifact) {
    if (artifact.contains('.')) {
        size = fileSize("Build/${artifact}")
    } else {
        size = new BigDecimal(directorySize("Build/${artifact}"))
    }
    return (size / (1024 * 1024)).setScale(1, 0)
}

public fileSize(artifact) {
    long bytes = 0
    def files = findFiles(glob: "${artifact}")
    for (file in files) {
        if (!file.isDirectory()) {
            bytes += file.length
        }
    }
    return bytes
}

public directorySize(directory) {
    def size = powershell returnStdout: true, script: "(gci $directory -recurse | measure Length -s).sum"
    echo "$size"
    return size.trim()
}

String getKeyStoreFileName() {
    if (!env.PUBLISHER_NAME) return KEYSTORE_FILE_NAME
    return "${PUBLISHER_NAME}_$KEYSTORE_FILE_NAME"
}

void exportApkFromAAB(executableFile) {
    def apksFile = executableFile.replace('.aab', '.apks')
    def apksPath = "${env.BUILD_LOCATION}${env.CLIENT_PLATFORM}\\${apksFile}"
    powershell script: "java -jar .\\JenkinsScripts\\bundletool-all.jar build-apks --bundle=${BUILD_LOCATION}${CLIENT_PLATFORM}\\${executableFile} --output=${apksPath} --ks=.\\Unity$BUILD_FILE_NAME\\${GetKeyStoreFileName()} --ks-pass=pass:\"$KEYSTORE_PASSWORD\" --ks-key-alias=theonestudio --key-pass=pass:\"$KEYSTORE_PASSWORD\" --mode=universal"

    //uncompress apks
    def zipPath = apksPath.replace('.apks', '.zip')
    fileOperations([fileRenameOperation(destination: zipPath, source: apksPath)])
    unzip zipFile: zipPath, dir: "${env.BUILD_LOCATION}${env.CLIENT_PLATFORM}\\"
    def apkPath = "${env.BUILD_LOCATION}${env.CLIENT_PLATFORM}\\universal.apk"
    def apkFile = executableFile.replace('.aab', '.apk')
    def newApkPath = "${env.BUILD_LOCATION}${env.CLIENT_PLATFORM}\\${apkFile}"
    fileOperations([fileRenameOperation(destination: newApkPath, source: apkPath)])
}

return this