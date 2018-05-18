
@Grab(group = 'org.codehaus.groovy.modules.http-builder', module = 'http-builder', version = '0.7.1')
import groovyx.net.http.RESTClient

def call(body) {
try {
    println "[INFO] : Determining config for Bluemix Deploy : ${body}"

    def zipFileName
    def folderName
    def configData = [:]
    // will recieve map object with keys artifactoryurl and subfolder
    configData = getConfigArtifactoryUrl("${WORKSPACE}/${body.configRepoName}/pom.xml", "${body.configVersion}")

    if(configData){
      env.CONFIG_REPO_NAME = "${body.configRepoName}"
      env.CONFIG_ARTIFACT_URL = configData['artifactoryurl']
      println "[INFO] : CONFIG_REPO_NAME :: ${CONFIG_REPO_NAME}, CONFIG_ARTIFACT_URL :: ${CONFIG_ARTIFACT_URL}"
      sh "if [[ -d ${WORKSPACE}/${body.configRepoName} ]]; then rm -rf ${WORKSPACE}/${body.configRepoName}*; fi;"
      sh "if [[ -d ${WORKSPACE}/config ]]; then rm -rf ${WORKSPACE}/config; fi;"
      zipFileName = "${CONFIG_ARTIFACT_URL.tokenize('/')[-1]}"
      folderName = "${zipFileName.replaceAll('.zip',"")}"
      println "[INFO] : printing out Zipfilename and folderName :: ${zipFileName} :: ${folderName}"
      sh "curl -O ${CONFIG_ARTIFACT_URL}"
      sh "unzip -q ${zipFileName} -d ${WORKSPACE}/config"
      // after unzip copy the contents to config folder and remove the sub folder
      sh "cp -r ${WORKSPACE}/config/${configData['subfolder']}/* ${WORKSPACE}/config/"
      sh "if [[ -d ${WORKSPACE}/config/${configData['subfolder']} ]]; then rm -rf ${WORKSPACE}/config/${configData['subfolder']}; fi;"
      // remove the downloaded config artifact zip
      sh "rm -f ${zipFileName}"
      return "${configData['config_version']}"
    }

  } catch(err){
    println "[ERROR] : Error encountered while determining config for BlueMix deploy" + "${err.getStackTrace()}"
    error("Error encountered while determining config for BlueMix deploy" + "${err.getStackTrace()}")
  }
}

