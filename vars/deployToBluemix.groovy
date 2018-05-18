/*

Sets Environments Variables:
1) BLUE_MIX_NAME : Blue Mix application name
2) BLUE_MIX_NAME_VERSIONED : Blue Mix application name along with version
3) BLUE_MIX_HOST : Host name as read from manifest.yml
4) BLUE_MIX_APP_DOMAIN : Blue Mix application domain. As seen by end user
5) BLUE_MIX_SERVICES :  Array of services as read from manifest.yml
*/

@Grab('org.yaml:snakeyaml:1.17')

import org.yaml.snakeyaml.Yaml
import groovy.json.JsonSlurper


def call(body) {
	// Runs only if it is not a whitehat triggered job not a PR build
	println "[INFO] Is this a PR Branch skipping the deploy to bluemix: ${env.IS_PR_BUILD}"
	if (checkIsWhitehatOrPR()){
	try {
                  def proceedDeploy = true
                  def appType = "blue"
                  // will be set for green build alone for deleting rollback apps
                  def originalRoute
                  def autoscalingFlag = false

									def environmentConfig
									if (body.configEnvironment && "${body.configEnvironment}" != "null")
										environmentConfig = body.configEnvironment
									else
										environmentConfig = body.space
									if (params.CONFIG_ENVIRONMENT)
										environmentConfig = params.CONFIG_ENVIRONMENT

            setEnv([stage: "DEPLOY", environment: "${environmentConfig}", credentialsID: "${body.credentialsID}", getArtifactsVersion: "true", useCustomLibertyPackage: "${body.useCustomLibertyPackage}"])

                  if (("${params.ENV_PROMOTE}").contains('promote')) {
                    script {
                            if (env.UPDATED_CONFIG_VERSION){
                              currentBuild.displayName = "${env.APP_VERSION}_${env.UPDATED_CONFIG_VERSION}_${env.BUILD_NUMBER}"
                            } else {
                              currentBuild.displayName = "${env.APP_VERSION}_${env.configVersion}_${env.BUILD_NUMBER}"
                            }

                          }
                    }

                  // check if we need an approval for the bluemix space
                  check_deploy_approval([space : "${body.space}", approvalGroupOverride: "${body.approvalGroupOverride}"])
                  downloadArtifact(body)
                  updateManifest(body)

                  withCredentials([
                        [$class: 'UsernamePasswordMultiBinding', credentialsId: "${body.credentialsID}",
                            usernameVariable: 'BX_USER', passwordVariable: 'BX_PASSWORD'
                        ]
                    ]) {

                              // loading shell scripts from resources
                              sh "ls -la ${WORKSPACE}"
                              loadShellScripts("bluemix_deploy.sh")
                              loadShellScripts("declared_service.sh")
                              loadShellScripts("userprovided_service.sh")
                              loadShellScripts("release_notification.yml")

                              if ( BX_USER && BX_PASSWORD && body.loginDomain && body.organization && body.space ){
                                  sh "cf login -u \"${BX_USER}\" -p \"${BX_PASSWORD}\" -a ${body.loginDomain} -o ${body.organization} -s ${body.space}"
                              } else {
                                  println "[ERROR] : Won't be attempting to login. As all login params were not available:: BX_USER: $BX_USER , BX_PASSWORD: $BX_PASSWORD, loginDomain: ${body.loginDomain}, organization: ${body.organization}, space: ${body.space}"
                                  error("Won't be attempting to login. As all login params were not available:: BX_USER: $BX_USER , BX_PASSWORD: $BX_PASSWORD, loginDomain: ${body.loginDomain}, organization: ${body.organization}, space: ${body.space}")
                              }

                              // decide on the type of deployment
                              if (body.deployType){
                                  println "[INFO] : Checking Deployment Type"
                                  def data = deployBlueGreen([
                                                      deployType: "${body.deployType}",
                                                      loginDomain: "${body.loginDomain}"
                                                  ])
                                  proceedDeploy = data['proceedDeploy']
                                  appType = data['appType']
                                  originalRoute = data['originalRoute']
                              }

                              println "Proceed with App Deploy to Bluemix? ${proceedDeploy}"
                              if ( proceedDeploy == true ){
                                // read the user provided services configuration and create a services if not present
                                createUserProvidedServices(body)

                                // read the declared services configuration and create a services if not present
                                autoscalingFlag = createDeclaredServices(body)

                                // calling shell script to push bluemix app
                                if ( env.BLUE_MIX_NAME && env.BLUE_MIX_NAME_VERSIONED && env.BLUE_MIX_HOST && env.BLUE_MIX_APP_DOMAIN_LIST && env.manifestfile && env.IS_PROD ){
                                  if("${env.buildType}" == 'maven') {
                                                            sh "bluemix_deploy.sh ${env.BLUE_MIX_NAME} ${env.BLUE_MIX_NAME_VERSIONED} ${env.BLUE_MIX_HOST} \"${env.BLUE_MIX_APP_DOMAIN_LIST}\" ${body.splunkPort} ${env.manifestfile} '${env.BLUE_MIX_SERVICES}' '${WORKSPACE}/package/${env.APP_ARTIFACT_PATH.tokenize('/')[-1]}' ${env.IS_PROD} ${appType} ${originalRoute} ${autoscalingFlag} ${env.BUILD_PACK}"
                                                          }
                                                          else {
                                                            sh "bluemix_deploy.sh ${env.BLUE_MIX_NAME} ${env.BLUE_MIX_NAME_VERSIONED} ${env.BLUE_MIX_HOST} \"${env.BLUE_MIX_APP_DOMAIN_LIST}\" ${body.splunkPort} ${env.manifestfile} '${env.BLUE_MIX_SERVICES}' '${WORKSPACE}/package' ${env.IS_PROD} ${appType} ${originalRoute} ${autoscalingFlag} ${env.BUILD_PACK}"
                                                          }

																													println "env.cloudantServiceExisted: ${env.cloudantServiceExisted}"
																													//create cloudant database
																													if("${env.cloudantServiceExisted}" == 'false' && body.cloudantDatabase) {
												                                  	createCloudantDatabase(body)
												                                  }
                                } else {
                                    println "[ERROR] : ******** All params are not set for calling shell script ******** "
                                    println "env.BLUE_MIX_NAME : ${env.BLUE_MIX_NAME}, env.BLUE_MIX_NAME_VERSIONED : ${env.BLUE_MIX_NAME_VERSIONED}, env.BLUE_MIX_HOST : ${env.BLUE_MIX_HOST}"
                                    println "env.BLUE_MIX_APP_DOMAIN : ${env.BLUE_MIX_APP_DOMAIN}, env.manifestfile : ${env.manifestfile}, env.IS_PROD : ${env.IS_PROD}"
                                    error("******** All params are not set for calling shell script ******** ")
                                }

                                sh "cf logout"

                                // Send notification to Release Management channel
                                sendReleaseNotification(body)

                                println "[INFO] : checking out test data"
                                println "[INFO] : smokeTestVer : ${body.smokeTestVer} , testSuiteXml: ${body.testSuiteXml}, testDatarepoGitHttpURL : ${body.testDatarepoGitHttpURL}"

                                if ( (body.testSuiteXml && body.testSuiteXml != "null") && (body.testDatarepoGitHttpURL && body.testDatarepoGitHttpURL != "null") && (body.smokeTestEnv && body.smokeTestEnv != "null") ){

                                    // Retrieving the reponame from repo URL
                                    def testDataRepoName = "${body.testDatarepoGitHttpURL}".split('/').last().tokenize(".")[0]
                                    checkoutSCM ([branch: "master" ,
                                              testDatarepoGitHttpURL: "${body.testDatarepoGitHttpURL}",
                                              credentialsID: "${body.credentialsID}",
                                              directoryPath: "$WORKSPACE/${testDataRepoName}" ])

                                      soapUITest ([environment:  "${body.smokeTestEnv}",
                                                      testSuiteXml: "${body.testSuiteXml}",
                                                      testDatarepoGitHttpURL: "${body.testDatarepoGitHttpURL}"])
                                  } else {
                                    println "*********************************************************"
                                    println "           Skipping soapUITest Test"
                                    println "           testSuiteXml : ${body.testSuiteXml}"
                                    println "           testDatarepoGitHttpURL : ${body.testDatarepoGitHttpURL}"
                                    println "           smokeTestEnv : ${body.smokeTestEnv}"
                                    println "                   *********************************************************"
                                  }
                              }

                        }
                  } catch (err) {
			error ("Received error in the process of deployment and smoketests ${err}")
		  }
	}
}
// updates required in manifest yml before app publish to bluemix
def updateManifest(body){

          try {
                  println "[INFO] : Updating the manifest.yml file before cf push....${env.manifestfile}"
                  // check if the file exists
                  if (fileExists("${env.manifestfile}")) {
                      def name
                      def host
                      def writeString
                      def dashedVersion = "${env.APP_VERSION}".replace(".","-")
                      String contents = readFile file: "${env.manifestfile}", encoding: "UTF-8"
                      String modified = ""
                      // removing any versioning that might have been added by dev

                      sh "cat ${env.manifestfile}"
                      String file = readFile("${env.manifestfile}")
                      Yaml parser = new Yaml()
                      Map ymap = (Map) parser.load("${file}")
                      // replacing unwanted brackets and appending version in format 0-0-0
                      name="${ymap['applications']['name']}".replaceFirst(/(-\d).*/,"").replace(/[/,"").replace(/]/,"") + "-" + "${env.APP_VERSION}"
                      ymap['applications'][0]['name']=  name

                      // dumping the map onto file
                      Yaml yaml = new Yaml()
                      def output = yaml.dump(ymap)
                      println "${output}"
                      // // setting them to null to handle serializable exceptions
                      yaml=null; ymap=null; parser=null

                      // writting the string yaml to manifest file
                      writeFile file: "${env.manifestfile}", text: "${output}", encoding: "UTF-8"

                      if ("${body.replaceTokens}"=='true') {
                        println "replaceTokens = $body.replaceTokens"
                        updateTokenManifest(body)
                      }


            } else {
                println "[ERROR] : No manifest.yml found in this path ${env.manifestfile}"
                error("No manifest.yml found in this path ${env.manifestfile}")
            }
          } catch (err){
                println "[ERROR] : Error while updating build number in  manifest.yml found in this path ${env.manifestfile}" + "${err.getStackTrace()}"
                error("Error while updating build number in  manifest.yml found in this path ${env.manifestfile}" + "${err.getStackTrace()}")
	}
}

