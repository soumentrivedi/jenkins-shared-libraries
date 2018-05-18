/*
Selenium test function to be called from Jenkins pipeline.

List of Arguments:
1. environment : will be used to pull author url from  UCD.

*/

def call(body){
	echo "**************** Running Selenium tests *********************"
	def test_url

	/* Fetch UCD urls based on environments. This needs more work and testing */
	//SmokeEnvironmentURL smokeEnvironmentUrl = new SmokeEnvironmentURL(context.UCD_URL, context.UCD_TOKEN, environment, applicationName, componentName)
        //def urls = smokeEnvironmentUrl.getEnvironmentUrlFromUCD()
        //println "URLs for ${environment} for ${applicationName} and ${componentName} are: " + urls
        
	

	// based on the environment, author url will be queried from UCD
	
	if ("${body.environment}" == "qa"){
		test_url = "http://xjzxqep0011x.dta.kp.org:4503"
	}
	
	sh "chmod 755 it.tests/functional/*"
	sh "it.tests/functional/execute.sh ${test_url}"


}

