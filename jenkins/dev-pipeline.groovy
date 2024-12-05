pipeline {
    agent any

    tools {
        maven 'maven-3.8.6' // Match the name from Global Tool Configuration
        dockerTool 'docker-latest' // Match the docker tool name from Tool Configuration
    }

    environment {
        // Define environment variables if needed
        //JAVA_HOME = '/path/to/java' // Update this to your Java installation path if we want to choose other jdk
        //PATH = "${JAVA_HOME}/bin:${env.PATH}"
        GIT_REPO = 'https://github.com/omaher/spring-boot-app.git' // Replace with your repository URL
        // Define Docker Hub repository details
        DOCKER_TLS_VERIFY = 'false'
        DOCKER_CERT_PATH = ''
        DOCKER_IMAGE = "omaher/spring-boot-app"
        DOCKER_HUB_CREDENTIALS = 'docker-hub-credentials-id'  // Jenkins credentials ID
        
    }

    parameters {
        string(name: 'BRANCH', defaultValue: 'main', description: 'Git branch to checkout') // User-provided branch
    }

    stages {
        stage('Checkout') {
            steps {
                withCredentials([string(credentialsId: 'Github-access', variable: 'GITHUB_TOKEN')]) {
                script {
                    try {
                        // Attempt to checkout the specified branch
                        checkout([
                            $class: 'GitSCM',
                            branches: [[name: "*/${params.BRANCH}"]],
                            userRemoteConfigs: [[url: "${GIT_REPO}", credentialsId: 'Github-access']],
                            extensions: [[$class: 'CleanBeforeCheckout']]
                        ])
                    } catch (Exception e) {
                        // Fail the pipeline if branch is not found
                        error("Branch '${params.BRANCH}' not found in repository '${GIT_REPO}'.")
                    }
                }
                }
            }
        }

        stage('Build') {
            steps {
                // Clean and build the project using Maven
                sh 'mvn -Dmaven.test.failure.ignore=true clean package'

                // Check if the JAR file was created
                //    if (!fileExists(JAR_FILE)) {
                //        error "Build failed: JAR file not created"
                //    }
            }
        }

        stage('Test') {
            steps {
                sh 'echo "This is test stage."'
                // Run unit tests
                //sh 'mvn test'
            }
        }

        stage('Build Docker Image') {
            steps {
                script {
                    // Use Jenkins build number as the Docker tag
                    def dockerTag = "${DOCKER_IMAGE}:${BUILD_NUMBER}"
                    
                    // Build Docker image with the build number as the tag
                    def customImage = docker.build(dockerTag)
                }
            }
        }

        stage('Login to Docker Hub') {
            steps {
                script {
                    // Login to Docker Hub using Jenkins credentials
                    docker.withRegistry('https://index.docker.io/v1/', DOCKER_HUB_CREDENTIALS) {
                        // Login is automatically handled by docker.withRegistry
                    }
                }
            }
        }

        stage('Push Docker Image') {
            steps {
                script {
                    // Push the Docker image to Docker Hub with the build number tag
                    docker.push("${DOCKER_IMAGE}:${BUILD_NUMBER}")
                }
            }
        }

    }

    post {
        success {
            echo 'Spring Boot application started successfully!'
            //junit '**/target/surefire-reports/TEST-*.xml'
            //archiveArtifacts 'target/*.jar'
        }
        failure {
            echo 'Build or application start failed.'
        }
    }
}
