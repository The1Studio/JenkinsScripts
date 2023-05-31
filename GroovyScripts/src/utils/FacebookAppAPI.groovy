package utils

class FacebookAppAPI {
    private JenkinsUtils jenkinsUtils

    private String accessToken
    private String appId
    private String appSecret

    FacebookAppAPI(JenkinsUtils jenkinsUtils, String appId, String appSecret) {
        this.jenkinsUtils = jenkinsUtils

        this.appId = appId
        this.appSecret = appSecret
    }

    FacebookAppAPI loadAccessToken() {
        this.accessToken = this.getAccessToken()
        return this
    }

    String getAccessToken(String appId, String appSecret) {
        String getAccessTokenCommand = "curl -X GET \"https://graph.facebook.com/oauth/access_token?client_id=${appId}&client_secret=${appSecret}&grant_type=client_credentials\""
        String accessJson = this.jenkinsUtils.runCommand(getAccessTokenCommand, true)
        accessJson = accessJson.trim().tokenize().last()

        def jsonObject = this.jenkinsUtils.ws.readJSON text: accessJson
        return jsonObject.access_token
    }

    String getAccessToken() {
        return getAccessToken(this.appId, this.appSecret)
    }

    void uploadBundleAssets(String appId, String accessToken, String zipFile, String comment = '') {
        String uploadBundleAssetsCommand = "curl -X POST https://graph-video.facebook.com/${appId}/assets -F \"access_token=${accessToken}\" -F \"type=BUNDLE\" -F \"asset=@${zipFile}\" -F \"comment=${comment}\""
        this.jenkinsUtils.runCommand(uploadBundleAssetsCommand)
    }

    void uploadBundleAssets(String zipFile, String comment = '') {
        this.uploadBundleAssets(this.appId, this.accessToken, zipFile, comment)
    }
}