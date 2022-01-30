def call(Map params = [:]) {
    // Start Default Arguments
    def args = [
            COMPONENT                  : '',
            LABEL                      : 'master'
    ]
    args << params

    pipeline {
        agent {
            label params.LABEL
        }

        options {
            ansiColor('xterm')
        }

        environment {
            NEXUS = credentials("NEXUS")
        }

        stages {

            stage('Labeling Build') {
                steps {
                    script {
                        str = GIT_BRANCH.split('/').last()
                        //addShortText background: 'yellow', color: 'black', borderColor: 'yellow', text: "COMPONENT = ${params.COMPONENT}"
                        addShortText background: 'yellow', color: 'black', borderColor: 'yellow', text: "BRANCH = ${str}"
//            addShortText background: 'orange', color: 'black', borderColor: 'yellow', text: "${ENV}"
                    }
                }
            }

            stage('Download NodeJS Dependencies') {
                steps {
                    sh """
            echo "+++++++ Before"
            ls -l
            npm install
            echo "+++++++ After"
            ls -l
          """
                }
            }

            stage('Submit Code Quality') {
                steps {
                    sh """
            #sonar-scanner -Dsonar.projectKey=${params.COMPONENT} -Dsonar.sources=. -Dsonar.host.url=http://172.31.10.164:9000 -Dsonar.login=f5d41bb23dfae907435a8ce9e63691e50b022787
            echo OK
          """
                }
            }

            stage('Check Code Quality Gate') {
                steps {
                    sh """
            #sonar-quality-gate.sh admin admin123 172.31.28.39 ${params.COMPONENT}
            echo OK 
          """
                }
            }

            stage('Test Cases') {
                steps {
                    sh 'echo Test Cases'
                }
            }

            stage('Upload Artifacts') {
                when {
                    expression { sh([returnStdout: true, script: 'echo ${GIT_BRANCH} | grep tags || true' ]) }
                }
                steps {
                    sh """
                    GIT_TAG=`echo ${GIT_BRANCH} | awk -F / '{print \$NF}'`
                    echo \${GIT_TAG} >version
                    zip -r ${params.COMPONENT}-\${GIT_TAG}.zip node_modules server.js version
                    curl -v -u ${NEXUS} --upload-file ${params.COMPONENT}-\${GIT_TAG}.zip http://172.31.10.164:8081/repository/${params.COMPONENT}/${params.COMPONENT}-\${GIT_TAG}.zip
                  """
                }
            }

            stage('Make AMI') {
                when {
                    expression { sh([returnStdout: true, script: 'echo ${GIT_BRANCH} | grep tags || true' ]) }
                }
                steps {
                    sh """
                    GIT_TAG=`echo ${GIT_BRANCH} | awk -F / '{print \$NF}'`
                    export TF_VAR_APP_VERSION=\${GIT_TAG}
                    terraform init
                    terraform apply -auto -approve
                  """
                }
            }

            stage('Delete AMI Instances') {
                when {
                    expression { sh([returnStdout: true, script: 'echo ${GIT_BRANCH} | grep tags || true' ]) }
                }
                steps {
                    sh """
                    GIT_TAG=`echo ${GIT_BRANCH} | awk -F / '{print \$NF}'`
                    export TF_VAR_APP_VERSION=\${GIT_TAG}
                    terraform init
                    terraform state rm module.ami.aws_ami_from_instance.ami
                    terraform destroy -auto -approve
                  """
                }
            }

//      stage('App Deployment - Dev Env') {
//        steps {
//          script {
//            GIT_TAG = GIT_BRANCH.split('/').last()
//          }
//          build job: 'Mutable/App-Deploy', parameters: [
//              string(name: 'ENV', value: 'dev'),
//              string(name: 'APP_VERSION', value: "${GIT_TAG}"),
//              string(name: 'COMPONENT', value: "${params.COMPONENT}")
//          ]
//        }
//      }

        }

        post {
            always {
                cleanWs()
            }
        }

    }

}

