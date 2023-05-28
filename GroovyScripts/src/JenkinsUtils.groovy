static void uploadToS3(file, path) {
    withAWS(region: 'ap-southeast-1', credentials: 'the1-s3-credential') {
        s3Upload(file: file, bucket: 'the1studio-builds', path: path)
    }
}

static long findSizeInMB(String path) {
    return fileSize(path) / (1024 * 1024)
}

static long fileSize(String path) {
    long bytes = 0

    if (path == null || (path = path.trim()) == '') {
        return -1
    }

    path = path.replace('\\', '/')

    if (path.endsWith('/')) {
        path = path.substring(0, path.length() - 1)
    }

    def f = new File(path)
    if (f.isFile()) {
        return f.length()
    }

    for (file in findFiles(glob: "${path}/*.*")) {
        if (!file.isDirectory()) {
            bytes += file.length
        }
    }

    return bytes
}

static def InstantiateJenkinsBuilder(String platform) {
    switch (platform) {
        case 'android': return new Object()
        case 'ios': return new UnityIOSJenkinsBuilder()
        case 'web': return new Object()
        default: return null
    }
}

return this