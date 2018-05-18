# FFPL-944 : Update APIC as part of the pipeline
# print out stuff on stdout
set -x

# An error exit function

error_exit()
{
	echo "[ERROR] : $1" 1>&2
	# restore project package json in worskpace
	if [[ -f ${WORKSPACE}/package_tmp.json ]]; then
		mv ${WORKSPACE}/package_tmp.json ${WORKSPACE}/package.json
	fi
	exit 1
}

APIC_SERVER=${1}
APIC_SPACE=${2}
APIC_ORG=${3}
APIC_CATALOG=${4}
APIC_DOMAIN=${5}
ARTIFACT_VERSION=${6}
ARTIFACT_NAME=${7}
BUILD_TYPE=${8}
APIC_URL_PREFIX=${9}

if [[ $APIC_SERVER != "" && ! -z $APIC_SERVER && $APIC_SPACE != "" && ! -z $APIC_SPACE
      && $APIC_ORG != "" && ! -z $APIC_ORG && $APIC_CATALOG != "" && ! -z $APIC_CATALOG
			&& $APIC_DOMAIN != "" && ! -z $APIC_DOMAIN && $BUILD_TYPE != "" && ! -z $BUILD_TYPE ]]; then

	if [[ ${BUILD_TYPE} == "maven" ]]; then
		  APICTitle=`echo ${ARTIFACT_NAME} | sed 's|-| |g'`
			PRODUCT_VERSION=`echo $ARTIFACT_VERSION | cut -d'-' -f1 | awk -F. '{print $1 "-" $2 "-" $3}'`
			MAJOR_VERSION=`echo $ARTIFACT_VERSION | awk -F. '{print $1}'`
			BUILD_NUMBER=`echo $ARTIFACT_VERSION | awk -F- '{print $3}'`
			APIC_VERSION=`echo $ARTIFACT_VERSION | cut -d'-' -f1`

			# non-prod will have build number and prod won't
			if [[ ! -z $BUILD_NUMBER ]]; then
				PRODUCT_VERSION=`echo ${PRODUCT_VERSION}-${BUILD_NUMBER}`
				APIC_VERSION=`echo ${APIC_VERSION}.${BUILD_NUMBER}`
			fi

			target_folder="${WORKSPACE}/target"
	else
		# convert hyphen separated to space separated
		APICTitle=`echo ${ARTIFACT_NAME} | sed 's|-| |g'`

		# create PRODUCT_VERSION=MAJOR-MINOR FORMAT
		PRODUCT_VERSION=`echo $ARTIFACT_VERSION | awk -F. '{print $1 "-" $2 "-" $3}'`

		# create PRODUCT_VERSION=MAJOR.MINOR FORMAT
		APIC_VERSION=`echo $ARTIFACT_VERSION`
		target_folder="${WORKSPACE}/node_modules/${ARTIFACT_NAME}/target"
	fi

	SPACE_PARAMS="--scope space --space ${APIC_SPACE}"
	ENV_MODE=${APIC_CATALOG}

	# if APIC_URL_PREFIX was not declared in Jenkinsfile use catalog name
	if [[ ! -z $APIC_URL_PREFIX && $APIC_URL_PREFIX != null ]]; then
		ENV_MODE=${APIC_URL_PREFIX}
	fi

	# if catalog is sb, the apic commands don't need space params
	if [[ ${APIC_CATALOG} == "sb" ]]; then
			SPACE_PARAMS=""
			ENV_MODE="sandbox"
	fi


	# var to store previous apic product version
	PREV_VERSION_PRODUCT=""
	# format mc-bff-messages-product-12:v1.2 in kp:dit:message-center
	apic_product_name=${ARTIFACT_NAME}-product-$PRODUCT_VERSION


	for product in `apic products ${SPACE_PARAMS} --catalog ${APIC_CATALOG} --organization ${APIC_ORG} --server ${APIC_SERVER} | awk '{print $1}' | grep -i "${ARTIFACT_NAME}-product-${MAJOR_VERSION}"`
	do
		product=`echo $product | awk -F: '{print $1}'`
		echo $product
		status=`apic products:get $product ${SPACE_PARAMS} --catalog ${APIC_CATALOG} --organization ${APIC_ORG} --server ${APIC_SERVER} | grep "status:"`
		if [[ $status =~ "published" ]]; then
				# we have a version that is published
				PREV_VERSION_PRODUCT=$product
				#PREV_VERSION=${PREV_VERSION_PRODUCT##*-}
		fi
	done



	if [[ $apic_product_name != $PREV_VERSION_PRODUCT ]]; then
		# tempoarily renaming package.json to overcome following error:
		# Error: Refusing to install project as a dependency of itself

		if [[ ${BUILD_TYPE} == "npm" ]]; then
			mv ${WORKSPACE}/package.json ${WORKSPACE}/package_tmp.json

			# intall project versioned artifact
			npm install ${ARTIFACT_NAME}@${ARTIFACT_VERSION}
			cd ${WORKSPACE}/node_modules/${ARTIFACT_NAME} || error_exit "Node Modules not found !!"

			# remove existing copies if any
			rm -rf ${target_folder}
		fi

		# to get project dependencies
		npm install

		node ./node_modules/build-plugin-apic-deploy --envRunMode "${ENV_MODE}" --bmxProdDomain "${APIC_DOMAIN}" || error_exit "Node build-plugin-apic-deploy failed !!"

		cd ${target_folder} || error_exit "Target folder not found !!"
		echo "**********************"
		cat apiconnect.hbs
		echo "**********************"


		echo "[INFO] :: APIC Title computed :: ${APICTitle}"
		apic create  --type api --template apiconnect.hbs --title "$APICTitle API $PRODUCT_VERSION" --filename ${ARTIFACT_NAME}-api.yaml || error_exit "Creating API file ${ARTIFACT_NAME}-api.yaml!!"
		apic validate  ${ARTIFACT_NAME}-api.yaml
		apic create  --type product  --title "$APICTitle Product $PRODUCT_VERSION" --version "v$APIC_VERSION" --apis ${ARTIFACT_NAME}-api.yaml --filename ${ARTIFACT_NAME}-product.yaml || error_exit "Creating API file ${ARTIFACT_NAME}-product.yaml!!"
		apic validate ${ARTIFACT_NAME}-product.yaml --product-only

		echo "*********************"
		cat ${ARTIFACT_NAME}-api.yaml
		echo "*********************"

		echo "*********************"
		cat ${ARTIFACT_NAME}-product.yaml
		echo "*********************"

		if [[ $PREV_VERSION_PRODUCT != "" ]]; then
				# need to do hot deploy
				apic publish --stage ${ARTIFACT_NAME}-product.yaml  ${SPACE_PARAMS} --catalog ${APIC_CATALOG} --organization ${APIC_ORG} --server ${APIC_SERVER} || error_exit "Staging for ${apic_product_name} failed!!"
				echo "[INFO] :: APIC Staging done before HOT DEPLOY"
				apic products:replace ${PREV_VERSION_PRODUCT} ${apic_product_name} --plans default:default ${SPACE_PARAMS} --catalog ${APIC_CATALOG} --organization ${APIC_ORG} --server ${APIC_SERVER} || error_exit " Hot Replace for ${PREV_VERSION_PRODUCT} -> ${apic_product_name} failed!!"
				echo "[INFO] :: APIC Hot Replace for ${PREV_VERSION_PRODUCT} -> ${apic_product_name} successful"
		else
				# no previous published products. will just publish
				apic publish ${ARTIFACT_NAME}-product.yaml ${SPACE_PARAMS} --catalog ${APIC_CATALOG} --organization ${APIC_ORG} --server ${APIC_SERVER} || error_exit "Publishing APIC!!"
				echo "[INFO] :: APIC Publish is successfully completed"
		fi

	else
		echo "[ERROR] : Found the same product version on APIC. To deploy new changes product version needs to change."
		exit 1
	fi

	# restore project package json in worskpace
	if [[ -f ${WORKSPACE}/package_tmp.json ]]; then
		mv ${WORKSPACE}/package_tmp.json ${WORKSPACE}/package.json
	fi
else
	echo "[ERROR] : Required params for APIC update were not found"
fi