// read manifest.yml and create declared services
def createDeclaredServices(body){

  def autoscaling = false
	env.cloudantServiceExisted = false
	def cloudantLabel = 'cloudantNoSQLDB Dedicated'
	if (body.cloudantLabel && body.cloudantLabel.trim() != 'cloudantNoSQLDB Dedicated') {
		cloudantLabel = "${body.cloudantLabel.trim()}"
	}

  try {
      println "[INFO] : Check for Declared Services from ${env.manifestfile}"

      if (fileExists("${env.manifestfile}")) {

            String file = readFile("${env.manifestfile}")
            Yaml parser = new Yaml()
            Map ymap = (Map) parser.load("${file}")
            def declaredServices = ymap['applications']['declared-services']
            def servicesFromManifest = ymap['applications']['services'][0]
            ymap=null
            parser=null
            if (declaredServices){
                      declaredServices.each{ itService ->
                          println itService.getClass()
                          println itService
                          itService.each{ key ->
                            println key.getClass()
                            println "key is " + key

                          for (Map.Entry<String, ArrayList<String>> entry : key.entrySet()) {
                              println "entry.getKey = ${entry.getKey()}" + " " + "entry.getValue.lable is ${entry.getValue()['label']}" + " " + "entry.getValue.plan is ${entry.getValue()['plan']}"

                              println "${entry.getKey()} size is " + entry.getKey().size()
                              if ( entry.getKey().size() > 50){
                                println " ERROR: Service name must be less than 50 characters, Please verify"
                                error(" Service name must be less than 50 characters, Please verify")
                              }
                              if (entry.getKey().startsWith('autoscaling_') || entry.getKey().startsWith('as_'))
                                  autoscaling = true
                              println "env.autoscalingFlag" + env.autoscalingFlag
                              def customName = '"'+"${entry.getKey()}"+'"'
                              def serviceName = '"' +"${entry.getValue()['label']}"+'"'
                              def plan = '"'+"${entry.getValue()['plan']}"+'"'
                                  // creating declared Services
                                  if ( customName && serviceName && plan ) {
                                      def output = sh (returnStdout: true, script: "declared_service.sh ${serviceName} ${plan} ${customName}").trim()

																			//if cloudant service has already existed, then set env.cloudantServiceExisted to true and it will not craete cloudant database and views
																			def declaredServiceLabel = "${entry.getValue()['label']}"
																			def declaredServiceName = "${entry.getKey()}"
																			def declaredServiceExistedEcho = "Declared serivce: ${declaredServiceName} has already existed."

																			if (output && declaredServiceLabel.trim() == cloudantLabel && output.contains("${declaredServiceExistedEcho}")) {
																					env.cloudantServiceExisted = true
																			}
                                  } else {
                                      println "[ERROR] : ******** All params are not set for calling shell script ******** "
                                      error("******** All params are not set for calling shell script ******** ")
                                  }
                            }

                          }
                      }
            } else {
                    println "******** No declared services found. Skipping declared services creation step ***********"
            }

      } else {
            println "[ERROR] : No manifest.yml found in this path ${env.manifestfile}"
            error("No manifest.yml found in this path ${env.manifestfile}")
      }
    } catch(err){
      println "[ERROR] : Error while creating declared services from  manifest.yml found in this path ${env.manifestfile}" + "${err.getStackTrace()}"
      error("Error while creating declared services from  manifest.yml found in this path ${env.manifestfile}" + "${err.getStackTrace()}")
    }
    return autoscaling
}

