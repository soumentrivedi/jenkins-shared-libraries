# print out stuff on stdout
set -x

# An error exit function

error_exit()
{
	echo "[ERROR] : $1" 1>&2
	exit 1
}

GREEN_APP_NAME=${1}
PROD_HOST=${2}
PROD_DOMAIN_LIST=${3}
TEST_DOMAIN=${4}
TEST_HOST=${5}

echo "******* Switching routes from Blue to Green App *****************"
echo "[INFO] : Mapping route for Green App : ${GREEN_APP_NAME}"
PROD_DOMAIN_LIST=($(echo $PROD_DOMAIN_LIST | tr -d ',' | tr -d ']' | tr -d '['))
PROD_DOMAIN="${PROD_DOMAIN_LIST[0]}"
#find app with production route
app_with_route=`cf routes | grep -w ${PROD_DOMAIN} | grep -w ${PROD_HOST} | awk '{print $4}' | tr ',' ' '`
multiple_apps=`echo $app_with_route | grep " "`

if [[ ! -z $multiple_apps ]]; then
		error_exit "Multiple apps were found on the production route: $app_with_route. Cannot Handle This!!!"
fi

if [[ ! -z $app_with_route ]]; then

	echo "[INFO] : Mapping Production route ${PROD_HOST}.${PROD_DOMAIN} for App : ${GREEN_APP_NAME}"
	# map the production route
	cf map-route ${GREEN_APP_NAME} ${PROD_DOMAIN} -n ${PROD_HOST} || error_exit "Failed to create map route"
	# unmap the test green app route
	cf unmap-route ${GREEN_APP_NAME} ${TEST_DOMAIN} -n ${TEST_HOST}

	# assuming there would be only one app
	cf set-env ${GREEN_APP_NAME} "ROLLBACK" ${app_with_route}|| error_exit "Failed to set-env : ${GREEN_APP_NAME}"

	# re-starting app for the env variable to take effect
	cf restart ${GREEN_APP_NAME} || error_exit "Failed to Re-Stage App : ${GREEN_APP_NAME}"

	echo "[INFO] : Unmapping route ${PROD_HOST}.${PROD_DOMAIN} for App : ${app_with_route}"
	cf unmap-route ${app_with_route} ${PROD_DOMAIN} -n ${PROD_HOST} || error_exit "Failed to unmap route for ${app_with_route}"

	# sleep 30 for current transactions to completed
	sleep 30

	cf set-env ${app_with_route} "ROLLBACK" ${GREEN_APP_NAME}|| error_exit "Failed to set-env : ${app_with_route}"
	cf stop ${app_with_route} || error_exit "Failed to stop App : ${app_with_route}"

	# deleting test route
	cf delete-route -f ${TEST_DOMAIN} -n ${TEST_HOST}
else
	error_exit "No Blue App Found to do Blue/Green. May be you just need deploy-blue"
fi
