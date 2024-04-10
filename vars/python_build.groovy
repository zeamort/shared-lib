def call(dockerRepoName, service) {
    pipeline {
        agent any
        parameters {
            booleanParam(defaultValue: false, description: 'Deploy the App', name: 'DEPLOY')
        }
        stages {
            stage('Build') {
                steps {
                    dir(service) {
                        sh 'if [ -d ".venv" ]; then rm -Rf .venv; fi'
                        sh 'python3 -m venv .venv'
                        sh '. ./.venv/bin/activate'
                        sh 'pip install -r requirements.txt --break-system-packages' 
                        sh 'pip install --upgrade flask --break-system-packages'
                        sh 'pip install safety --break-system-packages'
                    }
                }
            }
            stage('Python Lint') {
                steps {
                    dir(service) {
                        sh 'pylint --fail-under 1 *.py'
                    }
                }
            }
            stage('Security Check'){
                steps {
                    dir(service) {
                        sh script: '''
                        . ./.venv/bin/activate
                        export PATH=$PATH:~/.local/bin
                        safety check -r requirements.txt --full-report > safety_report.txt
                        ''', returnStdout: false
                        archiveArtifacts artifacts: 'safety_report.txt', onlyIfSuccessful: false
                    }
                }
            }
            stage('Package') {
                when {
                    expression { env.GIT_BRANCH == 'origin/main' }
                }
                steps {
                    dir(service) {
                        withCredentials([string(credentialsId: 'DockerHubMorteza', variable: 'TOKEN')]) {
                            sh "docker login -u 'zeamort' -p '$TOKEN' docker.io"
                            sh "docker build -t zeamort/${dockerRepoName}:latest ."
                            sh "docker push zeamort/${dockerRepoName}:latest"
                        }
                    }
                }
            }
            stage('Deliver') {
                when {
                    expression { params.DEPLOY }
                }
                steps {
                    sshagent(['Morteza3855VM']) { 
                        sh """
                        ssh -o StrictHostKeyChecking=no ubuntu@ec2-52-40-150-21.us-west-2.compute.amazonaws.com '
                            docker images | grep "${dockerRepoName}" | grep -v "latest" | awk '{print $3}' | xargs -r docker rmi -f
                            cd ~/api-microservices-project/deployment && docker compose pull "${dockerRepoName}"
                            docker compose up -d --scale receiver=3 --force-recreate
                        '
                        """
                    }
                }
            }
        }
    }
}