// read manifest.yml and create user provided services
def createUserProvidedServices(body){
  try{
      println "Check for UserProvided Services from ${env.manifestfile}"
      if (fileExists("${env.manifestfile}")) {

            String file = readFile("${env.manifestfile}")
            Yaml parser = new Yaml()
            Map ymap = (Map) parser.load("${file}")
            //println "ymap is ${ymap}"
            def userProvidedServices = ymap['applications']['user-provided-services']
            println "userProvidedServices = ${userProvidedServices}"
            ymap=null
            parser=null
            if (userProvidedServices) {
                  userProvidedServices.each { it->
                      println it.getClass()
                      println it
                      it.each { key ->
                        println key.getClass()
                        println key

                        for (Map.Entry<String, ArrayList<String>> entry : key.entrySet()) {
                              println "entry is ${entry} , key.entrySet is ${key.entrySet()} , entry.getValue is ${entry.getValue()} , entry.getKey is ${entry.getKey()}"
                              println "${entry.getKey()} size is " + entry.getKey().size()
                              if ( entry.getKey().size() > 50){
                                  println " ERROR: Service name must be less than 50 characters, Please verify"
                                error(" Service name must be less than 50 characters, Please verify")
                              }
                              println entry.getValue().find{ it.key == "secret-json-id"}
                              def isSecret
                              if (entry.getValue()['secret-json-id']) {
                                    isSecret = true
                                    println "Service details to be read from  Jenkins Secret : ${entry.getKey()}"
                                    withCredentials([string(credentialsId: "${entry.getValue()['secret-json-id']}", variable: 'jsonText')]) {
                                      if (entry.getKey() && jsonText) {
                                          println "entry.getkey is ${entry.getKey()}"
                                          println "jsonText is " + jsonText
                                          sh "userprovided_service.sh ${entry.getKey()} '${jsonText}' ${isSecret}"
                                      } else {
                                          println "[ERROR] : ******** All params are not set for calling shell script ******** "
                                          error("******** All params are not set for calling shell script ******** ")
                                        }

                                    }
                              } else if ( entry.getKey().startsWith('slog_') && entry.getValue()['port']) {
                                    isSecret = false
                                    println " Found splunk port : " + entry.getValue()['port']
                                    println "env.IS_PROD is ${env.IS_PROD}"
                                    def splunkURL
                                    if ("${env.IS_PROD}" == 'true'){
                                      splunkURL = "syslog://172.27.230.58:" + entry.getValue()['port']
                                      println " splunkURL is ${splunkURL}"
                                    }
                                    else {
                                      splunkURL = "syslog://172.16.74.78:" + entry.getValue()['port']
                                      println " splunkURL is ${splunkURL}"
                                    }
                                    sh "userprovided_service.sh ${entry.getKey()} '${splunkURL}' ${isSecret}"
                              } else if (entry.getValue()['json-text']){
                                  println "Service details to be read from ${env.manifestfile} : ${entry.getKey()}"
                                  // TODO : construct json string for cups
                              } else if (entry.getValue()['json-file']){
                                  println "Service details to be read from file system : ${entry.getKey()}"
                                  // TODO : passing in json file path for cups
                              } else {
                                  println "[ERROR] : Unknown Key: ${entry.getKey()}"
                                  error("Unknown format encountered while reading service details from ${env.manifestfile}")
                              }
                          }
                      }
              }
          }
      }
          else {
                println "[ERROR] : No manifest.yml found in this path ${env.manifestfile}"
                error("No manifest.yml found in this path ${env.manifestfile}")
          }
    }
     catch(err){
      println "[ERROR] : Error while creating user user provided services from  manifest.yml found in this path ${env.manifestfile}" + "${err.getStackTrace()}"
      error("Error while creating user user provided services from  manifest.yml found in this path ${env.manifestfile}" + "${err.getStackTrace()}")
}
}

