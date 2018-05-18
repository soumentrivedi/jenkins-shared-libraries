def call(body) {
  try {
echo "body is ${body}"

dir("${body.directoryPath}") {
git branch: "${body.branch}", credentialsId: "${body.credentialsID}", url: "${body.testDatarepoGitHttpURL}"
    }
    sh " ls -lrt ${body.directoryPath}"
  }
  catch (err) {
      currentBuild.result = 'FAILED'
      throw err
    }
}
