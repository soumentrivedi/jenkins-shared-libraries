/*

Description: Shared Library function to send hip chat notification.

List of params:

hipchat_room: "Hipchat room", //Mandatory
email: "no-reply@kp.org", //Optional.
notification_types: "success, failure, backtoNormal", //Optional. Default is failure, backtoNormal.
build_status: currentBuild.status

*/

def call(body) {

//    echo "*******************. do notification.  ************************"
  //  echo "Sending hipchat notification to room: ${body.hipchat_room} email: ${body.email} notification_types: ${body.notification_types} build_status: ${body.build_status}"
    def creds = credentials('HipChat-API-Token')
    def message
    def buildDuration = currentBuild.rawBuild.getDurationString().replace('and counting', "")
    //echo "${creds}"
    if(! body.message){
        message = "${env.JOB_NAME} #${env.BUILD_NUMBER} Build ${body.build_status} after ${buildDuration} <a href='${env.BUILD_URL}'>(View Build)</a>"
    } else {
        message = " ${body.message} :: ${env.JOB_NAME} #${env.BUILD_NUMBER} Build ${body.build_status} after ${buildDuration} <a href='${env.BUILD_URL}'>(View Build)</a>"
    }


    try {

      if ("${body.build_status}" == "SUCCESS") {
        hipchatSend (
        color: 'GREEN',
      //  credentialId: "${creds}",
        failOnError: true,
        message: "${message}",
        notify: true,
        room: "${body.hipchat_room}",
        sendAs: 'Jenkins',
        server: 'hipchat.kp.org',
        textFormat: false,
        v2enabled: false
        )
      } else if ("${body.build_status}" == "FAILED") {
        hipchatSend (
        color: 'RED',
    //    credentialId: "${creds}",
        failOnError: true,
        message: "${message}",
        notify: true,
        room: "${body.hipchat_room}",
        sendAs: 'Jenkins',
        server: 'hipchat.kp.org',
        textFormat: false,
        v2enabled: false
        )
      } else if ("${body.build_status}" == "NOTIFY"){
        hipchatSend (
        color: 'YELLOW',
    //    credentialId: "${creds}",
        failOnError: true,
        message: "${message}",
        notify: true,
        room: "${body.hipchat_room}",
        sendAs: 'Jenkins',
        server: 'hipchat.kp.org',
        textFormat: false,
        v2enabled: false
        )
      }


      // if email id is set and sendEmail is true send notification via email
     if (body.email && ("${body.email}" != null)) {
          echo "Sending email notification to : ${body.email}"
          emailext to: "${body.email}",
               subject: "${body.build_status}: ${currentBuild.fullDisplayName}",
               body: "${body.build_status} : ${env.BUILD_URL}"
      }


    //  echo "Sending hipchat notification to room: ${body.hipchat_room} and email: ${body.email} with notification_types: ${body.notification_types}"


    } catch (e) {
        echo "Failed to send hipchat notification : ${e}"
     throw e
   }
  }
