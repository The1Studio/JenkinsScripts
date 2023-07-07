package utils

import java.util.concurrent.TimeUnit

class JenkinsUtils {
    def ws
    def defaultValues

    JenkinsUtils(def ws) {
        this.ws = ws
    }

    JenkinsUtils loadResource() {
        this.defaultValues = this.ws.readJSON(text: this.ws.libraryResource('default-values.json'))
        return this
    }

    void uploadToS3(String file, String path) {
        def s3Settings = this.defaultValues['s3-settings']

        this.ws.withAWS(region: s3Settings['region'], credentials: s3Settings['credentials']) {
            this.ws.s3Upload(file: file, bucket: s3Settings['bucket'], path: path)
        }
    }

    long fileSizeInMB(String path) {
        if (this.ws.isUnix()) {
            return Long.parseLong(this.ws.sh(returnStdout: true, script: "du -sh -m $path | awk '{print \$1}'").trim() as String)
        }
        return Long.parseLong(this.ws.powershell(returnStdout: true, script: "Write-Output((Get-Item $path).length)").trim() as String) / (1024 * 1024)
    }

    def runCommand(String script, boolean returnStdout = false, String encoding = 'UTF-8', String label = '', boolean returnStatus = false) {
        if (this.ws.isUnix()) {
            return this.ws.sh(script: script, encoding: encoding, label: label, returnStatus: returnStatus, returnStdout: returnStdout)
        }
        return this.ws.bat(script: script, encoding: encoding, label: label, returnStatus: returnStatus, returnStdout: returnStdout)
    }

    void replaceInFile(String file, String regex, Closure closure) {
        String content = this.ws.readFile(file: file)
        this.ws.echo("Before: $content")
        content = content.replaceAll(regex, closure)
        this.ws.echo("After: $content")
        this.ws.writeFile(file: file, text: content)
    }

    void replaceWithJenkinsVariables(String file, HashMap<String, String> variables = [], boolean useEnv = true) {
        this.replaceInFile(file, /\$\{JENKINS_(.+?)\}/) {
            String match = it[0]
            String group = it[1]
            return variables[group] ?: useEnv ? this.ws.env[group] : match
        }
    }

    BuildResults getCurrentBuildResult() {
        try {
            this.ws.echo "Current build result: ${this.ws.currentBuild.result}"
            this.ws.echo "Current build current result: ${this.ws.currentBuild.currentResult}"
            return BuildResults.valueOf(this.ws.currentBuild.currentResult.toString())
        } catch (Exception ignored) {
            this.ws.echo "Failed to get current build result: ${ignored.message}"
            return BuildResults.UNKNOWN
        }
    }

    String combinePath(String... paths) {
        return paths.join(this.ws.isUnix() ? '/' : '\\' )
    }

    Boolean isCurrentBuildSuccess() {
        return this.getCurrentBuildResult() == BuildResults.SUCCESS
    }

    enum BuildResults {
        SUCCESS,
        FAILURE,
        ABORTED,
        UNSTABLE,
        NOT_BUILT,
        UNKNOWN
    }
}