// Send notification to Release Management Channel
def sendReleaseNotification(body){
  try {
        String file = readFile("release_notification.yml")
        Yaml parser = new Yaml()
        Map ymap = (Map) parser.load("${file}")
        def projects = ymap['folder_name']
        def environments = ymap['environments']
        def hipchatRoom = ymap['channels'][0]['hipchat']
        def email = ymap['channels'][1]['email']

        parser = null; ymap= null;

        if (projects && environments) {
            projects.each { project->
              environments.each { environment->
                if(("${JOB_URL}".contains("${project}")) && ("${body.space}" == "${environment}")){
                  notify([hipchat_room: "${hipchatRoom}",
                      email: "${email}", //Optional
                      build_status: "${currentBuild.currentResult}",
                      message: "Deployed BlueMix App ${BLUE_MIX_NAME_VERSIONED} to ${body.space} Space"])
					}
				}
			}
		}
  } catch(err){
        println "[ERROR] : Error while parsing deploy_notification.yml" + "${err.getStackTrace()}"
        error("[ERROR] : Error while parsing deploy_notification.yml" + "${err.getStackTrace()}")
    }

}

// check if the space needs approval
def check_deploy_approval(body){
  try {
        println "[INFO] : Checking if Deployment to ${body.space} needs Approval"
        def env_group_name= null
        def project_approval_group= null
        switch(body.space.toLowerCase()){
          case ["prod-sj","prod-dal"]:
              env_group_name="PROD"
              break;
          case ["qa","uat"]:
              env_group_name="QA"
              break;
          case ["preprod","perf"]:
              env_group_name="PREPROD"
              break;
        }
        if (env_group_name != null){
            println "[INFO] : Determined that Deployment to ${body.space} needs Approval"
            // determining the jenkins pipeline name
            project_approval_group = sh(returnStdout: true, script: 'echo \"${JOB_NAME}\" | cut -f1 -d/')
            project_approval_group = project_approval_group.trim()
            if ( "${project_approval_group}" == "" || project_approval_group == null ){
              println "[ERROR] : Error while reading pipeline name using JOB_NAME."
              error("[ERROR] : Error while reading pipeline name using JOB_NAME.")
            }
            println "[INFO] : Determined the Project Approval group to: ${project_approval_group}"
            println "[INFO] : Determined the Override Approval group from Jenkinsfile to : ${body.approvalGroupOverride}"
            println "[INFO] : Requesting approval for env group : ${env_group_name}"
            approve([environment: "${env_group_name}",approvalGroupOverride: "${body.approvalGroupOverride}",projectApprovalGroup: "${project_approval_group}"])
        }
        env_group_name=null
        project_approval_group=null

  } catch(err){
    println "[ERROR] : Error while verifying Approval before deployment to ${body.space}" + "${err.getStackTrace()}"
    error("[ERROR] : Error while verifying Approval before deployment to ${body.space}" + "${err.getStackTrace()}")
	}
}

