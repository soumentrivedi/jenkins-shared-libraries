// will call the update_apic.sh with appropriate params

def call(body){

  try{
    setEnv([getArtifactsVersion: "true"])

    if ("${env.buildType}" == "maven"){
      deleteDir()
      def fileName = downloadFile("${env.apicFullArtifactPath}")
      sh "unzip ${fileName}"
      def appName = sh (returnStdout: true, script: "cat package.json | grep -m 1 'name' | cut -d':' -f2 | tr -d ',' | tr -d '\"' ").trim()
      env.APP_NAME = "${appName}"
    }


    // override when artifact version provided by user. Else read from Jenkinsfile in workspace
    if(params.ARTIFACT_VERSION) {
      env.APP_VERSION = "${params.ARTIFACT_VERSION}"
    }

    println "[INFO] : apicServer : ${body.apicServer}"
    println "[INFO] : apicCredentials: ${body.apicCredentials}"
    println "[INFO] : apicSpace: ${body.apicSpace}"
    println "[INFO] : apicOrg: ${body.apicOrg}"
    println "[INFO] : apicCatalog: ${body.apicCatalog}"
    println "[INFO] : apicDomain: ${body.apicDomain}"
    println "[INFO] : App Version: ${env.APP_VERSION}"
    println "[INFO] : App Name: ${env.APP_NAME}"
    println "[INFO] : App URL prefix: ${body.apicUrlPrefix}"

    loadShellScripts("update_apic.sh")

    withCredentials([
          [$class: 'UsernamePasswordMultiBinding', credentialsId: "${body.apicCredentials}",
              usernameVariable: 'APIC_USER', passwordVariable: 'APIC_PASSWORD'
          ]
      ]){
          sh "echo 'no' | apic --accept-license login --server ${body.apicServer} --username $APIC_USER --password $APIC_PASSWORD"
          sh "update_apic.sh ${body.apicServer} ${body.apicSpace} ${body.apicOrg} ${body.apicCatalog} ${body.apicDomain} ${env.APP_VERSION} ${env.APP_NAME} ${env.buildType} ${body.apicUrlPrefix}"
          sh "apic logout --server ${body.apicServer}"

      }
  } catch(err) {
    println "[ERROR] :: Error encountered while updating APIC :: ${err.getStackTrace()}"
    error("Error encountered while updating APIC")
  }

}


// Downloads file when an URL is passed in
def downloadFile(address){
  sh "rm -rf ${WORKSPACE}/${address.tokenize('/')[-1]}"
  sh "curl -O ${address}"
  sh "if [[ ! -f ${WORKSPACE}/${address.tokenize('/')[-1]} ]]; then echo 'File was not found in artifactory!!'; exit 1; fi;"
  return "${WORKSPACE}/${address.tokenize('/')[-1]}"
}
