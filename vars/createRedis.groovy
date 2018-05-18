/*
Description:

Creaet redis service based on input specified in build parameters.

createRedis([
            appName: "${params.CONSUMER_APP_NAME}",
            bxDomain: "${params.DEPLOY_DOMAIN}",
            bxCredentialsID: "${params.DEPLOY_CREDENTIALS_ID}",
            bxOrg: "${params.DEPLOY_ORG}",
            bxSpace: "${params.DEPLOY_SPACE}",
            env: "${params.DEPLOY_ENV}", //optional
            datacenter: "${params.DEPLOY_DATACENTER}",
            composeTokenID: "${params.COMPOSE_TOKEN_ID}", //compose api token for the specified space
            redisID: "${params.DEPLOY_REDIS_ID}",
            units: "${params.DEPLOY_UNITS}",
            configServiceID: "${params.CONFIG_SERVICE_ID}",
            bitbucketCredentialsID: "${params.BITBUCKET_CREDENTIALS_ID}" //bitbucket credentials for fabric registry
          ])

Note: this script/pipeline is currently intended for enterprise data caching service team use cases only.
Reference: https://confluence-fof.appl.kp.org/display/SVC/Redis+Deployment+Automation

Please contact FOFT if you want to have similar pipeline for your project.
*/

@Grab(group = 'org.codehaus.groovy.modules.http-builder', module = 'http-builder', version = '0.7.1')

import groovyx.net.http.RESTClient
import groovy.json.JsonSlurperClassic
import groovy.json.JsonBuilder
import static groovyx.net.http.ContentType.JSON

def call(body) {

	try {

					verifyInput(body)

					trimInputParms(body)

					//bluemix details
					def datacenter = body.datacenter

					//compose details
					compose_api_url = 'https://api.compose.io/2016-07/'
					compose_api_token = getComposeApiToken(body)

					//config service encrytion details
					configServiceMap = getConfigServiceDetails(body)
					def configServicePwdEncryptUrl = configServiceMap.url
					def configSericeUsername = configServiceMap.username
					def configSericePassword = configServiceMap.password
					def configServiceAccessToken = configServiceMap.token

					//bitbucket details
					verifyBitbucketCredentials(body)
					bitbucketUrl = "bitbucket-fof.appl.kp.org/scm/dcp/dcp-configs-v2.git"
					bitbucketCredentialsId = body.bitbucketCredentialsID
					branch = "master"
					repo = "dcp-configs-v2"
					folder = "cache-fabric-registry-v2"
					file = ""
					profile = ""
					if (body.env) {
						file = "dcpconfig-cache-fabric-registry-v2-${body.env}.json"
						profile = "cache-fabric-registry-v2-${body.env}"
					} else {
						file = "dcpconfig-cache-fabric-registry-v2-${body.bxSpace}.json"
						profile = "cache-fabric-registry-v2-${body.bxSpace}"
					}

					//redis config
					def redisServiceName = body.redisID
					def redisServiceKey = "${redisServiceName}-service-key"
					def consumerAppName = body.appName
					def units = body.units as int

					loginBluemix(body)
					createRedisService(redisServiceName)
					createRedisServiceKey(redisServiceName, redisServiceKey)
					def deploymentId = getRedisDeploymentId(redisServiceName, redisServiceKey)
					createRedisSecureConnection(compose_api_url, compose_api_token, deploymentId)
					updateRedisScaling(compose_api_url, compose_api_token, deploymentId, units)

					def connectionDetails = getRedisConnectionDetails(compose_api_url, compose_api_token, deploymentId)

					if (connectionDetails.ca_certificate_base64 && connectionDetails.connection_strings && connectionDetails.connection_strings.direct) {

						def cert = new String("${connectionDetails.ca_certificate_base64}".decodeBase64())
						def username
						def password
						def baseUrl

						def direct = connectionDetails.connection_strings.direct
						direct.each { ep ->
								if (ep.startsWith("rediss")) {

									username = "admin"
									password = ep.split("//")[1].split(":")[1].split("@")[0]
									baseUrl = ep.split("@")[1]

									patterns = [[patternKey: "${redisServiceName}-cert", patternValue:"${cert}"], [patternKey: "${redisServiceName}-password", patternValue:"${password}"]]

									encryptRedisCredential(configServicePwdEncryptUrl, configSericeUsername, configSericePassword, configServiceAccessToken, profile, patterns)

									cacheFabricConfigKey = "${redisServiceName}_Redis_Map"
									cachePolicyFabricMapRegistry = [epLookupKey: "", datacenter: ["${datacenter}": [cacheFabric:[cacheFabricId: "Redis", cacheFabricConfigId: "${cacheFabricConfigKey}"]]]]
									cacheFabricConfigDetails = [[key: "BASE_URI", value: "${baseUrl}"], [key: "USER", value: "admin"], [key: "PASSWORD", value: "{{${redisServiceName}-password}}"], [key: "CERT", value: "{{${redisServiceName}-cert}}"]]
									cacheFabricConfig = [cacheFabricConfigId: cacheFabricConfigKey, available: true, cacheFabricConfigDetails: cacheFabricConfigDetails]

									updateFabricRegistry(bitbucketUrl, bitbucketCredentialsId, branch, repo, folder, file, consumerAppName, cachePolicyFabricMapRegistry, cacheFabricConfigKey, cacheFabricConfig)
								}
						}
					}

					sh "cf logout"

	} catch(err){
				println "[ERROR] : Error while creating redis instance: " + "${err.getStackTrace()}"
				error("[ERROR] : Error while creating redis instance: " + "${err.getStackTrace()}")
	}

}

