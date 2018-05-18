/*
  Handle route based blue green deployments.
*/
@Grab('org.yaml:snakeyaml:1.17')
import org.yaml.snakeyaml.Yaml

def call(body){
  String greenDomain="${body.loginDomain}".replace(/.bluemix.net/,".mybluemix.net")
                                          .replaceAll(/https\:\/\/api\./, "")
                                          .replaceAll(/http\:\/\/api\./, "")
  if("${body.deployType}".toLowerCase() == "deploy-green"){
    // update the manifest.yml with test domain
    def originalRoute = deployGreen("${greenDomain}")
    def data = [:]
    data['proceedDeploy'] = true
    data['appType'] = "green"
    data['originalRoute'] = "${originalRoute}"
    // app deployment required
    return data
  } else if("${body.deployType}".toLowerCase() == "blue-to-green"){
    blueToGreen("${greenDomain}")
    // no app deployment required
    def data = [:]
    data['proceedDeploy'] = false
    data['appType'] = null
    return data
  } else if("${body.deployType}".toLowerCase() == "rollback"){
    rollBack()
    // no app deployment required
    def data = [:]
    data['proceedDeploy'] = false
    data['appType'] = null
    return data
  } else if("${body.deployType}".toLowerCase() == "delete-test-route"){
    delete_test_route(greenDomain)
    // no app deployment required
    def data = [:]
    data['proceedDeploy'] = false
    data['appType'] = null
    return data
  } else {
    // app deployment required
    def data = [:]
    data['proceedDeploy'] = true
    data['appType'] = "blue"
    return data
  }
}

def blueToGreen(greenDomain){
  // do route switch
  loadShellScripts("blue_green.sh")

  sh "blue_green.sh ${env.BLUE_MIX_NAME_VERSIONED} ${env.BLUE_MIX_HOST} \"${env.BLUE_MIX_APP_DOMAIN_LIST}\" ${greenDomain} ${env.BLUE_MIX_HOST}-green"
}

def deployGreen(greenDomain){
  // deploy app after updating the host to mybluemix.net
  try{
    def data
    if (fileExists("${env.manifestfile}")) {
        String file = readFile("${env.manifestfile}")
        Yaml parser = new Yaml()
        Map ymap = (Map) parser.load("${file}")
        // assuming there would be only one domain
        def domains = ymap['applications']['domains'][0][0]
        def host = ymap['applications'][0]['host']

        if(domains){
          ymap['applications'][0]['domains'][0] = "${greenDomain}".toString()
          ymap['applications'][0]['host'] = "${host}".toString() + "-green"
          data="${env.BLUE_MIX_HOST}" + "." + "${env.BLUE_MIX_APP_DOMAIN}"
          env.BLUE_MIX_APP_DOMAIN_LIST= "${greenDomain}"
          env.BLUE_MIX_HOST= "${host}" + "-green"
          // dumping the map onto file
          Yaml yaml = new Yaml()
          def output = yaml.dump(ymap)
          println "Updated domain in manifest.yml"
          println "${output}"
          ymap=null; parser=null; yaml=null
          // writting the string yaml to manifest file
          writeFile file: "${env.manifestfile}", text: "${output}", encoding: "UTF-8"
          return data
        } else {
                println "[ERROR] : Domains not found in manifest.yml ${env.manifestfile}"
                error("[ERROR] : Domains not found in manifest.yml ${env.manifestfile}")
        }

    } else {
          println "[ERROR] : No manifest.yml found in this path ${env.manifestfile}"
          error("No manifest.yml found in this path ${env.manifestfile}")
    }
  } catch(err){
    println "[ERROR] Encountered error while deploying green App: ${err.getStackTrace()}"
    error("[ERROR] Encountered error while deploying green App: ${err.getStackTrace()}")
  }
}


def rollBack(){
// rollback to last known working version
  loadShellScripts("rollback.sh")
  sh "rollback.sh ${env.BLUE_MIX_NAME_VERSIONED} ${env.BLUE_MIX_HOST} \"${env.BLUE_MIX_APP_DOMAIN_LIST}\""
}

def delete_test_route(greenDomain){
  // delete mybluemix.net route for the given app
  loadShellScripts("delete_test_route.sh")
  env.BLUE_MIX_HOST= "${env.BLUE_MIX_HOST}" + "-green"
  sh "delete_test_route.sh ${env.BLUE_MIX_NAME_VERSIONED} ${env.BLUE_MIX_HOST} ${greenDomain}"
}
