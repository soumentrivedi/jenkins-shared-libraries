# Jenkins Pipeline Shared Libraries for FOTF-Bluemix Pipeline

***Jenkins Shared Libraries for FOTF-Bluemix Pipeline***

----

## Documentation

Documentation for Bluemix Jenkins FOTF pipeline
[Confluence-Bluemix-Documentation](https://confluence-fof.appl.kp.org/display/FF/FOTF+-+Bluemix+Pipeline)

----

## Usage

These shared libraries may be accessed in bluemix pipline :
1. Include this library in an `@Library` statement in a Pipeline script.

### Within Pipeline Script

To include libraries in Bluemix pipeline in all script dynamically by adding it to the `@Library` block within a Pipeline script on top.

```
@Library('jenkins-shared-libraries@master') _
```
----
### Sonar Quality gate
1. Add the application name in sonar_quality_gates.yml
   a. To mandate the Quality gate for 'productzero-node' applications we have to add jenkins repo and application name
   
```
applications:
  product-zero-pipeline: 
     - productzero-node
     
```
   b.To mandate the Quality gate for all the applications in specific jenkins repo

```
applications:
  product-zero-pipeline:
     - all
     
```
----

## Sample Jenkinsfile Templates

1. [Maven](Jenkinfile_Maven.Template)
2. [Node](Jenkinfile_NodeJS.Template)
3. [NodeLibraries](Jenkinsfile_NodeLibraries.Template)
4. [Config](Jenkinsfile.Config.Template)

----
## Getting help

If you have any trouble working with this repo, please contact Hipchat FOTF - DevOps Room.

----
## Getting help for enhancements

Got a new idea or enhancement you would like to suggest. Please raise FOTF Pipeline ServiceDesk request [Enhancement FOTF-ServiceDesk](https://jira-fof.appl.kp.org/servicedesk/customer/portal/4/group/32)

----
## Useful Declarative Pipeline Syntax Parameters
```agent - Specifies jenkins slave node where you want to run the jobs. Can be defined per Stage as well. Usage:
agent { label '<label_matching_node_in_jenkins>' }

tools - Specify global tool configurations. For eg.
    tools {
        maven 'maven-3.3.9' //label should match Jenkins Global configuration name.
      }

stages - Defines different phases of a Pipeline. For eg. Build/CodeQuality/Deploy/Test.
steps - These are the steps defined within a stage. This could be a shared library call or specific executions depending on the available plugins:[Plugins](https://jenkins.io/doc/pipeline/steps/)

when - Based on the when condition a particular stage will either trigger or skip.
```
----