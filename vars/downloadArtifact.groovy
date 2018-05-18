/*
  Description:
  Download artifact by reading environment variable : APP_ARTIFACT_PATH and unzip to make it ready for deploy.
*/

def call(body){
    try {
      println "[INFO] : Artifact path :: ${env.APP_ARTIFACT_PATH}"
      sh "chmod -R 755 ${WORKSPACE}"
      downloadFile("${env.APP_ARTIFACT_PATH}")
      unZipArtifact([space : "${body.space}", fileName: "${WORKSPACE}/${env.APP_ARTIFACT_PATH.tokenize('/')[-1]}"])
    } catch (err){
        println "[ERROR] : Error encountered while downloading artifact. ${err.getStackTrace()}"
        error("Error encountered while downloading artifact")
    }
}


// Downloads file when an URL is passed in and check for the status code 200
def downloadFile(address){
  sh "rm -rf ${WORKSPACE}/${address.tokenize('/')[-1]}"
  def DOWNLOAD_ARTIFACT_STATUS = sh ( script: "curl -Is ${address} | head -1 | awk \'{print \$2}\'" , returnStdout: true).trim()
	if ("${DOWNLOAD_ARTIFACT_STATUS}" == "200") {
		println("[INFO] : Check the artifactory URL ${address} and returned status code: ${DOWNLOAD_ARTIFACT_STATUS}")
	}else {
		println("[ERROR] : Error while checking URL ${address} and returned status code: ${DOWNLOAD_ARTIFACT_STATUS}")
		error("[ERROR] : Error while checking URL ${address} and returned status code: ${DOWNLOAD_ARTIFACT_STATUS}")
	}
	sh "curl -O ${address}"
	sh "if [[ ! -f ${WORKSPACE}/${address.tokenize('/')[-1]} ]]; then echo '[ERROR] : Downloaded artifact was not found in workspace'; exit 1; fi;"
	}

// unzipping downloaded artifacts
def unZipArtifact(data){
  println "[INFO] : Unzipping artifacts from :: ${data.fileName}"
  // remove package folder if it exists
  sh "rm -rf ${WORKSPACE}/package"
  if(data.fileName.contains(".tgz")){
      // once it is unzipped it folder name is package
      sh "tar -xzf ${data.fileName}"
      println "[INFO] : Checking if redis tunnel config needs to be inserted in package"
      loadShellScripts("include_redis_tunnel_config.sh")
      sh "include_redis_tunnel_config.sh ${WORKSPACE}/package ${data.space}"

    } else {
      println "[INFO] : Checking if liberty files needs to be inserted in package"
      loadShellScripts("include_liberty_files.sh")
      sh "include_liberty_files.sh ${data.space} ${data.fileName}"
      println "[INFO] : copying downloaded artifact to package folder for cf push"
      sh "mkdir -p ${WORKSPACE}/package"
      sh "mv ${data.fileName} ${WORKSPACE}/package/"
    }

}
