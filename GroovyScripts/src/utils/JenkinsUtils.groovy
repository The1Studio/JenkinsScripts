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

    float fileSizeInMB(String path) {
        float result;

        if (this.ws.isUnix()) {
            result = Long.parseLong(this.ws.sh(returnStdout: true, script: "du -shk '$path' | awk '{print \$1}'").trim() as String) / (1024.0)
        }
        else {
            result = Long.parseLong(this.ws.powershell(returnStdout: true, script: "@((gci $path -r | measure Length -s).Sum, (Get-Item $path).Length)[(Test-Path $path -Type leaf)]").trim() as String) / (1024.0 * 1024.0)
        }

        return result.round(2)
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

    String getRawCurrentBuildResult() {
        return this.ws.currentBuild.currentResult.toString()
    }

    BuildResults getCurrentBuildResult() {
        try {
            switch (this.ws.currentBuild.currentResult.toString()) {
                case 'SUCCESS':
                    return BuildResults.SUCCESS
                case 'FAILURE':
                    return BuildResults.FAILURE
                case 'ABORTED':
                    return BuildResults.ABORTED
                case 'UNSTABLE':
                    return BuildResults.UNSTABLE
                case 'NOT_BUILT':
                    return BuildResults.NOT_BUILT
                default:
                    throw new Exception("Unknown build result: ${this.ws.currentBuild.currentResult}")
            }
        } catch (Exception ignored) {
            this.ws.echo ignored.message
            return BuildResults.UNKNOWN
        }
    }

    String combinePath(String... paths) {
        return paths.join(this.ws.isUnix() ? '/' : '\\')
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
