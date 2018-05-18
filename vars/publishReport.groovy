def call(body) {
	try {
		if (checkIsWhitehatOrPR()){
			def reportName
			def reportDir

			if ("${body.report_type}".toLowerCase() == "clover") {
				println "Publishing clover report from directory ${body.report_dir} and clover filename ${body.reportFiles}"
				step([
					$class: 'CloverPublisher',
					cloverReportDir: "${body.report_dir}",
					cloverReportFileName: "${body.reportFiles}",
					healthyTarget: [methodCoverage: 70, conditionalCoverage: 80, statementCoverage: 80], // optional, default is: method=70, conditional=80, statement=80
					unhealthyTarget: [methodCoverage: 40, conditionalCoverage: 40, statementCoverage: 40], // optional, default is none
					failingTarget: [methodCoverage: 0, conditionalCoverage: 0, statementCoverage: 0]     // optional, default is none
				])

			}  else if ("${body.report_type}".toLowerCase() == "cobertura") {
				println "Publishing cobertura report"
				step([$class: 'CoberturaPublisher', autoUpdateHealth: false, autoUpdateStability: false,
					coberturaReportFile: '**/'+"${body.reportFiles}", failUnhealthy: false,
					failUnstable: false, maxNumberOfBuilds: 0, onlyStable: false,
					sourceEncoding: 'ASCII', zoomCoverageChart: false])
			} else if ("${body.report_type}".toLowerCase() == "soapui"){
				println "Publishing soapui report"
				reportDir = "${env.TEST_POM_PATH}" + "/" + "${body.reportDir}"
				reportName = "${body.reportName}" + " Report"
				publishHTML target: [
					allowMissing: false,
					alwaysLinkToLastBuild: false,
					keepAll: true,
					reportDir: "${reportDir}",
					reportFiles: "${body.reportFiles}",
					reportName: "${reportName}"
				]
			} else {
				println "Publishing default HTML report"
				publishHTML target: [
					allowMissing: false,
					alwaysLinkToLastBuild: false,
					keepAll: true,
					reportDir: "${body.reportDir}",
					reportFiles: "${body.reportFiles}",
					reportName: "${body.reportName}"
				]
			}
		}
	}
	catch (err) {
		error("Error while publishing the reports ${err}")
	}
}