def verifyInput(body) {
	if(!body || !body.appName || !body.bxDomain || !body.bxCredentialsID || !body.bxOrg || !body.bxSpace || !body.datacenter || !body.composeTokenID || !body.redisID || !body.units || !body.configServiceID || !body.bitbucketCredentialsID) {
		println "[ERROR] : Some required parameters are not supplied. Please check all parameters are filled properly."
		throw error
	}
}

def trimInputParms(body) {

	body.appName = body.appName.trim()
	body.bxDomain = body.bxDomain.trim()
	body.bxCredentialsID = body.bxCredentialsID.trim()
	body.bxOrg = body.bxOrg.trim()
	body.bxSpace = body.bxSpace.trim()
	if (body.env) {
		body.env = body.env.trim()
	}
	body.datacenter = body.datacenter.trim()
	body.composeTokenID = body.composeTokenID.trim()
	body.redisID = body.redisID.trim()
	body.units = body.units.trim()
	body.configServiceID = body.configServiceID.trim()
	body.bitbucketCredentialsID = body.bitbucketCredentialsID.trim()
}


def getComposeApiToken(body) {

	def tokenValue
	withCredentials([string(credentialsId: "${body.composeTokenID}", variable: 'token')]) {
			if (token) {
				tokenValue = token
			} else {
				println "[ERROR] : Failed to retrieve compose api token by using id: ${body.composeTokenID}"
				throw err
			}
	}

	return tokenValue
}

def getConfigServiceDetails(body) {

	def url
	def username
	def password
	def token

	withCredentials([string(credentialsId: "${body.configServiceID}", variable: 'configServiceJson')]) {
			if (configServiceJson) {

				def values = jsonParse(configServiceJson)
				if (values && values.url && values.username && values.password && values.token) {
					url = values.url
					username = values.username
					password = values.password
					token = values.token
				} else {
					println "[ERROR] : config service json cannot be parsed. Please json configured for id: ${body.configServiceID}"
					throw err
				}

			} else {
				println "[ERROR] : Failed to retrieve config service details by using id: ${body.configServiceID}"
				throw err
			}
	}

	return [url: url, username: username, password: password, token: token]
}

def verifyBitbucketCredentials(body) {

	withCredentials([
				[$class: 'UsernamePasswordMultiBinding', credentialsId: "${body.bitbucketCredentialsID}",
						usernameVariable: 'GIT_USER', passwordVariable: 'GIT_PASSWORD'
				]
		]) {
				if (!GIT_USER || !GIT_PASSWORD) {
					println "[ERROR] : Failed to retrieve bitbucket credentials by using id: ${body.bitbucketCredentialsID}"
					throw err
				}
		}
}

