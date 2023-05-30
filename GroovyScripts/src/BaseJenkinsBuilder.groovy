abstract class BaseJenkinsBuilder<TThis extends BaseJenkinsBuilder, TBuildSetting> {

    protected TBuildSetting settings
    protected def ws
    protected JenkinsUtils jenkinsUtils

    BaseJenkinsBuilder(def ws) {
        this.ws = ws
    }

    TThis loadResource() throws Exception {
        this.jenkinsUtils = new JenkinsUtils(this.ws).loadResource()
        return this as TThis
    }

    TThis importBuildSettings(def buildSetting) throws Exception {
        this.settings = buildSetting as TBuildSetting
        return this as TThis
    }

    abstract void build() throws Exception

    abstract void uploadBuild() throws Exception

    abstract void notifyToChatChannel() throws Exception

    abstract void notifyToGithub() throws Exception
}