def updateTokenManifest(body){
  try {
        withCredentials([string(credentialsId: "${body.token}", variable: 'tokenValue')]) {
            String mfile = readFile("${env.manifestfile}")
            Yaml mparser = new Yaml()
            Map mymap = (Map) mparser.load("${mfile}")
            def jsonSlurper = new JsonSlurper()
            def object = jsonSlurper.parseText(tokenValue)
            object.each{ tokenid , tokendata ->
                  mymap['applications'][ 0 ][ 'env' ][ "${tokenid}" ] = new String("${tokendata}")
                       }
            try {
            def mdomain = (mymap['applications'][ 0 ][ 'domains' ]!= null)? mymap['applications'][ 0 ][ 'domains' ][0] : mymap['applications'][ 0 ][ 'domain' ]
            def mhost   = (mymap['applications'][ 0 ][ 'hosts' ] !=null)? mymap['applications'][ 0 ][ 'hosts' ][0] :mymap['applications'][ 0 ][ 'host' ]
            if (mhost!= null && mdomain!= null ) {
            mymap['applications'][ 0 ][ 'env' ][ 'HOSTNAME' ]  = new String ("$mhost" +"."+"$mdomain" )
            println  "HOSTNAME =" + mymap['applications'][ 0 ][ 'env' ][ 'HOSTNAME' ]
                }
                }
                catch(err){
              println "[ERROR] : Error while updating tokens in manifest file: HOSTNAME" + "${err.getStackTrace()}"
              error("[ERROR] : Error while updating tokens in manifest file: HOSTNAME" + "${err.getStackTrace()}")
                        }
            Yaml myaml = new Yaml()
            def moutput = myaml.dump(mymap)
            //println  "${moutput}"
            myaml=null; mymap=null; mparser=null; object=null; jsonSlurper=null ;tokenid=null ; tokendata=null;
            mdomain =null;mhost=null
            writeFile file: "${env.manifestfile}", text: "${moutput}", encoding: "UTF-8"
            println "############ manifestfile token update complete ############"
		}
	}catch(err){
        println "[ERROR] : Error while updating manifest file tokens" + "${err.getStackTrace()}"
        error("[ERROR] : Error while updating manifest file tokens" + "${err.getStackTrace()}")
    }

}
