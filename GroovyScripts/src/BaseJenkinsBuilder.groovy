abstract class BaseJenkinsBuilder<TThis extends BaseJenkinsBuilder, TBuildSetting> {

    protected TBuildSetting settings

    TThis importBuildSettings(def buildSetting) throws Exception {
        this.settings = buildSetting as TBuildSetting
        return this as TThis
    }

    abstract void build() throws Exception

    abstract void uploadBuild() throws Exception

    abstract void notifyToChatChannel() throws Exception

    abstract void notifyToGithub() throws Exception
}