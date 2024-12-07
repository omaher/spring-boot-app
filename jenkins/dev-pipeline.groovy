pipeline {
    agent any

    tools {
        maven 'maven-3.8.6' // Match the name from Global Tool Configuration
        dockerTool 'docker' // Match the docker tool name from Tool Configuration
    }

    environment {
        // Define environment variables if needed
        //JAVA_HOME = '/path/to/java' // Update this to your Java installation path if we want to choose other jdk
        //PATH = "${JAVA_HOME}/bin:${env.PATH}"
        GIT_REPO = 'https://github.com/omaher/spring-boot-app.git' // Replace with your repository URL
        // Define Docker Hub repository details
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

        stage('Setup AWS CLI') {
            steps {
                withCredentials([[
                    $class: 'AmazonWebServicesCredentialsBinding', 
                    credentialsId: 'aws_credentials' // Use the ID of your Jenkins credentials
                ]]) {
                    sh '''
                    # Configure AWS CLI with credentials from Jenkins
                    aws configure set aws_access_key_id $AWS_ACCESS_KEY_ID
                    aws configure set aws_secret_access_key $AWS_SECRET_ACCESS_KEY
                    aws configure set default.region $AWS_REGION
                    aws configure set output json

                    # Test AWS CLI configuration
                    aws sts get-caller-identity
                    '''
                }
            }
        }

        stage('Fetch EC2 Instance Details') {
            steps {
                // Fetch the public IP of the EC2 instance
                script {
                    env.INSTANCE_IP = sh(
                        script: "aws ec2 describe-instances --region ${AWS_REGION} --instance-ids ${INSTANCE_ID} --query 'Reservations[*].Instances[*].PublicIpAddress' --output text",
                        returnStdout: true
                    ).trim()
                }

                echo "EC2 Instance IP: ${env.INSTANCE_IP}"
            }
        }

         stage('Connect to EC2') {
            steps {
                script {
                    // Command to SSH into the instance
                    def sshCommand = """
                    ssh -i ${SSH_KEY} -o StrictHostKeyChecking=no ${USER}@${env.INSTANCE_IP} "echo 'Connected to EC2 instance'; uname -a"
                    """

                    // Execute the SSH command
                    sh sshCommand
                }
            }
        }

        stage('Build Docker Image') {
            steps {
                sh '''
                    docker build -t ${DOCKER_IMAGE}:${BUILD_NUMBER} 
                    
                '''
            }
        }

        stage('Login to Docker Hub') {
            steps {
                // Use Jenkins credentials
                withCredentials([usernamePassword(credentialsId: 'DOCKER_HUB_CREDENTIALS', usernameVariable: 'DOCKER_USERNAME', passwordVariable: 'DOCKER_PASSWORD')]) {
                    sh '''
                        # Log in to Docker Hub
                        echo "${DOCKER_PASSWORD}" | docker login -u "${DOCKER_USERNAME}" --password-stdin
                    '''
                    }
                }
            }
        }

        stage('Push Docker Image') {
            steps {
                sh '''
                # Push the Docker image to Docker Hub
                docker push ${DOCKER_IMAGE}:${BUILD_NUMBER}
                '''
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
