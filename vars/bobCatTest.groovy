/*
Bobcat test function to be called from Jenkins pipeline.

List of Arguments:
1. environment : will be used to pull author url from  UCD.
2. author_login: to be resolved from jenkins bindings or mask password
3. author_password: to be resolved from jenkins bindings or mask password

*/

def call(body){

		try{
						echo "**************** Running BobCat tests *********************"
						echo "${body.environment}"
						echo "${body.author_login}"
						echo "${body.author_password}"
						def author_url
						def environment

						/* Fetch UCD urls based on environments. This needs more work and testing */
						//SmokeEnvironmentURL smokeEnvironmentUrl = new SmokeEnvironmentURL(context.UCD_URL, context.UCD_TOKEN, environment, applicationName, componentName)
									//def urls = smokeEnvironmentUrl.getEnvironmentUrlFromUCD()
									//println "URLs for ${environment} for ${applicationName} and ${componentName} are: " + urls



						// based on the environment, author url will be queried from UCD

						if ("${body.environment}" == "qa"){
							author_url = "http://xjzxdep0101x.dta.kp.org:4502"
						}

						withCredentials([
																	[$class: 'UsernamePasswordMultiBinding', credentialsId: "FotFTestAutomation",
																			usernameVariable: 'BobCat_User', passwordVariable: 'BobCat_Password'
																	]
															]){
											 sh "${mvnHome}/bin/mvn clean test -f it.tests/bobcat/pom.xml -Dauthor.url='${author_url}' -Dwebdriver.url=http://xlzvbta0094x.lvdc.kp.org:4445/wd/hub -Dauthor.login='${BobCat_User}' -Dauthor.password='${BobCat_Password}' -X"
								}

		} catch (err) {
				currentBuild.result = 'FAILED'
				throw err
		}

}
