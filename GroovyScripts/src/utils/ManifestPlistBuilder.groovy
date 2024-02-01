package utils

class ManifestPlistBuilder {
    private def ipaUrl
    private def imgUrl = ""
    private def fullImgUrl = ""
    private def bundleId
    private def bundleVersion = "1.0.0"
    private def title = "Demo"

    ManifestPlistBuilder setIPA(String url) {
        this.ipaUrl = url
        return this
    }

    ManifestPlistBuilder setImage(String url) {
        this.imgUrl = url
        return this
    }

    ManifestPlistBuilder setFullImage(String url) {
        this.fullImgUrl = url
        return this
    }

    ManifestPlistBuilder setBundleId(String bundleId) {
        this.bundleId = bundleId
        return this
    }

    ManifestPlistBuilder setVersion(String version) {
        this.bundleVersion = version
        return this
    }

    ManifestPlistBuilder setTitle(String title) {
        this.title = title
        return this
    }

    String build() {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
            <plist version="1.0">
            <dict>
            \t<key>items</key>
            \t<array>
            \t\t<dict>
            \t\t\t<key>assets</key>
            \t\t\t<array>
            \t\t\t\t<dict>
            \t\t\t\t\t<key>kind</key>
            \t\t\t\t\t<string>software-package</string>
            \t\t\t\t\t<key>url</key>
            \t\t\t\t\t<string>${this.ipaUrl}</string>
            \t\t\t\t</dict>
            \t\t\t\t<dict>
            \t\t\t\t\t<key>kind</key>
            \t\t\t\t\t<string>display-image</string>
            \t\t\t\t\t<key>url</key>
            \t\t\t\t\t<string>${this.imgUrl}</string>
            \t\t\t\t</dict>
            \t\t\t\t<dict>
            \t\t\t\t\t<key>kind</key>
            \t\t\t\t\t<string>full-size-image</string>
            \t\t\t\t\t<key>url</key>
            \t\t\t\t\t<string>${this.fullImgUrl}</string>
            \t\t\t\t</dict>
            \t\t\t</array>
            \t\t\t<key>metadata</key>
            \t\t\t<dict>
            \t\t\t\t<key>bundle-identifier</key>
            \t\t\t\t<string>${this.bundleId}</string>
            \t\t\t\t<key>bundle-version</key>
            \t\t\t\t<string>${this.bundleVersion}</string>
            \t\t\t\t<key>kind</key>
            \t\t\t\t<string>software</string>
            \t\t\t\t<key>title</key>
            \t\t\t\t<string>${this.title}</string>
            \t\t\t</dict>
            \t\t</dict>
            \t</array>
            </dict>
            </plist>
        """
    }
}
