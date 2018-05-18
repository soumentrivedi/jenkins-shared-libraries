#!/usr/bin/groovy

/*
Description:

Fetch artifacts from artifactory based on the version details read from dependency manifests. Should be able handle both Maven and NPM based projects.
Read pom.xml / package.json to determine artifact version and artifactory URL, then set them as environment variables for downstream access.

Environment Variables:
1) APP_VERSION
2) APP_ARTIFACT_PATH
3) APP_TEST_SUITE_ARTIFACT_PATH

*/
@Grab(group = 'org.codehaus.groovy.modules.http-builder', module = 'http-builder', version = '0.7.1')
import groovyx.net.http.RESTClient
import groovy.json.JsonSlurper

def call(config){

	try{
		  def componentVersion = [:]

		  if ("${env.buildType}" == "npm" || "${env.buildType}" == "yarn") {
			  componentVersion = buildNodeComponentVersion()
		  } else if ("${env.buildType}" == "maven") {
			  componentVersion = buildMavenComponentVersion(config)
		  }


		  name = componentVersion["name"]
		  appname = componentVersion["appname"]
		  appArtifactPath = componentVersion["appArtifactPath"]
		  testSuiteArtifactPath = componentVersion["testSuiteArtifactPath"]
		  env.codeRepoType = componentVersion["codeRepoType"]
		  env.isLibertyPlugin = componentVersion["isLibertyPlugin"]
		  env.apicFullArtifactPath = componentVersion["apicArtifactPath"]

		  if ("${appArtifactPath}" == null) throw new Exception('Unable to create correct version appArtifactPath')

		  println "[INFO] : Version: " + "${name}"
		  println "[INFO] : App Name: " + "${appname}"
		  println "[INFO] : appArtifactPath: " + "${appArtifactPath}"
		  println "[INFO] : apicFullArtifactPath: " + "${env.apicFullArtifactPath}"
		  println "[INFO] : testSuiteArtifactPath: " + "${testSuiteArtifactPath}"


		  // Setting environment variables
		  env.APP_VERSION = "${name}"
		  env.APP_NAME = "${appname}"
		  env.APP_ARTIFACT_PATH = "${appArtifactPath}"
		  env.APP_TEST_SUITE_ARTIFACT_PATH = "${testSuiteArtifactPath}"

	} catch (err){
		println "[ERROR] : Failed while getting artifact deatils by parsing pom.xml / package.json. ${err.getStackTrace()}"
		error("Failed while getting artifact deatils by parsing pom.xml / package.json")
	}
}



	  /* Read artifact details from pom.xml */
	  def buildMavenComponentVersion(config) {

		  println "[INFO] Determining artifactory for Maven Project"
		  def componentVersion = [:]
		  def INHOUSE_SNAPSHOT = 'inhouse_snapshot'
		  def INHOUSE_RELEASE = 'inhouse_release'
		  def file
		  def response
		  def apicResponse
		  file = readFile("pom.xml")
		  def parsedPom = new XmlParser().parseText(file)
		  def pomVersion = parsedPom.version[0].text()
		  def snapshot = pomVersion.findAll('SNAPSHOT')
		  def codeRepoType = snapshot.size > 0 ? INHOUSE_SNAPSHOT : INHOUSE_RELEASE
		  def groupId = parsedPom.groupId[0].text()
		  def appArtifactId = parsedPom.artifactId[0].text()
		  def artifactFileExtension = '.jar'


		  def parentPomVersion = null
		  def appArtifactName = null
		  def apicVersionPath = null
		  def fullArtifactPath = null
		  def apicArtifactName = null

		  Boolean hasTestSuite = false
		  def testSuiteArtifactName = null
		  def testSuiteVersionPath = null
		  def fullTestSuiteArtifactPath = null
		  def apicFullArtifactPath = null
		  def testSuiteArtifactExtension = '.zip'
		  def testModuleName
		  def buildNumber
		  def timeStamp
		  def apicbuildNumber
		  def apictimeStamp
		  Boolean isLibertyPlugin = false

		  apicArtifactName = new XmlParser().parseText(file).artifactId[0].text()
		  apicVersionPath = "${ARTIFACTORY_URL}/${codeRepoType}/${groupId.replace(".", "/")}/${apicArtifactName}/${pomVersion}/"

		  def groupIdList = [
				  'wpp.is.ws',
				'org.kp.wpp',
				'org.kp.dss.is',
				'org.kp.rx.services',
				'org.kp.uds.services',
				'org.kp.userinteractionlog.services',
				'org.kp.addressadapter.services'
				]

		  def configGroupIdList = [
				'org.kp.wpp.config',
				'org.kp.dss.is.config'
				]

		  if (groupIdList.contains(groupId)) {
			  parentPomVersion = pomVersion
			  def modulesNames = []

			  parsedPom.modules[0].each {
				  modulesNames << it.value()[0]
			  }

				if (modulesNames) {
				  modulesNames.each {
					  if (it.contains('test-suite')) {
						  hasTestSuite = true
						  testModuleName = 'test-suite'
						  appArtifactName = new XmlParser().parseText(file).artifactId[0].text()
					  }
					  if (it.contains('war') || it.contains('wlp') || it.contains('rxadapter')) {
						  file = readFile("${it}/pom.xml")
						  appArtifactName = new XmlParser().parseText(file).artifactId[0].text()

						  if (new XmlParser().parseText(file).build.plugins.plugin){
								new XmlParser().parseText(file).build.plugins.plugin.each{ plugin->
								if (plugin['artifactId'][0].value()[0] == "liberty-maven-plugin") {
								  isLibertyPlugin=true
								}
									}

							// if we have liberty-maven-plugin installed extension needs to be .war
							if (isLibertyPlugin || "${config.useCustomLibertyPackage}" == "true") {
								println "[INFO] : Liberty plugin detected, setting artifact extension to .zip"
								artifactFileExtension = '.zip'
							} else {
								println "[INFO] : Liberty plugin NOT detected, setting artifact extension to .war"
								artifactFileExtension = '.war'
							}
						  } else {
							  println "[INFO] : Checked for Liberty plugin in this path: build.plugins.plugin and path not found in pom"
							  println "[INFO] : Defaulting to file extension as : .zip"
							  artifactFileExtension = '.zip'
						  }
					  }
				  }
				} else {
					//if pom doesn't contain modules, then we assume that it's a libary project and only has parent pom
					println "[INFO] : No modules found in the pom file....Assume that it's a java libary project."
					parentPomVersion = pomVersion
					appArtifactName = appArtifactId
					artifactFileExtension = '.jar'
				}
			} else if (configGroupIdList.contains(groupId)) {
			  parentPomVersion = pomVersion
			  appArtifactName = appArtifactId
			  artifactFileExtension = '.zip'
		  } else if (groupId == 'org.kp.mobile.bff') {
			  parentPomVersion = pomVersion
			  appArtifactName = appArtifactId
			  artifactFileExtension = '.war'
		  } else {
			// default case when doesn't fall in above co-ordinates
			println("[INFO] : Group ID doesn't match existing ones. Taking default values")
			parentPomVersion = pomVersion
			appArtifactName = appArtifactId
			artifactFileExtension = '.zip'
		  }

		  def appVersionPath = "${ARTIFACTORY_URL}/${codeRepoType}/${groupId.replace(".", "/")}/${appArtifactName}/${pomVersion}/"
		  println("[INFO] : printing out App artifact name" + "${appArtifactName}")
		  if (hasTestSuite) {
			  file = readFile("${testModuleName}/pom.xml")
			  parsedPom = new XmlParser().parseText(file)
			  testSuiteArtifactName = parsedPom.artifactId[0].text()
			  testSuiteVersionPath = "${ARTIFACTORY_URL}/${codeRepoType}/${groupId.replace(".", "/")}/${testSuiteArtifactName}/${pomVersion}/"
		  }

		  if (hasTestSuite) println(testSuiteVersionPath)

		  if (codeRepoType == INHOUSE_SNAPSHOT) {
			  if (params.ARTIFACT_VERSION){
				 // deploy the specifc build number mentioned in build params
				  println "[INFO] : Artifact Version was provided. Will use specific build number :: ${params.ARTIFACT_VERSION}"
				  def data = [:]
				  data['artifactory_url'] = "${appVersionPath}"
				  data['extension'] = "${artifactFileExtension}"
				  data['apicArtifactExtension'] = ".zip"
				  data['buildnum'] = "${params.ARTIFACT_VERSION}".tokenize("-")[-1]

				  appArtifactName = artifactByBuildNum(data)
				  data['artifactory_url'] = "${apicVersionPath}"
				  apicArtifactName = apicArtifactByBuildNum(data)
				  fullArtifactPath = appVersionPath + appArtifactName
				  apicFullArtifactPath = apicVersionPath + apicArtifactName


			  } else {
						println "[INFO] : Artifact Version was not provided. Will determine latest SNAPSHOT from maven-metadata.xml"
						// evaluate the latest by parsing maven-metadata.xml
						def client = new RESTClient(appVersionPath)
						def apicClient = new RESTClient(apicVersionPath)
						try {
							// println versionPath
							response = client.get(path: "maven-metadata.xml")
							buildNumber = response.data.versioning.snapshot.buildNumber
							timeStamp = response.data.versioning.snapshot.timestamp


						} catch (err) {
							println "[INFO] : Cannot access url ${appVersionPath}/maven-metadata.xml. Because this the First artifactory upload."
							if ("${config.stage}" == "DEPLOY"){
								error("[ERROR] : Cannot access url ${appVersionPath}/maven-metadata.xml . Please verify that it exists")
							}
						}

						try {
							  apicResponse = apicClient.get(path: "maven-metadata.xml")
							  apicbuildNumber = apicResponse.data.versioning.snapshot.buildNumber
							  apictimeStamp = apicResponse.data.versioning.snapshot.timestamp
						} catch (err){
							  println "[INFO] : Cannot access APIC Artifact url ${appVersionPath}/maven-metadata.xml. Because this the First artifactory upload."
						}

						fullArtifactPath = appVersionPath + appArtifactName + "-" + pomVersion.substring(0, pomVersion.length() - 9) +
								"-" + timeStamp + "-" + buildNumber + artifactFileExtension

						apicFullArtifactPath = apicVersionPath + apicArtifactName + "-" + pomVersion.substring(0, pomVersion.length() - 9) +
								"-" + apictimeStamp + "-" + apicbuildNumber + ".zip"

						parentPomVersion = parentPomVersion + "-" + buildNumber
						println "[INFO] : Artifactory URL computed for project after parsing pom.xml ${fullArtifactPath}"
						println "[INFO] : Artifactory URL computed for APIC after parsing pom.xml ${apicFullArtifactPath}"
			  }

			  if (hasTestSuite) {
				  client = new RESTClient(testSuiteVersionPath)
				  try {
					  // println versionPath
					  response = client.get(path: "maven-metadata.xml")
					  buildNumber = response.data.versioning.snapshot.buildNumber
					  timeStamp = response.data.versioning.snapshot.timestamp
				  } catch (Exception err) {
					  println "[INFO] : Cannot access url ${testSuiteVersionPath}/maven-metadata.xml. Because this the First artifactory upload."
					  if ("${config.stage}" == "DEPLOY"){
						error("Cannot access url ${testSuiteVersionPath}/maven-metadata.xml . Please verify that " +
								"it exists")
					  }
				  }

				  fullTestSuiteArtifactPath = testSuiteVersionPath + testSuiteArtifactName + '-' + pomVersion.substring(0, pomVersion.length() - 9) +
						  '-' + timeStamp + '-' + buildNumber + testSuiteArtifactExtension
			  }
		  } else {
			  fullArtifactPath = appVersionPath + appArtifactName + '-' + pomVersion + artifactFileExtension
				apicFullArtifactPath = apicVersionPath + apicArtifactName + "-" + pomVersion + ".zip"

				println "[INFO] : Artifactory URL computed after parsing pom.xml ${fullArtifactPath}"
				println "[INFO] : Artifactory URL computed for APIC after parsing pom.xml ${apicFullArtifactPath}"

			  if (hasTestSuite)
				  fullTestSuiteArtifactPath = testSuiteVersionPath + testSuiteArtifactName + '-' + pomVersion + testSuiteArtifactExtension
		  }

		  componentVersion["name"] = parentPomVersion
		  componentVersion["appArtifactPath"] = fullArtifactPath
		  if (fullTestSuiteArtifactPath) componentVersion.testSuiteArtifactPath = fullTestSuiteArtifactPath
		  componentVersion["codeRepoType"] = codeRepoType
		  componentVersion["isLibertyPlugin"] = isLibertyPlugin
		  componentVersion["apicArtifactPath"] = apicFullArtifactPath
		  client = null //sets REST object client to null after completion
		  apicClient = null
		  response = null
		  apicResponse = null
		  return componentVersion
	  }

	  /* curl to get specific build number*/
	  def artifactByBuildNum(data){

		  def build_artifact = sh (returnStdout: true, script: "curl -s ${data['artifactory_url']} | grep -m 1 ${data['buildnum']}${data['extension']} | cut -d'>' -f2 | cut -d'<' -f1").trim()
		  return "${build_artifact}"
	  }

	  /* curl to get specific build number*/
	  def apicArtifactByBuildNum(data){

		  def build_artifact = sh (returnStdout: true, script: "curl -s ${data['artifactory_url']} | grep -m 1 ${data['buildnum']}${data['apicArtifactExtension']} | cut -d'>' -f2 | cut -d'<' -f1").trim()
		  return "${build_artifact}"
	  }


	  /* Read artifact details from package.json */
	  def buildNodeComponentVersion() {
		  println "[INFO] Determining artifactory for Node project"

		  def componentVersion = [:]
		  def file = readFile("package.json")
		  def parsedJSON = new JsonSlurper().parseText(file)
		  def name = parsedJSON.name
		  def version = parsedJSON.version
		  // when specific version needs to be deployed, override the version read from POM / package.json
		  if ( params.ARTIFACT_VERSION ){
			  echo "Specific Version will be deployed : ${params.ARTIFACT_VERSION}"
			  version = params.ARTIFACT_VERSION
		  }
		  componentVersion["appArtifactPath"] = "${ARTIFACTORY_URL}/npm-release/${name}/-/${name}-${version}.tgz"
			// same as the app artifact, since for node builds we only have one package deployed to artifactory
			componentVersion["testSuiteArtifactPath"] = "${ARTIFACTORY_URL}/npm-release/${name}/-/${name}-${version}.tgz"
		  componentVersion["name"] = version
		  // this will be used for apic updates
		  componentVersion["appname"] = name
		  return componentVersion
	  }
