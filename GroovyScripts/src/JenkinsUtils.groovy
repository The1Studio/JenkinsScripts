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
            return Long.parseLong(this.ws.sh(returnStdout: true, script: "du -k $path | awk '{print \$1}'").trim() as String) / 1024
        }
        return Long.parseLong(this.ws.powershell(returnStdout: true, script: "Write-Host((Get-Item $path).length)").trim() as String) / (1024 * 1024)
    }
}
