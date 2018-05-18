def call(config) {
	try {
		if (isQualityGate()){
						if (config == null || config == '') {
							error ("For release branch has been changed please configure sonarqube variables in build stage. For configuration/reference please look into https://confluence-fof.appl.kp.org/display/FF/Jenkins+Configuration+for+Release+Branch+for+Bluemix+Pipeline")
						}else {
							env.ENABLE_QUALITY_GATE = true
							codeQuality(config)
						}
					}else if(config) {
					codeQuality(config)
		}
	}catch (err) {
	error("Error encountered while determining the quality gate ${err}")
}
}
def isQualityGate() {
	loadShellScripts("sonar_quality_gates.yml")
	def qualityGate = readYaml file: 'sonar_quality_gates.yml'
	def repoName = "${env.JOB_NAME}".tokenize('/')[0]
	def enableSonarQualityList
	def sonarApplications = qualityGate.'applications'

	// Check for the application to enable sonar Quality gate
	if(sonarApplications."${repoName}" != null && sonarApplications."${repoName}" != "") {

		enableSonarQualityList =  sonarApplications."${repoName}"
	}
	def applicationName = scm.getUserRemoteConfigs()[0].getUrl().tokenize('/')[-1].split("\\.")[0]

	//Remove the sonar_quality_gates.yml file in workspace
	sh "if [[ -f ${WORKSPACE}/sonar_quality_gates.yml ]]; then rm -f ${WORKSPACE}/sonar_quality_gates.yml ;fi"
	if (enableSonarQualityList) {
	for (eachApp in enableSonarQualityList) {
		//check if list matches application name or repo name
		if ("${eachApp}".equals("${applicationName}") || "${eachApp}".equals("all")){
			if ("${BRANCH_NAME}".contains("release/")) {
				return true
		}
		} else { return false}
		}
		}else {
			return false
		}
}
