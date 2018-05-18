def call(body) {
try {
    echo "[INFO] Running Soap UI tests....."

    echo "[INFO] Running Soap UI tests on : ${body.environment}"
    echo "[INFO] Running Soap UI tests with suite : ${body.testSuiteXml}"
    def artifactFileName = "${env.APP_TEST_SUITE_ARTIFACT_PATH.tokenize('/')[-1]}"
    def testDataRepoName = "${body.testDatarepoGitHttpURL}".split('/').last().tokenize(".")[0]
    def testPOMPath = "${testDataRepoName}".split('-test-config').first()
    def testSuiteExtension
    env.TEST_POM_PATH = "${testPOMPath}"
    // remove old files in worskpace
    sh "rm -rf ${WORKSPACE}/${testPOMPath}\n" +
       "mkdir -p ${WORKSPACE}/${testPOMPath}/test-suite"

    if ("${env.APP_TEST_SUITE_ARTIFACT_PATH}" != "null" && "${env.APP_TEST_SUITE_ARTIFACT_PATH}" != ""){
      echo "[INFO] Getting Test Artifact from artifactory : ${env.APP_TEST_SUITE_ARTIFACT_PATH}"
      // when you HAVE test suite as an artifact
      testSuiteExtension = downloadFile("${env.APP_TEST_SUITE_ARTIFACT_PATH}")
      switch(testSuiteExtension) {
        case 'tgz':
            echo '[INFO] Using tar utility to extract :' + artifactFileName
            sh "rm -rf ${WORKSPACE}/tmp\n"+
               "mkdir ${WORKSPACE}/tmp\n"+
               "tar xzf ${WORKSPACE}/${artifactFileName} -C ${WORKSPACE}/tmp\n"+
               "cp -rf ${WORKSPACE}/tmp/package/test-suite/* ${WORKSPACE}/${testPOMPath}/test-suite/\n"
        break
        default:
            echo '[INFO] Using unzip utility to extract :' + artifactFileName
            sh "rm -rf ${WORKSPACE}/tmp\n"+
               "mkdir ${WORKSPACE}/tmp\n"+
               "unzip -q ${WORKSPACE}/${artifactFileName} -d ${WORKSPACE}/tmp"
            def unzipFolderName = sh returnStdout: true, script: "ls ${WORKSPACE}/tmp"
            unzipFolderName = "${unzipFolderName}".trim()
            sh "cp -rf ${WORKSPACE}/tmp/${unzipFolderName}/* ${WORKSPACE}/${testPOMPath}/test-suite/"
        break
      }

    } else {
          echo "[INFO] Getting Test Artifact from currect branch : ${BRANCH_NAME}"
          // when you don't have test suite as an artifact
          sh "cp -rf ${WORKSPACE}/test-suite/* ${WORKSPACE}/${testPOMPath}/test-suite/"
    }
    if ( body.testMavenArgs && "${body.testMavenArgs}" != "null" )
      SOAPUI_MAVEN_ARGS="${body.testMavenArgs}"
    else
      SOAPUI_MAVEN_ARGS=""
    sh "cd ${WORKSPACE}/${testPOMPath}/test-suite/ && ${mvnHome}/bin/mvn clean test -U -DskipTestSuiteTests=false -Dsoapui.https.protocols=TLSv1,TLSv1.2,SSLv3 -DsuiteXmlFile=${WORKSPACE}/${testPOMPath}/${body.testSuiteXml} -DEnvName=${body.environment} ${SOAPUI_MAVEN_ARGS}"

  }
  catch (err) {
      error("Error encountered while running Soap UI tests" + "${err.getStackTrace()}")
  }
}

// Downloads file when an URL is passed in and check for the status code 200
def downloadFile(address){
  sh "rm -rf ${WORKSPACE}/${address.tokenize('/')[-1]}"
  def DOWNLOAD_ARTIFACT_STATUS = sh ( script: "curl -Is ${address} | head -1 | awk \'{print \$2}\'" , returnStdout: true).trim()
  def fileName = "${WORKSPACE}/${address.tokenize('/')[-1]}"

  if ("${DOWNLOAD_ARTIFACT_STATUS}" == "200") {
    println("[INFO] : Check the artifactory URL ${address} and returned status code: ${DOWNLOAD_ARTIFACT_STATUS}")
  } else {
    println("[ERROR] : Error while checking URL ${address} and returned status code: ${DOWNLOAD_ARTIFACT_STATUS}")
    error("[ERROR] : Error while checking URL ${address} and returned status code: ${DOWNLOAD_ARTIFACT_STATUS}")
  }

  sh "curl -O ${address}"
  sh "if [[ ! -f ${fileName} ]]; then echo '[ERROR] : Downloaded artifact was not found in workspace'; exit 1; fi;"
  return "${fileName.tokenize('.')[-1]}"
}
