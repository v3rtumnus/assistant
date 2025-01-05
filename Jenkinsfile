pipeline {
    agent any

    triggers {
        pollSCM('*/5 * * * *')
    }

    stages {
        stage('Clone sources') {
            steps {
                git credentialsId: 'github-ssh',
                    url: 'git@github.com:v3rtumnus/assistant.git',
                    branch: 'main'
            }
        }

        stage('Create bootable jar') {
            steps {
                sh './gradlew clean bootJar'
            }
        }

        stage('Deploy service') {
            steps {
                sh 'docker-compose -f /var/assistant-data/docker-compose.yml -p assistant down'
                sh 'cp build/libs/assistant.jar /var/assistant-data/docker'
                sh 'docker-compose -f /var/assistant-data/docker-compose.yml -p assistant build'
                sh 'docker-compose -f /var/assistant-data/docker-compose.yml -p assistant up -d'
            }
        }
    }
}
