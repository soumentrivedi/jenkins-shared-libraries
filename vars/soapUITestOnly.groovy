def call(body) {
	// Runs only if it is not a whitehat triggered job
	if (! env.IS_WHITEHAT_JOB){
		try {
			println "[INFO] : checking out test data"
			println "[INFO] : smokeTestEnv : ${body.smokeTestEnv} , testSuiteXml: ${body.testSuiteXml}, testDatarepoGitHttpURL : ${body.testDatarepoGitHttpURL}"
			setEnv([getArtifactsVersion: "true" ])
			downloadArtifact(body)
			script {
				currentBuild.displayName = "${params.TEST_TYPE}_${body.smokeTestEnv}_${env.APP_VERSION}"
			}
			if ( (body.testSuiteXml && body.testSuiteXml != "null") && (body.testDatarepoGitHttpURL && body.testDatarepoGitHttpURL != "null") && (body.smokeTestEnv && body.smokeTestEnv != "null") ){

				// Retrieving the reponame from repo URL
				def testDataRepoName = "${body.testDatarepoGitHttpURL}".split('/').last().tokenize(".")[0]
				checkoutSCM ([branch: "master" ,
					testDatarepoGitHttpURL: "${body.testDatarepoGitHttpURL}",
					credentialsID: "${body.credentialsID}",
					directoryPath: "$WORKSPACE/${testDataRepoName}" ])

				soapUITest ([environment:  "${body.smokeTestEnv}",
					testSuiteXml: "${body.testSuiteXml}",
					testDatarepoGitHttpURL: "${body.testDatarepoGitHttpURL}",
					testMavenArgs: "${body.testMavenArgs}" ])
			} else {
				println "*********************************************************"
				println "           Skipping soapUITest Test"
				println "           testSuiteXml : ${body.testSuiteXml}"
				println "           testDatarepoGitHttpURL : ${body.testDatarepoGitHttpURL}"
				println "           smokeTestEnv : ${body.smokeTestEnv}"
				println "                   *********************************************************"
			}
		}
		catch (err) {
			error("Error encountered while running Soap UI tests" + "${err.getStackTrace()}")
			currentBuild.result = 'FAILED'
		}
	}
}
