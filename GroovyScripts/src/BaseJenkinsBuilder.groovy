abstract class BaseJenkinsBuilder<TBuildSetting> {

    protected TBuildSetting settings

    void importBuildSettings(def buildSetting) throws Exception {
        this.settings = buildSetting as TBuildSetting
    }

    abstract void build() throws Exception

    abstract void uploadBuild() throws Exception

    abstract void notifyToChatChannel() throws Exception

    abstract void notifyToGithub() throws Exception
}