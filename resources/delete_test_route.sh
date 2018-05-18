# this script will be used to do adhoc operations like
# 1. Deleting test route

# print out stuff on stdout
set -x

# An error exit function

error_exit()
{
	echo "[ERROR] : $1" 1>&2
	exit 1
}

APP_NAME=${1}
HOST_NAME=${2}
TEST_DOMAIN=${3}

TAB=$'\t'
# get test route for the given app
routes=`cf app ${APP_NAME} | grep routes | cut -d':' -f2 | sed -e 's/^[ ${TAB}]*//' | tr ',' ' '`

# check if there are multiple apps and only remove mybluemix.net route
for route in $routes
do
  echo $route | grep ${TEST_DOMAIN}
  if [[ $? -eq 0 ]];then
    # route in on mybluemix.net
    cf delete-route $TEST_DOMAIN -n $HOST_NAME -f || error_exit "Failed to delete route : ${route}"
  fi
done
