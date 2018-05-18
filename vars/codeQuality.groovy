def call(config) {
	// Runs only if it is not a whitehat triggered job
		if (checkIsWhitehatOrPR()){
		try {
			setEnv([:])
            if ("${config.clover}" == "true") {
              if ("${env.buildType}" == "maven") sh "${mvnHome}/bin/mvn verify clover:instrument-test clover:aggregate clover:clover -U"
              else sh 'echo "Not a maven project. Skipping Clover"'
            }
				}
		catch(err) {
			currentBuild.result = 'FAILED'
			println "[ERROR] : Error encountered while clover code quality check"
			error("[ERROR] : Error encountered while clover code quality check")
			throw err
		}
		try {
				  sh 'echo "Sonar is running"'
              println "============================ SONAR ANALYSIS START ===================================="
              def DEFAULT_SONAR_EXCLUSIONS
              def DEFAULT_SONAR_COVERAGE_EXCLUSIONS
              PRODUCT_NAME = sh(returnStdout: true, script: 'echo \"${JOB_NAME}\" | cut -f2 -d/')
              env.PRODUCT_NAME = "${PRODUCT_NAME}".trim()
              if ("${env.buildType}" == "npm" || "${env.buildType}" == "yarn") {
                  echo "node project"
                  DEFAULT_SONAR_EXCLUSIONS = [
                      '**/target/**,**/target/*',
                      '**/ui.resources/node_modules/**',
                      '**/ui.resources/node_modules/*',
                      '**/ui.resources/bower_components/**',
                      '**/ui.resources/bower_components/*',
                      'node_modules/**',
                      'node_modules/*',
                      '**/*.jpg,**/*.svg,**/vendor.bundle.js'
                      ]
                  DEFAULT_SONAR_COVERAGE_EXCLUSIONS = []

              } else if ("${env.buildType}" == "maven") {
                  echo "maven project"
                  DEFAULT_SONAR_EXCLUSIONS = [
                      '**/target/**,**/target/*',
                      '**/ui.resources/node_modules/**',
                      '**/ui.resources/node_modules/*',
                      '**/ui.resources/bower_components/**',
                      '**/ui.resources/bower_components/*',
                      '**/*.jpg,**/*.svg,**/vendor.bundle.js'
                      ]
                  DEFAULT_SONAR_COVERAGE_EXCLUSIONS = [
                      '**/it.tests/**'
                  ]
              }
                def exclusionsList = (config.sonarExclusions && config.sonarExclusions != "null")? DEFAULT_SONAR_EXCLUSIONS.plus(sonarExclusions) : DEFAULT_SONAR_EXCLUSIONS
                def coverageExclusionsList = (config.sonarCoverageExclusions && config.sonarCoverageExclusions != "null")? DEFAULT_SONAR_COVERAGE_EXCLUSIONS.plus(sonarCoverageExclusions) : DEFAULT_SONAR_COVERAGE_EXCLUSIONS
                def sonarCloverReportPathGenerated = (config.sonarCloverReportPath && config.sonarCloverReportPath != "null") ? config.sonarCloverReportPath : '/target/site/clover.xml'
                def sonarLcovReportPathGenerated = (config.sonarLcovReportPath && config.sonarLcovReportPath != "null") ? config.sonarLcovReportPath : '/coverage/lcov.info'
                def sonarCoberturaReportPathGenerated = (config.sonarCoberturaReportPath && config.sonarCoberturaReportPath != "null") ? config.sonarCoberturaReportPath : '/target/coverage/cobertura.xml'
              script {
                env.scannerHome = tool name: 'SonarQube Runner', type: 'hudson.plugins.sonar.SonarRunnerInstallation';
              }
              withSonarQubeEnv('Sonar') {
                sh """
                   ${sonarScannerHome}/bin/sonar-runner -e \\
                  -Dsonar.projectKey=org.sonarqube:${env.PRODUCT_NAME} \\
                  -Dsonar.host.url=${SONARQUBE_URL} \\
                  -Dsonar.projectName=${env.PRODUCT_NAME} \\
                  -Dsonar.projectVersion=${env.APP_VERSION} \\
                  -Dsonar.sources=. -Dsonar.exclusions='${(exclusionsList).join(', ')}' \\
                  -Dsonar.coverage.exclusions='${(coverageExclusionsList).join(', ')}' \\
                  -Dsonar.clover.reportPath=${sonarCloverReportPathGenerated} \\
                  -Dsonar.javascript.lcov.reportPaths=${sonarLcovReportPathGenerated} \\
                  -Dsonar.cobertura.reportPath=${sonarCoberturaReportPathGenerated} \\
                  -Dsonar.sourceEncoding=UTF-8 \\
									-Dsonar.java.binaries=.
                  """
              }

			if (("${env.ENABLE_QUALITY_GATE}" == "true" && "${BRANCH_NAME}".contains("release/")) || ("${config.enforceQualityGate}" == "true")) {
				timeout(time: 10, unit: 'MINUTES') {
					def qualityGate = waitForQualityGate()
					println "Quality gate status: ${qualityGate.status}"
					if (qualityGate.status != "OK") {
						error ("Pipeline aborted because code doesnot met the FOTF-Quality gate threshold.For FOTF-Quality gate standards Please refer https://sonarqube-fof.appl.kp.org/quality_gates/show/4")

					}
				}
			}
			println "============================ COMPLETED SONAR ANALYSIS ===================================="
            }
		catch(err) {
				currentBuild.result = 'FAILED'
				error("[ERROR] : Error encountered while sonar code quality check ${err}")
			}
	}
}
