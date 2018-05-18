def call(body) {
    def approver

    if ("${body.environment}" == "QA"){
      approver = "jenkins-build-engineers,jenkins-qa-deploy-engineers,${body.projectApprovalGroup}-qa-deploy"
      if("${body.approvalGroupOverride}" != "null" && "${body.approvalGroupOverride}" != ""){
          approver = "${approver},${body.approvalGroupOverride}-qa-deploy"
      }
    }

    if ("${body.environment}" == "PREPROD"){
      approver = "jenkins-qa-deploy-engineers,${body.projectApprovalGroup}-qa-deploy,${body.projectApprovalGroup}-prod-deploy"
      if("${body.approvalGroupOverride}" != "null" && "${body.approvalGroupOverride}" != ""){
        approver = "${approver},${body.approvalGroupOverride}-qa-deploy,${body.approvalGroupOverride}-prod-deploy"
      }
    }

    if ("${body.environment}" == "PROD"){
      approver = "jenkins-prod-deploy-engineers,${body.projectApprovalGroup}-prod-deploy"
      if("${body.approvalGroupOverride}" != "null" && "${body.approvalGroupOverride}" != ""){
        approver = "${approver},${body.approvalGroupOverride}-prod-deploy"
      }
    }

    def approve_message = "Deploy to ${body.environment}? Requires Approval from ${approver}"

    notify([hipchat_room: "${env.hipchatRoom}", //Mandatory
            email: "${env.email}", //Optional
            build_status: "NOTIFY",
            message: "${approve_message}" ])

            try{
              timeout(time: 30, unit: 'MINUTES') {
                      input (message: "${approve_message}", submitter: "${approver}", submitterParameter: "approver")
              }
            } catch(err){
                  def user = err.getCauses()[0].getUser()
                  if('SYSTEM' == user.toString()) { // SYSTEM means timeout.
                      didTimeout = true
                      echo "[ERROR] Waited for 30 minutes. Timing Out now!!"
                      error("[ERROR] Waited for 30 minutes. Timing Out now!!")
                  } else {
                      userInput = false
                      echo "[ERROR] Aborted by: [${user}]"
                      error("[ERROR] Aborted by: [${user}]")
                  }
            }
}
