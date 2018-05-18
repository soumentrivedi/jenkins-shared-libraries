# print out stuff on stdout
set -x

# An error exit function

error_exit()
{
	echo "[ERROR] : $1" 1>&2
	exit 1
}

echo "************* Creating User Provided Services in Bluemix ****************"

BLUE_MIX_SERVICE_NAME="${1}"
SERVICE_DATA=${2}
IS_SECRET=${3}

#  check if all the mandatory varaiables are set
if [[ ! -z ${BLUE_MIX_SERVICE_NAME} && ${BLUE_MIX_SERVICE_NAME} != null && ! -z ${SERVICE_DATA} && $IS_SECRET == true ]]; then

      # check if the service already exists
      cf services

      serviceStatus=`cf services | grep ${BLUE_MIX_SERVICE_NAME} | wc -l`
      if [[ ${serviceStatus} -eq 0 ]]; then
          # Service not found creating
          echo "**** CREATING USER PROVIDED SERVICE ${BLUE_MIX_SERVICE_NAME} ****"
          cf cups ${BLUE_MIX_SERVICE_NAME} -p $SERVICE_DATA >> ${WORKSPACE}/instruction.txt || error_exit "Failed to create USER PROVIDED service for ${BLUE_MIX_SERVICE_NAME} !!"

      else
          # update a existing service
          echo "*** UPDATING USER PROVIDED SERVICE ${BLUE_MIX_SERVICE_NAME} ****"
          cf uups ${BLUE_MIX_SERVICE_NAME} -p $SERVICE_DATA >> ${WORKSPACE}/instruction.txt || error_exit "Failed to update USER PROVIDED service for ${BLUE_MIX_SERVICE_NAME} !!"

      fi

      # check if services are getting listed
      cf services

elif [[ ! -z ${BLUE_MIX_SERVICE_NAME} && ${BLUE_MIX_SERVICE_NAME} != null && ! -z ${SERVICE_DATA} && $IS_SECRET == false ]]; then
			cf services

			serviceStatus=`cf services | grep ${BLUE_MIX_SERVICE_NAME} | wc -l`
			if [[ ${serviceStatus} -eq 0 ]]; then
			# Service not found creating
			echo "**** CREATING USER PROVIDED SERVICE ${BLUE_MIX_SERVICE_NAME} ****"
			cf cups ${BLUE_MIX_SERVICE_NAME} -l $SERVICE_DATA >> ${WORKSPACE}/instruction.txt || error_exit "Failed to create USER PROVIDED service for ${BLUE_MIX_SERVICE_NAME} !!"

			else
			# update a existing service
			echo "*** UPDATING USER PROVIDED SERVICE ${BLUE_MIX_SERVICE_NAME} ****"
			cf uups ${BLUE_MIX_SERVICE_NAME} -l $SERVICE_DATA >> ${WORKSPACE}/instruction.txt || error_exit "Failed to update USER PROVIDED service for ${BLUE_MIX_SERVICE_NAME} !!"

			fi

	# check if services are getting listed
	cf services
else
      error_exit "****** All the required params were not recieved by this script *******"
fi