// method to get config artifactory url
def getConfigArtifactoryUrl(pomFilePath, config_version){
  echo "Parse pom.xml and determine artifactory url :: ${pomFilePath}"

  def fullPath = [:]
  def artifactFileExtension = '.zip'
  String fileAsString
  def response
  def buildNumber
  def timeStamp
  def pomVersion
  def INHOUSE_SNAPSHOT = 'inhouse_snapshot'
  def INHOUSE_RELEASE = 'inhouse_release'
  fileAsString = readFile file: "${pomFilePath}", encoding: "UTF-8"
  def parsedPom = new XmlParser().parseText(fileAsString)
  def groupId = parsedPom.groupId[0].text()
  def appArtifactId = parsedPom.artifactId[0].text()

  if( "${params.CONFIG_VERSION}" == "" || "${params.CONFIG_VERSION}" == null ){
    // Use case 1: Read from Jenkinsfile
    if("${config_version}" != "" && "${config_version}" != null){
        println "[INFO] : Reading config version from Jenkinsfile"
        if("${config_version}".toLowerCase().contains("latest")){
            def config = determineLatest([version: "${config_version}", appid: "${appArtifactId}", gid: "${groupId}"])
            println "config is " + config
            checkVersionFormat("${config['version']}")
            pomVersion = config['version']
            repo = config['repo']
        } else {
          checkVersionFormat("${config_version}")
          pomVersion = "${config_version}".split('-SNAPSHOT-')[0]+ "-SNAPSHOT"
          buildNumber = config_version.split('-SNAPSHOT-')[1]
          repo = snapshot_or_release(pomVersion)
        }

    } else {
      println "[ERROR] : Error reading configVersion from Jenkinsfile"
      error("[ERROR] : Error reading configVersion from Jenkinsfile")
    }
  } else if ("${params.CONFIG_VERSION}".toLowerCase().contains("latest")) {
    // Use Case 2: determine the latest config version and repo
    println "[INFO] : Evaluating latest config version in Artifactory"
    def config = determineLatest([version: "${params.CONFIG_VERSION}", appid: "${appArtifactId}", gid: "${groupId}"])
    checkVersionFormat("${config['version']}")
    pomVersion = config['version']
    repo = config['repo']

  } else {
    // Use Case 3: read from params as is
    println "[INFO] : Reading Version from Jenkins parameters"
    checkVersionFormat("${params.CONFIG_VERSION}")
    pomVersion = "${params.CONFIG_VERSION}".split('-SNAPSHOT-')[0]+ "-SNAPSHOT"
    buildNumber = params.CONFIG_VERSION.split('-SNAPSHOT-')[1]
    println "pomVersion is" + pomVersion
    repo = snapshot_or_release(pomVersion)
  }

  // common code for all above use cases
  fullPath['subfolder'] = "${appArtifactId}" + "-" + "${pomVersion}"
  // this will be used to rename build to have  config version
  fullPath['config_version'] = "${pomVersion}"
  def appVersionPath = "${env.ARTIFACTORY_URL}/${repo}/${groupId.replace(".", "/")}/${appArtifactId}/${pomVersion}/"

  if (repo == INHOUSE_SNAPSHOT) {

      try {
        if ((params.CONFIG_VERSION && !("${params.CONFIG_VERSION}".toLowerCase().contains('latest'))) ||  !("${config_version}".toLowerCase().contains('latest')))
        {
          println "appVersionPath is " + appVersionPath
          println "buildNumber is " + buildNumber
          fullPath['artifactName'] = sh (returnStdout: true, script: "curl -s ${appVersionPath} | grep -m 1 '${buildNumber}${artifactFileExtension}' | cut -d'>' -f2 | cut -d'<' -f1").trim()
          fullPath['artifactoryurl'] = appVersionPath + fullPath['artifactName']
          println "fullpath is" + fullPath['artifactoryurl']
        }
        else{
          def client = new RESTClient(appVersionPath)
          response = client.get(path: "maven-metadata.xml")
          buildNumber = response.data.versioning.snapshot.buildNumber
          timeStamp = response.data.versioning.snapshot.timestamp
          client= null
          // artifactory url when inhouse_snapshot
          fullPath['artifactoryurl'] = appVersionPath + appArtifactId + "-" + pomVersion.substring(0, pomVersion.length() - 9) +
                  "-" + timeStamp + "-" + buildNumber + artifactFileExtension
          println "[INFO] : Artifactory url : ${fullPath['artifactoryurl']}"
        }
      } catch (err) {
        println "[ERROR] : Cannot form full artifactory url ${fullPath}"
        error("[ERROR] : Cannot form full artifactory url ${fullPath}" + "${err.getStackTrace()}")
      }

  } else {
      // artifactory url when inhouse_release
      fullPath['artifactoryurl'] = appVersionPath + appArtifactId + '-' + pomVersion + artifactFileExtension
      println "[INFO] : Artifactory url : ${fullPath['artifactoryurl']}"
  }
  // return map object with configVersion and artifactoryurl
  return fullPath
}


// determine if snapshot or release
def snapshot_or_release(version){
  def SNAPSHOT = 'inhouse_snapshot'
  def RELEASE = 'inhouse_release'

  if(version.contains("SNAPSHOT") || version.contains("snapshot")) {
      return SNAPSHOT
  } else {
      return RELEASE
  }
}

// gives back version and repo url of the latest
def determineLatest(data) {
  println "Determining latest config version in Artifactory..."
  def config = [:]
  def repotype = snapshot_or_release("${data['version']}")
  def latestPath = "${env.ARTIFACTORY_URL}/${repotype}/${data['gid'].replace(".", "/")}/${data['appid']}/"

  try {
      def restclient = new RESTClient(latestPath)
      def resp = restclient.get(path: "maven-metadata.xml")
      config['version'] = resp.data.versioning.latest.toString()
      config['repo'] = repotype
  } catch (err) {
      println "[ERROR] : Error while getting latest artifact url ${env.ARTIFACTORY_URL}/${repotype}/${data['gid'].replace(".", "/")}/${data['appid']}/"
      error("[ERROR] : Error while getting latest artifact url ${env.ARTIFACTORY_URL}/${repotype}/${data['gid'].replace(".", "/")}/${data['appid']}/")
  }
  restclient= null
  return config
}

// check version format
// check version format
def checkVersionFormat(data) {
  println "data is " + data
  if ("${data}" =~ /^(\d+)\.(\d+)\.(\d+)\-SNAPSHOT$/ || "${data}" =~ /^(\d+)\.(\d+)\.(\d+)$/ || "${data}" =~ /^(\d+)\.(\d+)\.(\d+)\-SNAPSHOT\-(\d+)/){
    println "Found the config version to be of correct format"
  } else {
    println "[ERROR] : Found the config version to be of incorrect format"
    error("[ERROR] : Found the config version to be of incorrect format")
  }
}
