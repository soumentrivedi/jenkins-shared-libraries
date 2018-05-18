def call(config) {

          setEnv([:])
          def runNexusScan = env.BUILD_CAUSE_DETERMINED
          // Checking and aborting the job if the current version of artifact is already present in artifactory.
          try {
                  if((("${BRANCH_NAME}".contains("master")) || ("${BRANCH_NAME}".contains("release/"))) && "${runNexusScan}" != "true"){
                      echo "Checking if the current version of artifact is already present in artifactory or not ."
                      getArtifactsByVersion()
                      def ARTIFACT_PATH_STATUS = sh ( script: "curl -Is ${env.APP_ARTIFACT_PATH} | head -1 | awk \'{print \$2}\'" , returnStdout: true).trim()
                      echo "ARTIFACTY_PATH_STATUS = ${ARTIFACT_PATH_STATUS}"
                      if ("${ARTIFACT_PATH_STATUS}" == "200") {
                        println("[ERROR] : Current version of artifact ${env.APP_VERSION} exists in artifactory , Please bump up the artifact version ")
                        error("ERROR : Current version of artifact ${env.APP_VERSION} exits in artifactory , Please bump up the artifact version ")
                      }
                  }

                  println "[INFO] : Will Nexus IQ scan be run? ${runNexusScan}"
                  // just do projet builds here, irrespective of branch. we have separate scripts for whitehat
                  if("${runNexusScan}" != "true") {
					  println "============================ BUILD NPM START ===================================="
                      sh "${env.buildType} install"
                      sh "${env.buildType} test"
                      sh "${env.buildType} run ci-test"
					  //Check for Quality Gate conditions
						qualityGate(config)
					} }catch (err) {
					if("${env.buildType}" == "yarn"){
						throw e
					} else {
						println "[ERROR] : Error encountered while npm build"
						error("Error encountered while npm build ${err}")
					}
				}
			         // publish artifact on when built from master or release branch. And not a whitehat run
         if((("${BRANCH_NAME}".contains("master")) || ("${BRANCH_NAME}".contains("release/"))) && "${runNexusScan}" != "true"){
             sh "${env.buildType} publish"
          }
		  println "============================ BUILD NPM COMPLETED ===================================="
          // Common job rename script
          setEnv([getArtifactsVersion: "true" ])
          echo "APP_VERSION = ${env.APP_VERSION}"
          echo "APP_ARTIFACT_PATH = ${env.APP_ARTIFACT_PATH}"
          script {
            currentBuild.displayName = "${env.APP_VERSION}_${env.BUILD_NUMBER}"
          }

          // execute whitehat script only when run with timer
          if("${runNexusScan}" == "true"){
            echo '[INFO] Evaluated job run eligible for Nexus & Whitehat Scan'
            if(("${BRANCH_NAME}".contains("master") || "${BRANCH_NAME}".contains("release/")) && (!"${PRODUCT_NAME}".endsWith("-config")))
              whiteHatNexusEvaluation()

          } else {
                 println "[INFO] : NEXUS IQ SCAN AND WHITEHAT ARTIFACT GENERATION WILL BE EXECUTED BASED ON THE TRIGGER CONFIGURED IN Jenkinsfile"
          }

}
