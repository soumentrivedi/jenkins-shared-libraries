# print out stuff on stdout
set -x

# An error exit function

error_exit()
{
	echo "[ERROR] : $1" 1>&2
	exit 1
}

echo "************* Creating Declared Services in Bluemix ****************"
SERVICE_NAME="${1}"
PLAN="${2}"
CUSTOM_NAME="${3}"

if [[ ! -z ${CUSTOM_NAME} && ${CUSTOM_NAME} != null && ! -z ${SERVICE_NAME} && ${SERVICE_NAME} != null
&& ! -z ${PLAN} && ${PLAN} != null ]]; then
			#creating declared services here
			echo "************* Creating Declared Services ****************"

			serviceStatus=`cf services | grep ${CUSTOM_NAME} | wc -l` || error_exit "Failed to query for  service ${CUSTOM_NAME} !!"
			if [[ ${serviceStatus} -eq 0 ]]; then
					cf create-service "${SERVICE_NAME}" "${PLAN}" "${CUSTOM_NAME}" >> ${WORKSPACE}/instruction.txt || error_exit "Failed to create declared service ${CUSTOM_NAME}"
					# sleep 60
					cf services
			else
				# Note: be careful when updating this particular log statement as it's used in deployToBluemix script for conditional logic
				echo "Declared serivce: ${CUSTOM_NAME} has already existed."
			fi
else
  echo "****** All the required params were not recieved by this script *******"
  exit 1
fi
