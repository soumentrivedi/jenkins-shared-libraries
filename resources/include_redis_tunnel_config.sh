# print out stuff on stdout
set -x

# An error exit function
error_exit()
{
	echo "[ERROR] : $1" 1>&2
	exit 1
}

PACKAGE_FOLDER=$1
ENVIRONMENT=$2

if ls ${WORKSPACE}/config/env/${ENVIRONMENT}/*.sh 1> /dev/null 2>&1 &&
   ls ${WORKSPACE}/config/env/${ENVIRONMENT}/id_rsa 1> /dev/null 2>&1; then

   echo "[INFO] Attempting to include redis tunnel config in package"
	 if [[ ! -d ${PACKAGE_FOLDER}/.profile.d/ ]]; then
	 		mkdir -p ${PACKAGE_FOLDER}/.profile.d/
	 fi
   cp -f ${WORKSPACE}/config/env/${ENVIRONMENT}/*.sh ${PACKAGE_FOLDER}/.profile.d/ || error_exit "Failed to copy shell script for redis tunnel setup"
   cp -f ${WORKSPACE}/config/env/${ENVIRONMENT}/id_rsa ${PACKAGE_FOLDER}/ || error_exit "Failed to copy keys for redis tunnel setup"
   echo "[INFO] Including redis tunnel config in package was successful"
else
    echo "[INFO] All the pre-requisites for redis tunnel setup were not met. Please check config and .profile.d folder"
fi
