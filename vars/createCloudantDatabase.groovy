/*
Description:

Creaet cloudant dabase and views based on the cloudant details specified in jenkins file.

In jenkins file, the following variables have to be passed to deployToBluemix step:
1): cloudantLabel: cloudant service label
It's default to be 'cloudantNoSQLDB Dedicated'. Use the varaible to override the defaul value if
kp bluemix marketplace has a new cloudant service or existing lable name is updated.
2): cloudantDatabase: a list of database to be created. e.g. "['db1', 'db2']"
*/

@Grab(group = 'org.codehaus.groovy.modules.http-builder', module = 'http-builder', version = '0.7.1')

import groovyx.net.http.RESTClient
import groovy.json.JsonSlurperClassic
import static groovyx.net.http.ContentType.JSON


def call(body) {

	try {
					//get cloudant label
					def cloudantLabel = 'cloudantNoSQLDB Dedicated'
					if (body.cloudantLabel && body.cloudantLabel.trim() != 'cloudantNoSQLDB Dedicated') {
						cloudantLabel = "${body.cloudantLabel.trim()}"
					}
					println "Cloudant Label: ${cloudantLabel}"

					def viewJsonFileBasePath = "${WORKSPACE}/config/cloudant"
					def appEnvVariables = sh (returnStdout: true, script: "cf env ${env.BLUE_MIX_NAME_VERSIONED}").trim()

					if (!appEnvVariables) {
							println "[ERROR] : Error while retrieving app environment variables: null"
							throw err
					}

					//retrieves all vcap services, application variables
					def vcap = appEnvVariables.substring(appEnvVariables.indexOf("System-Provided:") + "System-Provided:".length(), appEnvVariables.indexOf("User-Provided:"))

					if (vcap) {

							def url = getCloudantUrl(vcap, cloudantLabel)
							if (url) {

									def ep = url.getAt(0)

									def response

									//read cloudant database and view configs
									def cloudantDatabase = Eval.me(body.cloudantDatabase)
									println "cloudantDatabase: ${cloudantDatabase}"

									if (cloudantDatabase) {

											cloudantDatabase.each { db ->
													//create database
													response = createDatabase(ep, db)

													if(response.status && (response.status == 200 || response.status == 201) && response.data && response.data.ok && response.data.ok == true) {
															println "Successfully created databse: ${db}"
															response=null

															def viewJsonDirectory = viewJsonFileBasePath + "/${db}/views"
															if (fileExists(viewJsonDirectory)) {

																	//iterate view json files
																	def viewFiles = findFiles(glob: "**/config/cloudant/${db}/views/*.json")
																	println "viewFiles for database ${db}: ${viewFiles}"

																	if (viewFiles) {
																		//get views for the db
																		def dbViews = viewFiles.collect {it.name.take(it.name.lastIndexOf('.json'))}
																		if (dbViews) {
																				println "cloudant views for database ${db}: ${dbViews}"
																				dbViews.each {view ->
																						//create view
																						createView(ep, viewJsonDirectory, db, view)
																				}
																		} else {
																				println "cloudant database: ${db} has no views to create"
																		}
																	} else {
																				println "No view json files found at ${viewJsonDirectory}. So assume that cloudant database: ${db} has no views to create"
																	}

															} else {
																println "cloudant database: ${db} has no view json directory. So assume that cloudant database: ${db} has no views to create"
															}

													} else {
															println "[ERROR] : Error while creating cloudant database: ${db}"
															throw err
													}

											}
									}
							} else {
									println "[ERROR] : Error while creating cloudant database: unable to retrieve cloudant url"
									throw err
							}

					} else {
							println "[ERROR] : Error while creating cloudant database: unable to retrieve application environment variables"
							throw err
					}

}catch(err){
			println "[ERROR] : Error while creating cloudant database: " + "${err.getStackTrace()}"
			error("[ERROR] : Error while creating cloudant database: " + "${err.getStackTrace()}")
}

}

def getEnvironmentConfig(body) {
		def environmentConfig
		if (body.configEnvironment && "${body.configEnvironment}" != "null")
				environmentConfig = body.configEnvironment
		else
				environmentConfig = body.space
		return environmentConfig
}

@NonCPS
def jsonParse(def json) {
    new groovy.json.JsonSlurperClassic().parseText(json)
}

def getCloudantUrl(vcap, cloudantLabel) {
		def url

		def json = jsonParse(vcap)

		if (json && json.VCAP_SERVICES && json.VCAP_SERVICES."${cloudantLabel}" && json.VCAP_SERVICES."${cloudantLabel}".credentials) {
			url = json.VCAP_SERVICES."${cloudantLabel}".credentials.url
		}

		return url
}

def createDatabase(ep, db) {
		def client = new RESTClient(ep)
		client.ignoreSSLIssues()
		def response = client.put(path: "/${db}",
													 headers: [Accept: 'application/json'])
		//set client to null to prevent serialization exception
		client = null
		return response
}

def createView(ep, viewJsonDirectory, db, view) {
		def viewJsonFilePath = viewJsonDirectory + "/${view}.json"
		println "viewJsonFilePath : ${viewJsonFilePath}"

		if (fileExists("${viewJsonFilePath}")) {

				def viewJson = readJSON file: "${viewJsonFilePath}"

				def client = new RESTClient(ep)
				client.ignoreSSLIssues()
				def response = client.put (
						path: "/${db}/_design/${view}",
						contentType: JSON,
						body: viewJson,
						headers: [Accept: 'application/json']
				)

				client=null
				if(response.status && (response.status == 200 || response.status == 201) && response.data && response.data.ok && response.data.ok == true) {
						println "Successfully created view: ${view} for databse: ${db}"
						response = null
				} else {
						println "[ERROR] : Error while creating view: ${view} for databse: ${db}"
						throw err
				}

		} else {
			println ("[ERROR] : error while trying to read view json. ${viewJsonFilePath} does not Exist")
			throw err
		}
}
