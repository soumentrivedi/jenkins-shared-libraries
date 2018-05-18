@Grab('org.yaml:snakeyaml:1.17')

import org.yaml.snakeyaml.Yaml

def call(config) {

	try {
		// Discard the old builds keep max number of builds to 50
		if (currentBuild.rawBuild.project.logRotator == null || currentBuild.rawBuild.project.logRotator.numToKeepStr != "50" ) {
			discardOldBuilds()
			println "[INFO:] Discard old Builds is configured and Maximum number of builds to keep is set to ${currentBuild.rawBuild.project.logRotator.numToKeepStr}"
		}
		// check if buildType is defined in Jenkinsfile
		if("${env.buildType}" == "" || "${env.buildType}" == "None" || !env.buildType){
			println "[ERROR] Could not determine buildType from Jenkinsfile. Make sure you define buildType=\"npm\" or buildType=\"maven\""
			error("[ERROR] Could not determine buildType from Jenkinsfile. Make sure you define buildType=\"npm\" or buildType=\"maven\"")
		} else if ("${env.buildType}" != "npm" && "${env.buildType}" != "maven" && "${env.buildType}" != "yarn") {
			println "[ERROR] Acceptable value for buildType is only : buildType=\"npm\" or buildType=\"yarn\" or buildType=\"maven\". And Not: ${env.buildType}"
			error("[ERROR] Acceptable value for buildType is only : buildType=\"npm\" or buildType=\"yarn\" or buildType=\"maven\". And Not: ${env.buildType}")
		}

		env.CF_HOME="${WORKSPACE}"
		env.mvnHome = tool 'maven-3.3.9'
		env.sonarScannerHome = tool name: 'SonarQube Runner', type: 'hudson.plugins.sonar.SonarRunnerInstallation';
		env.IS_WHITEHAT_JOB = false
		env.IS_PR_BUILD = "${BRANCH_NAME}".matches("PR-\\d+")

		if ("${BUILD_URL}".contains("local")){
			env.ARTIFACTORY_URL ="http://localhost:18081/artifactory"
			env.SONARQUBE_URL = "http://localhost:18082"
		} else if ("${BUILD_URL}".contains("sandbox")){
			env.ARTIFACTORY_URL ="https://artifactory-fof-sandbox.appl.kp.org/artifactory"
			env.SONARQUBE_URL = "https://sonarqube-fof-sandbox.appl.kp.org"
		} else {
			env.ARTIFACTORY_URL ="https://artifactory-fof.appl.kp.org/artifactory"
			env.SONARQUBE_URL = "https://sonarqube-fof.appl.kp.org"
		}

		if (env.projectenv){
			env.ARTIFACTORY_URL ="https://artifactory-bluemix.kp.org"
			env.SONARQUBE_URL = "https://sonarqube-bluemix.kp.org/"
			env.UCD_URL = "https://ucd-devops.kp.org"
		}

		PRODUCT_NAME_WITHOUT_TRIM = sh(returnStdout: true, script: 'echo \"${JOB_NAME}\" | cut -f2 -d/')
		env.PRODUCT_NAME = "${PRODUCT_NAME_WITHOUT_TRIM}".trim()

		if (config.getArtifactsVersion && "${config.getArtifactsVersion}" == "true") {
				println "Using Custom Liberty Package: ${config.useCustomLibertyPackage}"
				if (!env.projectenv) getArtifactsByVersion([stage: "${config.stage}", useCustomLibertyPackage: "${config.useCustomLibertyPackage}"])
				if (params.ARTIFACT_VERSION) env.APP_VERSION = "${params.ARTIFACT_VERSION}"
				if (params.configVersion) env.configVersion = "${params.configVersion}"
			}
		// check build cause and set environment variable. This will be used for whitehat & nexusPolicyEvaluation
		env.BUILD_CAUSE_DETERMINED = isJobStartedByTimer()

		if(config.stage && "${config.stage}" == "DEPLOY") {
			// manifest file locaton will be determined based on bluemix space
			def manifestfile = "${WORKSPACE}/config/env/${config.environment}/manifest.yml"
			env.manifestfile = manifestfile
			println "[INFO] Manifest file path computed : ${manifestfile}"

			// checking out config repo to determine groupId and appArtifactId
			if (env.configRepoGitHttpURL && env.configVersion) {
				println "*********************** Downloading config artifact from artifactory   ***********************"
				def configRepoName = "${env.configRepoGitHttpURL}".split('/').last().tokenize(".")[0]
				checkoutSCM([branch: "master", testDatarepoGitHttpURL: "${env.configRepoGitHttpURL}",
					credentialsID: "${config.credentialsID}", directoryPath: "${WORKSPACE}/${configRepoName}"])
				env.UPDATED_CONFIG_VERSION = getConfigToDeploy([ configRepoName: "${configRepoName}",
					configVersion: "${env.configVersion}"])
			} else {
				println "[ERROR] Please configure configRepoGitHttpURL & configVersion in Jenkinsfile"
				error("Please configure configRepoGitHttpURL & configVersion in Jenkinsfile")
			}

			getProjDetails([ manifestfile: "${manifestfile}"])
		}
	}
	catch(err) {
		println "Error while setting environmets variables"
		error("Error while setting environmets variables")
	}
}

