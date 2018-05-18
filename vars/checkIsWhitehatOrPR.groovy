def call() {
	try {
			if (("${env.IS_WHITEHAT_JOB}" == "true" || "${env.IS_WHITEHAT_JOB}" == null) || "${env.IS_PR_BUILD}" == "true") {
				println ("[INFO] : Current build is either WhiteHat or PR build so skipping other steps")
				return false
			}else {
				return true
			}
	}catch (err) {
	error("[ERROR] : Error while checking the whitehat or PR builds ${err}")
}
}
