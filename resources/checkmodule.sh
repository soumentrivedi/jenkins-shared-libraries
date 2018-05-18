# using this script to validate if we have test-suite module in project
set -x
MAVEN_HOME=${1}
MAVEN_MODULE_NAME=${2}

${MAVEN_HOME}/bin/mvn help:evaluate -Dexpression=project.modules | grep -v "^\\[" | grep -v "<\/*strings>" | sed "s/<\/*string>//g" | sed "s/[[:space:]]//" | grep "${MAVEN_MODULE_NAME}" | cat
