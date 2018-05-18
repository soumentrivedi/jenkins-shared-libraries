/*
  Separate script for doung Nexus Scan and WhiteHat package creation
*/

def call() {
  script{
    currentBuild.displayName = "${currentBuild.displayName}_WHITEHAT"
  }
  println "============================ NEXUS AND WHITEHAT SCAN START ===================================="
  echo '[INFO] Initiating Nexus IQ evaluation and WhiteHat package creation'
  if("${env.buildType}" == "maven"){
    echo "[INFO] Detected project type as : ${env.buildType}"
    def verifyGoal="clean verify -Dmaven.test.skip=true"
    def compileGoal="org.apache.maven.plugins:maven-dependency-plugin:2.10:copy-dependencies -DincludeScope=compile"
    def runtimeGoal="org.apache.maven.plugins:maven-dependency-plugin:2.10:copy-dependencies -DincludeScope=runtime"
    try {
      loadShellScripts("checkmodule.sh")
      def hasTestSuite = sh(returnStdout: true, script: "checkmodule.sh ${mvnHome} test-suite").trim()
      if (hasTestSuite) {
        echo "[INFO] Has test-suite in project : " + hasTestSuite
      } else {
        echo "[INFO] There is no test-suite in project."
      }

      if(hasTestSuite && "${hasTestSuite}" == "test-suite") {
        echo "[INFO] Adding test case module exclusion"
        verifyGoal = "${verifyGoal} -pl !${hasTestSuite}"
        compileGoal = "${compileGoal} -pl !${hasTestSuite}"
        runtimeGoal = "${runtimeGoal} -pl !${hasTestSuite}"
      }

      sh "${mvnHome}/bin/mvn ${verifyGoal}\n" +
         "${mvnHome}/bin/mvn ${compileGoal}\n" +
         "${mvnHome}/bin/mvn ${runtimeGoal}"
    } catch(err) {
        println "[ERROR]: occurs while trying to creating or executing maven goals. ${err}"
        throw err
    }
  } else {
    echo "[INFO] Detected project type as : ${env.buildType}"

    // only get production dependencies
    if("${env.buildType}" == "yarn") {
        sh "${env.buildType} install --production"
    } else {
        echo '[INFO] Getting the latest artifact from artifactory'
        def fileName = "${env.APP_ARTIFACT_PATH.tokenize('/')[-1]}"
        deleteDir()
        sh "curl -O ${env.APP_ARTIFACT_PATH}\n" +
           "tar -xzf ${fileName}\n" +
           "rm -f ${fileName}\n" +
           "cd ${WORKSPACE}/package\n" +
           "${env.buildType} install --only=production"
    }
  }

  echo '[INFO] Creating WhiteHat tar'
  env.BRANCH_NAME_WITHOUT_SLASH = ("${BRANCH_NAME}").replace("/","_")
  sh "touch whitehat_${BRANCH_NAME_WITHOUT_SLASH}.tar.gz"
  sh "tar -czf whitehat_${BRANCH_NAME_WITHOUT_SLASH}.tar.gz --exclude='*.tar.gz' --exclude='**/test-suite' ."
  artifactoryDeploy()
  echo "[INFO] : WhiteHat Artifact URL: ${ARTIFACTORY_URL}/list/whitehat/${env.PRODUCT_NAME}/whitehat_${env.BRANCH_NAME_WITHOUT_SLASH}.tar.gz"
  // removing tar file, so that it doesn't get picked up for nexusPolicyEvaluation
  sh "rm -f whitehat*.tar.gz"

  // adding nexus iq publish. iqStage will always be build. nexusIQAppId will be set in jenkinsfile
  if(env.nexusIQAppId) {
     println "[INFO] : Running nexusPolicyEvaluation with AppId configured in Jenkinsfile : ${env.nexusIQAppId}"
     nexusPolicyEvaluation failBuildOnNetworkError: false, iqApplication: "${env.nexusIQAppId}", iqScanPatterns: [[scanPattern: '**/*.jar'], [scanPattern: '**/*.war'], [scanPattern: '**/*.ear'], [scanPattern: '**/*.zip'], [scanPattern: '**/*.tar.gz'], [scanPattern: '**/*.js']], iqStage: 'build', jobCredentialsId: ''
  } else {
     echo "[INFO] nexusIQAppId was not configured in Jenkinsfile. Skipping nexusPolicyEvaluation"
  }

  // set environment variable if it is a whitehat triggered job
  env.IS_WHITEHAT_JOB = true
  println "============================ NEXUS AND WHITEHAT SCAN COMPLETED ===================================="
  echo "[INFO] : Sucessfully Completed the Whitehat skipping all other stages"
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
