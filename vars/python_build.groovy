def call(dockerRepoName, imageName, portNum, service) {
    pipeline {
        agent any
        parameters {
            booleanParam(defaultValue: false, description: 'Deploy the App', name: 'DEPLOY')
        }
        stages {
            stage('Build') {
                dir(service) {
                    steps {
                        sh "cd ${service}"
                        sh 'if [ -d ".venv" ]; then rm -Rf .venv; fi'
                        sh 'python3 -m venv .venv'
                        sh '. ./.venv/bin/activate'
                        sh 'pip install -r requirements.txt --break-system-packages'
                        sh 'pip install --upgrade flask --break-system-packages'
                    }
                }
            }
            stage('Python Lint') {
                dir(service) {
                    steps {
                        sh 'pylint --fail-under 5 *.py'
                    }
                }
            }
            stage('Security Check'){
                dir(service) {
                    steps {
                        sh 'echo hello'
                    }
                }
            }
            stage('Package') {
                dir(service) {
                    when {
                        expression { env.GIT_BRANCH == 'origin/main' }
                    }
                    steps {
                        withCredentials([string(credentialsId: 'DockerHubMorteza', variable: 'TOKEN')]) {
                            sh "docker login -u 'zeamort' -p '$TOKEN' docker.io"
                            sh "docker build -t ${dockerRepoName}:latest --tag zeamort/${dockerRepoName}:${imageName} ."
                            sh "docker push zeamort/${dockerRepoName}:${imageName}"
                        }
                    }
                }
            }
            stage('Deliver') {
                dir(service) {
                    when {
                        expression { params.DEPLOY }
                    }
                    steps {
                        sh "docker stop ${dockerRepoName} || true && docker rm ${dockerRepoName} || true"
                        sh "docker run -d -p ${portNum}:${portNum} --name ${dockerRepoName} ${dockerRepoName}:latest"
                    }
                }
            }
        }
    }
}