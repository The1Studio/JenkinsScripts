def call() {
    def jenkinsBuilder = new UnityIOSJenkinsBuilder(this)

    pipeline {
        agent { label "macos" }

        stages {
            stage('Create Builder') {
                steps {
                    script {
                        jenkinsBuilder.loadResource()
                        jenkinsBuilder.setupParameters([])
                        jenkinsBuilder.importBuildSettings()
                    }
                }
            }

            stage("Clean and sync") {
                options { timeout(time: 1, unit: 'HOURS') }

                steps {
                    script {
                        jenkinsBuilder.clean()
                    }
                }
            }

            stage("Build Client (IOS)") {
                options { timeout(time: 2, unit: 'HOURS') }

                steps {
                    script {
                        jenkinsBuilder.build()
                    }
                }

                post {
                    failure {
                        script {
                            jenkinsBuilder.cleanOnError()
                        }
                    }

                    always {
                        script {
                            String logPath = jenkinsBuilder.getLogPath(false) { "Build-Client.${jenkinsBuilder.settings.platform}.log" }
                            String reportPath = jenkinsBuilder.getLogPath(false) { "Build-Client-Report.${jenkinsBuilder.settings.platform}.log" }
                            String podInstallPath = jenkinsBuilder.getLogPath(false) { "Pod-Install-Client.${jenkinsBuilder.settings.platform}.log" }
                            String archivePath = jenkinsBuilder.getLogPath(false) { "Archive-Client.${jenkinsBuilder.settings.platform}.log" }
                            String exportPath = jenkinsBuilder.getLogPath(false) { "Export-Client.${jenkinsBuilder.settings.platform}.log" }

                            if (fileExists(logPath)) {
                                archiveArtifacts artifacts: logPath, allowEmptyArchive: true
                            }

                            if (fileExists(reportPath)) {
                                archiveArtifacts artifacts: reportPath, allowEmptyArchive: true
                            }

                            if (fileExists(podInstallPath)) {
                                archiveArtifacts artifacts: podInstallPath, allowEmptyArchive: true
                            }

                            if (fileExists(archivePath)) {
                                archiveArtifacts artifacts: archivePath, allowEmptyArchive: true
                            }

                            if (fileExists(exportPath)) {
                                archiveArtifacts artifacts: exportPath, allowEmptyArchive: true
                            }
                        }
                    }
                }
            }

            stage("Upload Build") {
                options { timeout(time: 75, unit: 'MINUTES') }

                steps {
                    script {
                        jenkinsBuilder.uploadBuild()
                    }
                }
            }
        }

        post {
            always {
                script {
                    jenkinsBuilder.notifyToChatChannel()
                }
            }
        }
    }
}

