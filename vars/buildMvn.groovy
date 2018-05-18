def call(config) {
    try {
              setEnv([getArtifactsVersion: "true" ]) //This is for initial setup.
              def runNexusScan = env.BUILD_CAUSE_DETERMINED
              println "[INFO] : Will Nexus IQ scan be run? ${runNexusScan}"
              // just do projet builds here. we have separate scripts for whitehat
              if("${runNexusScan}" != "true") {
				  println "============================ BUILD MAVEN START ===================================="
                  sh "${mvnHome}/bin/mvn clean install"
				  //Check for Quality Gate conditions
				  qualityGate(config)

              // publish artifact on when built from master or release branch. And not a whitehat run
              if((("${BRANCH_NAME}".contains("master")) || ("${BRANCH_NAME}".contains("release/"))) && "${runNexusScan}" != "true"){
                  if (isLibertyPlugin)
                    sh "${mvnHome}/bin/mvn deploy -Durl=${ARTIFACTORY_URL}/${codeRepoType}/ -DrepositoryId=${codeRepoType}"
                  else
                    sh "${mvnHome}/bin/mvn deploy"
              }
			  println "============================ BUILD MAVEN COMPLETED ===================================="

              // Common job rename script
              setEnv([getArtifactsVersion: "true" ]) //This is to get correct version after deploy and renaming the build correctly
              script {
                if (env.configVersion)
                    currentBuild.displayName = "${env.APP_VERSION}_${env.BUILD_NUMBER}"
                else
                    currentBuild.displayName = "${env.APP_VERSION}"
                }
              }
              // execute whitehat script only when run with timer
              if("${runNexusScan}" == "true"){
                echo '[INFO] Evaluated job run eligible for Nexus & Whitehat Scan'
                if(("${BRANCH_NAME}".contains("master") || "${BRANCH_NAME}".contains("release/")) && (!"${PRODUCT_NAME}".endsWith("-config")))
                  whiteHatNexusEvaluation()
              }

	} catch (err) {
            error("Error encountered while maven build ${err}")
        }
}