// get getProjDetails from manifest yml and set environment variables before app published to bluemix
def getProjDetails(body){

	try {
		echo "Parsing manifest.yml file for setting environment variables :: ${body.manifestfile}"

		// check if the file exists
		if (fileExists("${body.manifestfile}")) {
			def name
			String file = readFile("${body.manifestfile}")
			Yaml parser = new Yaml()
			Map ymap = (Map) parser.load("${file}")
			// replacing unwanted brackets and removing version details
			env.BLUE_MIX_NAME="${ymap['applications']['name']}".replace(/[/,"").replace(/]/,"").replaceAll(/(-\d).*/,"")
			env.BLUE_MIX_NAME_VERSIONED="${ymap['applications']['name']}".replace(/[/,"").replace(/]/,"").replaceAll(/(-\d).*/,"") + "-" + "${env.APP_VERSION}"
			env.BLUE_MIX_HOST="${ymap['applications']['host']}".replace(/[/,"").replace(/]/,"")
			env.BLUE_MIX_APP_DOMAIN="${ymap['applications']['domains'][0][0]}".toString()
			env.BLUE_MIX_APP_DOMAIN_LIST="${ymap['applications']['domains']}".toString()
			env.BUILD_PACK="${ymap['applications']['buildpack'][0]}"
			println " env.BLUE_MIX_APP_DOMAIN is " + env.BLUE_MIX_APP_DOMAIN
			env.BLUE_MIX_SERVICES="${ymap['applications']['services'][0]}".toString()
			env.IS_PROD = "${env.BLUE_MIX_NAME}".startsWith("prod")
			println "Bluemix App name: ${env.BLUE_MIX_NAME}, Bluemix App Name Versioned: ${env.BLUE_MIX_NAME_VERSIONED}, Bluemix Host: ${env.BLUE_MIX_HOST}, Bluemix App Domain: ${env.BLUE_MIX_APP_DOMAIN},BlueMix Services: ${env.BLUE_MIX_SERVICES}, Is Deploy Prod?: ${env.IS_PROD}"
			// // setting them to null to handle serializable exceptions
			ymap=null; parser=null


		} else {
			println "No manifest.yml found in this path ${body.manifestfile}"
			error("No manifest.yml found in this path ${body.manifestfile}")
		}
	} catch (err){
		println "Error while getting project details from  manifest.yml found in this path ${body.manifestfile}"
		error("Error while getting project details from  manifest.yml found in this path ${body.manifestfile}")
	}
}

// check if the job was triggered by timer, declared in Jenkinsfile
def isJobStartedByTimer() {
	def startedByTimer = false
	try {
		def buildCauses = currentBuild.rawBuild.getCauses()
		for ( buildCause in buildCauses ) {
			if (buildCause != null) {
				def causeDescription = buildCause.getShortDescription()
				println "[INFO] : Cause of Build: ${causeDescription}"
				if (causeDescription.contains("Started by timer")) {
					startedByTimer = true
				}
			}
		}
	} catch(theError) {
		println "[ERROR] : Error getting build cause"
		error("[ERROR] : Error determining build cause. WhiteHat scan not executed.")
	}

	return startedByTimer
}

def discardOldBuilds() {
	properties([
		buildDiscarder(logRotator(numToKeepStr: '50'))
	])
}