def loginBluemix(body) {

	//set cf home so that cf command can be executed
	env.CF_HOME="${WORKSPACE}"

	withCredentials([
				[$class: 'UsernamePasswordMultiBinding', credentialsId: "csf-bluemix-np",
						usernameVariable: 'BX_USER', passwordVariable: 'BX_PASSWORD'
				]
		]) {

			if ( BX_USER && BX_PASSWORD && body.bxDomain && body.bxOrg && body.bxSpace ){
				def cmd = "#!/bin/sh -e\n cf login -u \"${BX_USER}\" -p \"${BX_PASSWORD}\" -a ${body.bxDomain} -o ${body.bxOrg} -s ${body.bxSpace}"
				def loginOutput = sh returnStdout: true, script: cmd

				if (loginOutput && loginOutput.contains("Targeted org ${body.bxOrg}") && loginOutput.contains("Targeted space ${body.bxSpace}")) {
					println "Successfully log into bluemix: ${body.bxDomain}, ${body.bxOrg}, ${body.bxSpace}"
				} else {
					println "[ERROR] : Failed to log into bluemix: ${body.bxDomain}, ${body.bxOrg}, ${body.bxSpace}. ${loginOutput}"
					throw err
				}

			} else {
					println "[ERROR] : Won't be attempting to login. As all login params were not available:: BX_USER: $BX_USER , BX_PASSWORD: $BX_PASSWORD, loginDomain: ${body.bxDomain}, organization: ${body.bxOrg}, space: ${body.bxSpace}"
					error("Won't be attempting to login. As all login params were not available:: BX_USER: $BX_USER , BX_PASSWORD: $BX_PASSWORD, loginDomain: ${body.bxDomain}, organization: ${body.bxOrg}, space: ${body.bxSpace}")
			}

	}
}

def createRedisService(name) {

	println "## Creating a redis deployment: ${name} ## "

	cmd = "#!/bin/sh -e\n cf create-service compose-redis-dedicated Enterprise ${name}"
	output = sh returnStdout: true, script: cmd

	if (output) {
		if (output.contains("FAILED")) {
			println	"## [ERROR] Failed to create redis deployment: ${name}. Error details: ${output} ## "
			throw err
		}
		println "## Successfully created redis deployment: ${name} ## "

	} else {
		println	"## [ERROR] Failed to create redis deployment: ${name}."
		throw err
	}

}

def createRedisServiceKey(name, key) {

	println "## Creating redis service key for ${name} ## "

	cmd = "#!/bin/sh -e\n cf create-service-key ${name} ${key}"
	output = sh returnStdout: true, script: cmd

	if (output) {
		if (output.contains("FAILED")) {
			println	"## [ERROR] Failed to create redis service key for ${name}. Error details: ${output} ## "
			throw err
		}
		println "## Successfully create redis service key ${key} for ${name} ## "

	} else {
		println	"## [ERROR] Failed to create redis service key for ${name}."
		throw err
	}

}

def getRedisDeploymentId(name, key) {

	println "## Getting redis deployment id for ${name} ## "

	cmd = "#!/bin/sh -e\n cf service-key ${name} ${key} | grep deployment_id | cut -d'\"' -f4"
	output = sh returnStdout: true, script: cmd

	if (output) {
		if (output.contains("FAILED")) {
			println	"## [ERROR] Failed to create redis service key for ${name}. Error details: ${output} ## "
			throw err
		}
		println "## Successfully create redis service key ${key} for ${name} ## "

	} else {
		println	"## [ERROR] Failed to create redis service key for ${name}."
		throw err
	}

	return output.trim()
}

