/*

Description: Shared Library function to generate whitehat.tar.gz
Applicable only for "master" & "release" branches
*/
def call() {
      try {
		  script{
			  currentBuild.displayName = "${currentBuild.displayName}_WHITEHAT"
		  }

        if(("${BRANCH_NAME}".contains("master")) || ("${BRANCH_NAME}".contains("release"))) {
                    PRODUCT_NAME = sh(returnStdout: true, script: 'echo \"${JOB_NAME}\" | cut -f2 -d/')
                    env.PRODUCT_NAME = "${PRODUCT_NAME}".trim()
                    echo "Folder for whitehat is ${PRODUCT_NAME}"
                    echo "Job Name is ${JOB_NAME}"
                    sh "mkdir -p /apps/jenkins-slave/workspace/whitehat/${PRODUCT_NAME}"
                    sh "cd /apps/jenkins-slave/workspace/whitehat/${PRODUCT_NAME}"
                    println "[INFO] : Directory created for WhiteHat under :: /apps/jenkins-slave/workspace/whitehat/${PRODUCT_NAME}"
                    unstash 'whitehat_tar'
                    println "[INFO] : Unstash complete"
                    artifactoryDeploy()
                    println "[INFO] : WhiteHat Artifact URL: ${ARTIFACTORY_URL}/list/whitehat/${env.PRODUCT_NAME}/whitehat_${BRANCH_NAME}.tar.gz"
                    // removing tar file, so that it doesn't get picked up for nexusPolicyEvaluation
                    sh "rm -f whitehat*.tar.gz"
                    // adding nexus iq publish. iqStage will always be build. nexusIQAppId will be set in jenkinsfile
                    if(env.nexusIQAppId) {
                        println "[INFO] : Running nexusPolicyEvaluation with AppId configured in Jenkinsfile : ${env.nexusIQAppId}"
                        nexusPolicyEvaluation failBuildOnNetworkError: false, iqApplication: "${env.nexusIQAppId}", iqScanPatterns: [[scanPattern: '**/*.jar'], [scanPattern: '**/*.war'], [scanPattern: '**/*.ear'], [scanPattern: '**/*.zip'], [scanPattern: '**/*.tar.gz'], [scanPattern: '**/*.js']], iqStage: 'build', jobCredentialsId: ''
                    }
				    // set environment variable if it is a whitehat triggered job
				   env.IS_WHITEHAT_JOB = true
				   println "[INFO] : Sucessfully Completed the Whitehat skipping all other stages"
                  }
                  else
                  println "[INFO] : Whitehat is Applicable only for master/release branches"
        } catch (err) {
            println "[ERROR] : Error while creating whiteHat artifact and running nexusPolicyEvaluation"
            error("[ERROR] : Error while creating whiteHat artifact and running nexusPolicyEvaluation")
        }
}

def artifactoryDeploy() {
  try{
        def server = Artifactory.server('artifactory-production-whitehat')
        def uploadSpec = """{
                            "files": [
                            {
                              "pattern": "whitehat*.tar.gz",
                              "target": "whitehat/${env.PRODUCT_NAME}/"
                            }
                            ]
                            }"""
        server.upload(uploadSpec)
  } catch(err){
    println "[ERROR] : Error while uploading whiteHat artifact"
    error("[ERROR] : Error while uploading whiteHat artifact")
  }
}
