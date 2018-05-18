# print out stuff on stdout
set -x

ENVIRONMENT=$1
PACKAGE_NAME=$2
DO_ZIP="N"

# An error exit function
error_exit()
{
	echo "[ERROR] : $1" 1>&2
	exit 1
}

update_serverxml() {
	if [[ (-f $1) && $(grep 'location' $1 | grep war) ]]; then
		echo "Found server.xml in config"
		search=`cat $1 | grep .war | cut -d'"' -f2`
		app_path_in_liberty_zip=$(zipinfo -1 $PACKAGE_NAME | grep .war)
		replace=${app_path_in_liberty_zip##*/}
		echo "Replacing $search with $replace in $1"
		perl -pi -e "s/$search/$replace/g" $1
	fi
}
insert_liberty_files(){
	# copy for both default and env specific
  pushd ${WORKSPACE}
      ls $1
      if [ -d wlp ]; then rm -rf wlp; fi
      mkdir -p wlp/usr/servers/LibertyServer
      cp -Rf $1 wlp/usr/servers/LibertyServer/
  popd
}



if ls ${WORKSPACE}/config/env/default/liberty 1> /dev/null 2>&1; then
  echo "[INFO] : No environment specific liberty files found. Using common liberty files under ${WORKSPACE}/config/env/default/liberty"
  insert_liberty_files "config/env/default/liberty/*"
	update_serverxml "config/env/default/liberty/server.xml"
	DO_ZIP="Y"
  echo "[INFO] : Including liberty files into package ${PACKAGE_NAME}/liberty"
else
	echo "[INFO] : No default liberty files found in Config Artifact. Wont be included into Liberty package before CF PUSH"
fi

if ls ${WORKSPACE}/config/env/${ENVIRONMENT}/liberty 1> /dev/null 2>&1; then
  echo "[INFO] : Found liberty files under ${WORKSPACE}/config/env/${ENVIRONMENT}/liberty"
  insert_liberty_files "config/env/${ENVIRONMENT}/liberty/*"
	update_serverxml "config/env/${ENVIRONMENT}/liberty/server.xml"
	DO_ZIP="Y"
  echo "[INFO] : Including liberty files into package ${PACKAGE_NAME}/liberty"
else
  echo "[INFO] : No environment specific liberty found in Config Artifact. Wont be included into Liberty package before CF PUSH"
fi

# zip only once
if [[ ${DO_ZIP} == "Y" ]]; then
	zip -mr ${PACKAGE_NAME} wlp/usr/servers/LibertyServer/* || error_exit "Failed to include liberty files in Liberty Package"
	unzip -l ${PACKAGE_NAME}
fi
