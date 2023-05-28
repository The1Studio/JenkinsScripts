static void uploadToS3(def ws, String file, String path) {
    ws.withAWS(region: 'ap-southeast-1', credentials: 'the1-s3-credential') {
        ws.s3Upload(file: file, bucket: 'the1studio-builds', path: path)
    }
}

static long findSizeInMB(def ws, String path) {
    return fileSize(ws, path) / (1024 * 1024)
}

static long fileSize(def ws, String path) {
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

    for (file in ws.findFiles(glob: "${path}/*.*")) {
        if (!file.isDirectory()) {
            bytes += file.length
        }
    }

    return bytes
}

return this