def createRedisSecureConnection(url, token, deploymentId) {

	/*sample request:
			curl -X POST 'https://api.compose.com/2016-07/deployments/5854017d89d50f424e00002c/portals' \
			-H "Authorization: Bearer [[app:Authorization]]" \
			-H "Content-Type: application/json" \
			-d '{
			"deployment": {
				"portal": {
					"type": "tcp"
				}
			}
			}'
	*/
	/*sample response:
			{
		"id":"39e72c468a32b5015c030122",
		"account_id":"48c75ba0d2f885101ce2b15a",
		"parent_id":null,
		"template":"Recipes::Deployment::Run",
		"status":"running",
		"status_detail":"	Running create_capsule on aws-us-east-1-portal.3.",
		"created_at":"2017-10-06T15:07:02Z",
		"updated_at":"2017-10-06T15:07:10Z",
		"operations_complete":0,
		"operations_total":3,
		"deployment_id":"5854017d89d50f424e00002c",
		"name":"Creating haproxy portal",
		"source":"customer_api",
		"source_user":"58b73bd0a2f865000ce2b159",
		"source_reason":null,
		"source_ip":"231.158.133.117",
		"_embedded": {
			"recipes":[]
		}
		}
	*/

	println "## Creating redis secure connection for deployment: ${deploymentId} ## "

	def portalUrl = "${url}deployments/${deploymentId}/portals"
	def dataMap = [deployment: [portal: [type: "tcp", ssl: true]]]
	def data = jsonProduce(dataMap)
	def cmd = '#!/bin/sh -e\ncurl -X POST -H "Authorization: Bearer ' + token + '" -H "Content-Type: application/json" -d \''+ data +'\' "' + portalUrl + '"'
	def addPortalOutput = sh returnStdout: true, script: cmd

	if (addPortalOutput) {
		def response = jsonParse(addPortalOutput)
		if (response && response.'deployment_id' && response.'deployment_id' == "${deploymentId}") {
			println "## Successfully craeted redis secure connection for deployment: ${deploymentId} ## "
		} else {
			println "## [ERROR]: Failed to create redis secure connection for deployment: ${deploymentId} ## "
			throw err
		}

	} else {
		println "## [ERROR]: Failed to create redis secure connection for deployment: ${deploymentId} ## "
		throw err
	}

}

def updateRedisScaling(url, token, deploymentId, units) {

	if(units > 1) {
		println "## Updating redis scalings for deployment: ${deploymentId} to ${units} units## "

		def scalingUrl = "${url}deployments/${deploymentId}/scalings"
		def dataMap = [deployment: [units: units]]
		def data = jsonProduce(dataMap)
		def cmd = '#!/bin/sh -e\ncurl -X POST -H "Authorization: Bearer ' + token + '" -H "Content-Type: application/json; charset=utf-8" -d \''+ data +'\' "' + scalingUrl + '"'
		def updateScalingOutput = sh returnStdout: true, script: cmd

		if (updateScalingOutput) {
			def response = jsonParse(updateScalingOutput)
			if (response && response.deployment_id && response.deployment_id == "${deploymentId}") {
				println "## Successfully updated redis scalings for deployment: ${deploymentId} to ${units} units## "
			} else {
				println "## [ERROR]: Failed to update redis scalings for deployment: ${deploymentId} to ${units} units## "
				throw err
			}
		} else {
			println "## [ERROR]: Failed to updated redis scalings for deployment: ${deploymentId} to ${units} units## "
			throw err
		}
	} else {
			println "## Units config for deployment: ${deploymentId} is not larger than 1. Therefore, no need to update scalings ## "
	}

}

def getRedisConnectionDetails(url, token, deploymentId) {

  println "## Retrieving redis connection details for Deployment ID " + deploymentId

	def getDeploymentUrl = "${url}deployments/${deploymentId}"
  cmd = '#!/bin/sh -e\ncurl -X GET -H "Authorization: Bearer ' + token + '" -H "Content-Type: application/json" "' + getDeploymentUrl + '"'
  output = sh returnStdout: true, script: cmd
	def response

	if (output) {
		response = jsonParse(output)
		if (response && response.id && response.id == "${deploymentId}") {
			println "## Successfully retrieved redis connection details for Deployment ID: ${deploymentId}## "
		} else {
			println "## [ERROR]: Failed to retrieve redis connection details for Deployment ID: ${deploymentId}## "
			throw err
		}
	} else {
		println "## [ERROR]: Failed to retrieve redis connection details for Deployment ID: ${deploymentId}## "
		throw err
	}

  return response

}

