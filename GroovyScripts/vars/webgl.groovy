def call() {
    def jenkinsBuilder = new UnityWebGLJenkinsBuilder(this)

    pipeline {
        agent any

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

            stage("Build Client (WebGL)") {
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

                            if (fileExists(logPath)) {
                                archiveArtifacts artifacts: logPath, allowEmptyArchive: true
                            }

                            if (fileExists(reportPath)) {
                                archiveArtifacts artifacts: reportPath, allowEmptyArchive: true
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

