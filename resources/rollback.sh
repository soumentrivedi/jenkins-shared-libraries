# print out stuff on stdout
set -x

# An error exit function

error_exit()
{
	echo "[ERROR] $1" 1>&2
	exit 1
}

ROLLING_BACK_APP_NAME=${1}
PROD_HOST=${2}
PROD_DOMAIN_LIST=${3}

# check all the required params were recieved
if [[ ! -z ${ROLLING_BACK_APP_NAME} && ! -z ${PROD_HOST} && ! -z ${PROD_DOMAIN_LIST} ]]; then
	PROD_DOMAIN_LIST=($(echo $PROD_DOMAIN_LIST | tr -d ',' | tr -d ']' | tr -d '['))
	PROD_DOMAIN="${PROD_DOMAIN_LIST[0]}"
	echo "******* Switching routes from Blue to Rollback App *****************"
	echo "[INFO] : Mapping route for Rollback App : ${ROLLING_BACK_APP_NAME}"

	# read current apps env props to get rollback appname
	rollback_app_name=`cf env ${ROLLING_BACK_APP_NAME}| grep -w 'ROLLBACK' | cut -d: -f2 | sed -e 's/^[ \s]*//'`

	if [[ ! -z ${rollback_app_name} ]]; then
		is_rollback_available=`cf apps | awk '{print $1}' | grep -w ${rollback_app_name}`
		if [[ -z ${is_rollback_available} ]]; then
			# rollback app was not found in the space
			error_exit "Could not find ROLLBACK app: ${rollback_app_name} in the space"
		fi
	else
		# could not read rollback version
		error_exit "Could not determine ROLLBACK app name from the current live app : ${ROLLING_BACK_APP_NAME}"
	fi

	# determine route of the current app. This will be mapped to the rollback app later.
	prod_comp_route=`cf app ${ROLLING_BACK_APP_NAME} | grep -w 'routes' | cut -d: -f2 | sed -e 's/^[ \s]*//'`
	prod_host=`echo ${prod_comp_route%%.*}`
	prod_domain=`echo ${prod_comp_route#*.}`

	# check if route determination was successful
	if [[ -z ${prod_comp_route} || -z ${prod_host} || -z ${prod_domain} ]]; then
		# rollback app not available
		error_exit "Rollback App was not found in Space. Please Re-Deploy the Rollback App."
	fi

	# starting the rollback app
	cf start ${rollback_app_name} || error_exit "Failed to start the Rollback App : ${rollback_app_name}"

	# Since the App was started successfully. Now map the production route
	cf map-route ${rollback_app_name} ${prod_domain} -n ${prod_host} || error_exit "Failed to create map route for : ${rollback_app_name}"

	# Unmap routes from the app whick being rolled back
	cf unmap-route ${ROLLING_BACK_APP_NAME} ${prod_domain} -n ${prod_host} || error_exit "Failed to create unmap route for : ${ROLLING_BACK_APP_NAME}"

	# putting in a sleep 30 for current transactions to be completed
	sleep 30

	# Delete the bad app, as ASG can use it for rollback
	cf delete ${ROLLING_BACK_APP_NAME} -f || error_exit "Failed to Delete Bad App after rollback : ${ROLLING_BACK_APP_NAME}"
else
	error_exit "All the required params were not recieved by the script"
fi
