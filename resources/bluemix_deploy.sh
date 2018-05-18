# print out stuff on stdout
set -x

# An error exit function

error_exit()
{
	echo "[Error] : $1" 1>&2
	exit 1
}


echo "************* Deploying to Bluemix ****************"
echo -n > ${WORKSPACE}/instruction.txt
BLUE_MIX_NAME="${1}"
BLUE_MIX_NAME_VERSIONED="${2}"
BLUE_MIX_HOST="${3}"
BLUE_MIX_APP_DOMAIN_LIST="${4}"
MANIFEST_FILE_PATH="${6}"
BLUE_MIX_SERVICES="${7}"
PATH_TO_PUBLISH_ARTIFACT="${8}"
IS_PROD="${9}"
APP_TYPE="${10}"
ORIGINAL_ROUTE="${11}"
AUTOSCALING_FLAG="${12}"
BUILD_PACK="${13}"

#  check if all the mandatory varaiables are set
if [[ ! -z ${BLUE_MIX_NAME} && ${BLUE_MIX_NAME} != null && ! -z ${BLUE_MIX_NAME_VERSIONED} && ${BLUE_MIX_NAME_VERSIONED} != null
			&& ! -z ${BLUE_MIX_HOST} && ${BLUE_MIX_HOST} != null && ! -z ${BLUE_MIX_APP_DOMAIN_LIST}
      && ${BLUE_MIX_APP_DOMAIN_LIST} != null && ! -z ${MANIFEST_FILE_PATH} && ! -z ${BLUE_MIX_SERVICES}
			&& ! -z ${PATH_TO_PUBLISH_ARTIFACT} && ! -z ${IS_PROD} && ! -z ${APP_TYPE} && ${APP_TYPE} != null
			&& ! -z ${BUILD_PACK} && ${BUILD_PACK} != null ]]; then

				echo "************* Pushing Application to Bluemix ****************"
				BLUE_MIX_SERVICES=$(echo $BLUE_MIX_SERVICES | tr -d ',' | tr -d ']' | tr -d '[')
				BLUE_MIX_APP_DOMAIN_LIST_FINAL=($(echo $BLUE_MIX_APP_DOMAIN_LIST | tr -d ',' | tr -d ']' | tr -d '['))
				BLUE_MIX_APP_DOMAIN="${BLUE_MIX_APP_DOMAIN_LIST_FINAL[0]}"

				# determine if the target has the appropriate buildpack
				cf_build_pack=$(cf buildpacks | grep $BUILD_PACK | awk '{print $1}' | head -n1) || error_exit "Failed to determine build pack name in target for : ${BUILD_PACK}"

				# exit if no matching build pack found in target
				if [[ -z $cf_build_pack ]]; then
					error_exit "No Matching Build Pack found in Bluemix Target for : ${BUILD_PACK}"
				fi

				# cf delete ${BLUE_MIX_NAME_VERSIONED} -f -r
				# list all apps in space
				cf apps

				# check if any other apps have the same route. If they do unmap and stop those apps
				apps_with_route=`cf routes | grep -w ${BLUE_MIX_APP_DOMAIN} | grep -w ${BLUE_MIX_HOST} | awk '{print $4}' | tr ',' ' ' | tr -d '\n'`

				multiple_apps=`echo $apps_with_route | grep " "`

				if [[ ! -z $multiple_apps ]]; then
						error_exit "Multiple apps were found on the production route: $apps_with_route. Cannot Handle This!!!"
				fi

				# When we deploy green builds find live app and get rollback app name for deletion
				if [[ ${APP_TYPE} == "green" && ! -z ${ORIGINAL_ROUTE} ]]; then
						find_live_app=`cf apps | grep -w ${ORIGINAL_ROUTE} | awk '{print $1}'`
						get_rollback_appname=`cf env ${find_live_app}| grep -w 'ROLLBACK' | cut -d: -f2 | sed -e 's/^[ \s]*//'`
						# was able to determine rollback app on live route
						# FFPL-1099 : adding check to make sure app being deleted in not the currect blue app
						if [[ (! -z ${get_rollback_appname}) && (${get_rollback_appname} != ${find_live_app}) ]]; then
							echo "[INFO] : Deleting Rollback App : ${get_rollback_appname}"
							cf delete ${get_rollback_appname} -f || error_exit "Failed to delete old app : ${get_rollback_appname}"
						fi
				fi

				# creating services before app push
				echo "************************ Creating Services **********************"
				# determine dynatrace api details based on prod vs non-prod and create service
				if [[ $IS_PROD == true ]]; then
							echo "${BLUE_MIX_NAME} :: Choosing Production Dynatrace service"
							DYNATRACE_SERVICE_NAME="smon_prod_dynatrace"
							serviceStatus=`cf services | grep -w ${DYNATRACE_SERVICE_NAME} | wc -l` || error_exit "Failed to query for  for PROD service ${DYNATRACE_SERVICE_NAME} !!"
							if [[ ${serviceStatus} -eq 0 ]]; then
		              echo "**** DYNATRACE service not present CREATING ${DYNATRACE_SERVICE_NAME} for PROD****"
		              cf cups ${DYNATRACE_SERVICE_NAME} -p '{ "apiurl": "https://dynatrace-paas.appl.kp.org/e/ea96fd35-4a91-4571-9999-391fba61c917/api", "environmentid": "ea96fd35-4a91-4571-9999-391fba61c917", "apitoken": "-H0K6eg4TryKNPF0zwRAw" }' || error_exit "Failed to create service for ${DYNATRACE_SERVICE_NAME} !!"
		              # Create a single service instance for Dynatrace with the name dynatrace as a substring
		          else
		              echo "*** DYNATRACE service present UPDATING ${DYNATRACE_SERVICE_NAME} for PROD****"
		              cf uups ${DYNATRACE_SERVICE_NAME} -p '{ "apiurl": "https://dynatrace-paas.appl.kp.org/e/ea96fd35-4a91-4571-9999-391fba61c917/api", "environmentid": "ea96fd35-4a91-4571-9999-391fba61c917", "apitoken": "-H0K6eg4TryKNPF0zwRAw" }' || error_exit "Failed to update service for ${DYNATRACE_SERVICE_NAME} !!"
		              # update a single service instance for Dynatrace with the name dynatrace as a substring
		          fi
				else
							echo "${BLUE_MIX_NAME} :: Choosing Non-Production Dynatrace service"
							DYNATRACE_SERVICE_NAME="smon_non_prod_dynatrace"
							serviceStatus=`cf services | grep -w ${DYNATRACE_SERVICE_NAME} | wc -l` || error_exit "Failed to query for  for NON PROD service ${DYNATRACE_SERVICE_NAME} !!"
							if [[ ${serviceStatus} -eq 0 ]]; then
		              echo "**** DYNATRACE service not present CREATING ${DYNATRACE_SERVICE_NAME} for NON PROD****"
		              cf cups ${DYNATRACE_SERVICE_NAME} -p '{ "apiurl": "https://dynatrace-paas.appl.kp.org/e/183e7a38-c2ef-43c6-a918-3f496f52472b/api", "environmentid": "183e7a38-c2ef-43c6-a918-3f496f52472b", "apitoken": "NF5s3NebQeOpt3qtPFp6E" }' || error_exit "Failed to create service for ${DYNATRACE_SERVICE_NAME} !!"
		              # Create a single service instance for Dynatrace with the name dynatrace as a substring
		          else
		              echo "*** DYNATRACE service present UPDATING ${DYNATRACE_SERVICE_NAME} for NON PROD ****"
		              cf uups ${DYNATRACE_SERVICE_NAME} -p '{ "apiurl": "https://dynatrace-paas.appl.kp.org/e/183e7a38-c2ef-43c6-a918-3f496f52472b/api", "environmentid": "183e7a38-c2ef-43c6-a918-3f496f52472b", "apitoken": "NF5s3NebQeOpt3qtPFp6E" }' || error_exit "Failed to update service for ${DYNATRACE_SERVICE_NAME} !!"
		              # update a single service instance for Dynatrace with the name dynatrace as a substring
		          fi
				fi

				# pushing the app to bluemix
				echo "*********************** Build Artifacts to be Uploaded to BlueMix ***************************"
				#Push App to Bluemix
        if [[ -f ${MANIFEST_FILE_PATH} ]]; then
					# navigate to the artifactory unzip path
					if [[ -d $PATH_TO_PUBLISH_ARTIFACT ]] || [[ -e $PATH_TO_PUBLISH_ARTIFACT ]]; then
							cd $PATH_TO_PUBLISH_ARTIFACT
							if [[ -f "../.npmrc" ]]; then
									echo "Copying .npmrc file to override BlueMix NPM_CONFIG_REGISTRY"
									cp ../.npmrc .
							fi
					else
						  echo "Package folder not found. This folder hosts artifact for cf push"
						  exit -1
					fi

				# List files / folder before doing push
				pwd
				ls -la
				cf push -f ${MANIFEST_FILE_PATH} -p $PATH_TO_PUBLISH_ARTIFACT -b $cf_build_pack --no-start --no-route || error_exit "Failed to push application to Bluemix"
      else
        echo "No manifest.yml Found"
        exit -1
      fi


      # Bind Service for Dynatrace, Splunk and Auto-scaling which are common to the platform
      cf bind-service ${BLUE_MIX_NAME_VERSIONED} ${DYNATRACE_SERVICE_NAME} >> ${WORKSPACE}/instruction.txt || error_exit "Failed to bind service ${DYNATRACE_SERVICE_NAME}"

			# autoscaling
			# extract the manifest file and append autoscaling json to it
			AUTO_SCALING_CONFIG=`echo ${MANIFEST_FILE_PATH%/*}/autoscaling.json`

			if [[ $AUTOSCALING_FLAG == true ]]; then
				if [[ -f ${AUTO_SCALING_CONFIG} ]]; then
						check_bx_installed=`bluemix plugin list | grep 'auto-scaling'`
						if [[ -z $check_bx_installed ]]; then
								bluemix plugin install https://artifactory-fof.appl.kp.org/artifactory/kp-external/bx-autoscaling/auto-scaling-linux-amd64-0.2.2 || error_exit "Failed to install auto-scaling plugin"
					  fi
						bx as policy-attach ${BLUE_MIX_NAME_VERSIONED} -p ${AUTO_SCALING_CONFIG} || error_exit "Failed to attach auto-scaling policy"
				else
						error_exit "autoscaling.json not found, Failed to attach auto-scaling policy."
				fi
			fi

			# if rollback app available set in env variable
			if [[ ! -z $apps_with_route && ! ${APP_TYPE} == "green" ]]; then
				get_rollback_appname=`cf env ${apps_with_route}| grep -w 'ROLLBACK' | cut -d: -f2 | sed -e 's/^[ \s]*//'`
				if [[ ! -z ${get_rollback_appname} ]]; then
						cf set-env ${BLUE_MIX_NAME_VERSIONED} "ROLLBACK" ${get_rollback_appname} || error_exit "Failed to set-env : ${BLUE_MIX_NAME_VERSIONED}"
				fi
			fi

			# Start the APP
      cf start ${BLUE_MIX_NAME_VERSIONED} || error_exit "Failed to start Application"
			sleep 45

			# mapping production route
			if [[ ! -z $BLUE_MIX_APP_DOMAIN_LIST_FINAL && $BLUE_MIX_APP_DOMAIN_LIST_FINAL != null ]]; then
				for DOMAIN in ${BLUE_MIX_APP_DOMAIN_LIST_FINAL[@]}; do
					cf map-route ${BLUE_MIX_NAME_VERSIONED} ${DOMAIN} -n ${BLUE_MIX_HOST} || error_exit "Failed to create map route"
				done
			fi

			# Unmap routes and delete any other apps in the space.
			if [[ (! -z ${apps_with_route}) && (${apps_with_route} != ${BLUE_MIX_NAME_VERSIONED}) ]]; then
					# un-mapping production route from from any other apps in the space.
					# After un-map these apps will be deleted as well
					echo "[INFO] : Unmapping route ${BLUE_MIX_HOST}.${BLUE_MIX_APP_DOMAIN} for App : ${apps_with_route}"
					cf unmap-route ${apps_with_route} ${BLUE_MIX_APP_DOMAIN} -n ${BLUE_MIX_HOST} || error_exit "Failed to unmap route for : ${apps_with_route}"
					# sleeping 30 for transactions to complete
					sleep 30
					cf stop ${apps_with_route} || error_exit "Failed to stop app : ${apps_with_route}"
					cf delete ${apps_with_route} -f
		  fi

else
      error_exit "****** All the required params were not recieved by this script *******"
fi