def encryptRedisCredential(ep, username, password, accessToken, profile, patterns) {

	dataMap =  [[service: "dcpconfig", profile: "${profile}", label: "master", patterns: patterns]]

	request = jsonProduce(dataMap)

	credential = "Basic " + "${username}:${password}".bytes.encodeBase64().toString()

	def response

	cmd = '#!/bin/sh -e\ncurl -X PUT -H "Content-Type: application/json" -H "Authorization: ' + credential + '" -H "access_token: '+ accessToken +'" -d \''+ request +'\' "'+ ep +'"'
	output = sh returnStdout: true, script: cmd

	if (output) {
		response = jsonParse(output)
		if (response && response.message && response.message == "Request has been processed successfully") {
					println "Successfully encrypted redis password and cert"
		} else {
					println "[ERROR] : Error while encrypting"
					throw err
		}
	} else {
				println "[ERROR] : Error while encrypting"
				throw err
	}

}

def updateFabricRegistry(bitbucketUrl, bitbucketCredentialsId, branch, repo, folder, file, cachePolicyFabricMapKey, cachePolicyFabricMapRegistry, cacheFabricConfigKey, cacheFabricConfig) {
	withCredentials([
				[$class: 'UsernamePasswordMultiBinding', credentialsId: "${bitbucketCredentialsId}",
						usernameVariable: 'GIT_USER', passwordVariable: 'GIT_PASSWORD'
				]
		]) {

				def dcpConfigFolderPath = "${WORKSPACE}/${repo}"
				dir("${dcpConfigFolderPath}") {
					git branch: "${branch}", credentialsId: "${bitbucketCredentialsId}", url: "https://${bitbucketUrl}"
				}

				def fabricRegistryFilePath = "${WORKSPACE}/${repo}/${folder}/${file}"
				println "fabricRegistryFilePath : ${fabricRegistryFilePath}"

				if (fileExists("${fabricRegistryFilePath}")) {

				def fabricRegistryFile = readFile file: "${fabricRegistryFilePath}"
				def fabricRegistry = jsonParse(fabricRegistryFile)


				fabricRegistry.cachePolicyFabricMapRegistry.put("${cachePolicyFabricMapKey}", cachePolicyFabricMapRegistry)
				fabricRegistry.cacheFabricRegistry."Redis".cacheFabricConfig.put("${cacheFabricConfigKey}", cacheFabricConfig)

				def json = jsonProducePrettyPrint(fabricRegistry)
				writeFile file: "${fabricRegistryFilePath}", text: "${json}", encoding: "UTF-8"

				//change directory
				dir("${WORKSPACE}/${repo}") {

									sh ("git add ${folder}/${file}")
									def gitCommitOutput = sh (returnStdout: true, script: "git commit -m 'added new redis configs'")

									if (gitCommitOutput && gitCommitOutput.contains("1 file changed")) {
										println "Successfully commit updates"
										sh "git tag ${BUILD_TAG}"

										def gitPushOutput = sh (returnStdout: true, script: "git push https://${GIT_USER}:${GIT_PASSWORD}@${bitbucketUrl} --all")

									} else {
										println	"## [ERROR] Failed to commit changes to: ${file} "
										throw err
									}
				}

				} else {
					println ("[ERROR] : error while trying to read fabric registry json. ${fabricRegistryFilePath} does not Exist")
					throw err
				}
		}
}

@NonCPS
def jsonParse(def json) {
    new groovy.json.JsonSlurperClassic().parseText(json)
}

@NonCPS
def jsonProduce(def dataMap) {
		def json = new JsonBuilder()
    json(dataMap)
		json.toString()
}

@NonCPS
def jsonProducePrettyPrint(def dataMap) {
		def json = new JsonBuilder()
    json(dataMap)
		json.toPrettyString()
}
