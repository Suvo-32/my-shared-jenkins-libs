@Library('my-shared-jenkins-libs') _
pipeline {
    agent { label 'Best-worker' }

    stages {

        stage('code clone') {
            steps {
                // git url: "git@github.com:Biswarup96/central-frontend.git", branch: "main"
                   clone(
                    "git@github.com:Biswarup96/central-frontend.git",
                    "main"
                )
            }
        }

        stage('checking git leaks') {
            steps {
                script {
                    try {
                        sh '''
                        gitleaks detect --source . --report-format json --report-path gitleaks-report.json
                        '''
                    } catch (e) {
                        echo '❌ leaks detected'

                        sh '''
                        echo "===== GITLEAKS REPORT ====="
                        cat gitleaks-report.json || echo "No report file found"
                        echo "==========================="
                        '''

                        echo "Stopping pipeline due to secret leakage"
                    }
                }
            }
        }

        stage('trivy file system scan') {
            steps {
                script {
                    try {
                        sh '''
                        trivy fs --severity CRITICAL --exit-code 1 .
                        '''
                    } catch (e) {
                        error "${e.message}"
                    }
                }
            }
        }

        stage('docker cleanup') {
            steps {
                sh '''
                docker image prune -af
                docker builder prune -af
                '''
            }
        }

        stage('docker build') {
            steps {
                sh 'docker build -t suvo32/central:latest .'
            }
        }
        
        stage('Trivy Image Scan') {
        steps {
        script {
            try {
                sh '''
                trivy image --exit-code 0 --severity CRITICAL,HIGH suvo32/central:latest
                '''
                echo "Trivy image scan completed successfully."
            }
            catch (e) {
                echo "Trivy found vulnerabilities in image: ${e}"
                error "Image scan failed. Fix vulnerabilities"
            }
        }
    }
}

        stage('push to dockerhub') {
            steps {
                withCredentials([usernamePassword(
                    credentialsId: 'dockerCreds',
                    usernameVariable: 'USERNAME',
                    passwordVariable: 'PASSWORD'
                )]) {
                    sh '''
                    docker login -u $USERNAME -p $PASSWORD
                    docker push $USERNAME/central:latest
                    '''
                }
            }
        }

        stage('docker compose') {
            steps {
                sh 'docker compose up -d'
            }
        }
    }
}