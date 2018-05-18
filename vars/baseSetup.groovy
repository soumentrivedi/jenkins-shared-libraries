def call(key) {
    //  sh "export ARTIFACTORY_URL=\"https://artifactory-fof-sandbox.appl.kp.org/artifactory\""
    switch (key) {
      case artifactory:
        url = "https://artifactory-fof-sandbox.appl.kp.org/artifactory"
        return url
      }
}
