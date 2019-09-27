package com.mycompany.app;

/**
 * Hello world!
 *
 */
public class App 
{
    public static void main( String[] args )
    {
        System.out.println( "Hello World!" );
    }
}


@Library('shared-lib@master') _

pipeline{
	agent {
		node{
			label 'master'
			customWorkspace "/opt/data_original/Jenkins/pipeline/${env.JOB_BASE_NAME}" 
		}
	}
	parameters {
        string(defaultValue: 'releasename', description: '', name: 'RELEASE_NAME')
        string(defaultValue: '', description: '', name: 'TRANSACTION_ID')
        string(defaultValue: '', description: '', name: 'REQUEST_ID')
        string(defaultValue: 'entityname', description: '', name: 'ENTITY_NAME')
        string(defaultValue: 'artifactname', description: '', name: 'ARTIFACT_NAME')
        string(defaultValue: 'DEV', description: '', name: 'ENVIRONMENT')
        string(defaultValue: "${JOB_BASE_NAME}_${BUILD_NUMBER}", description: '', name: 'SCM_TAG')
        string(defaultValue: 'appspecific', description: '', name: 'APP_SPECIFIC')
        string(defaultValue: 'BUILD', description: '', name: 'REQUEST_TYPE')
        string(defaultValue: 'http://172.16.86.58:8998/ARTClient/actions/response', description: '', name: 'RESPONSE_URL')
        string(defaultValue: 'feature/eap71-dev', description: '', name: 'SCM_BRANCH')
        string(defaultValue: 'a', description: '', name: 'SWIMLN')
        booleanParam(defaultValue: true, description: '', name: 'skipTests')
    }
	environment{
		warname='cap-legacy.war'
		projectName="${JOB_BASE_NAME}"
		PHANTOMJS_BIN='/opt/apps/phantomjs-2.1.1-linux-x86_64/bin/phantomjs'
		artifactory_path="libs-release-local-origin/com/bestbuy/bbym/$JOB_BASE_NAME/$SCM_TAG/"
        artifactory_URL="https://code.bestbuy.com/artifactory/${env.artifactory_path}"
		sonar_url="http://ptl01ce3ap05c:9000"
		cvsRootPath="${WORKSPACE}"
		bincapdir='target'
		region='US'
		service='Carrier'
		IsPatch="${IsPatch}"
		regionDirectory='/repository/'
		binRepository='/binRepoWar'
		batchFilePath='/batchJobs'
		batchPathDir="${WORKSPACE}/build/deployment/common/purgescripts"
		SCM_TAG="${SCM_TAG}"
		sonar_branch="${RELEASE_NAME}"
		skipInitially='false'
		genRepo='true'
		destDir="${WORKSPACE}/target"
		env="SQUADTEST"
	}
	stages{
		stage('checkout')
                {
                        steps
                        {
                                script
                                {
                                        if (params.REQUEST_TYPE == 'BUILD')
                                        {
                                                git branch: "${SCM_BRANCH}", url: 'ssh://git@git.bestbuy.com/vip/cap.git'
                                        }
                                }
                        }
                }

		stage('Build'){
			steps
			{
				script
				{
					if (params.REQUEST_TYPE == 'BUILD')
					{
						withMaven(maven: 'maven-3.5.0') 
						{
							buildWrapper args: 'exec:java -DcvsRootPath=${WORKSPACE} -DskipInitially=false -Dexec.mainClass=com.bestbuy.bbym.buildutils.BuildUtils "-Dexec.args=${WORKSPACE} ${regionDirectory} ${binRepository} ${region} ${ENVIRONMENT} ${batchPathDir} target Carrier" -DBUILD_VERSION=`echo ${SCM_TAG} | sed s/^[A-Za-z]*_//g`', pom:'-f build/pom.xml'	
						}
				
						withMaven(maven: 'maven-3.5.0') 
						{
							buildWrapper args: '-pl CAP -am org.jacoco:jacoco-maven-plugin:prepare-agent -Denvironment=${ENVIRONMENT} -DskipTests -DlogFilePath=${WORKSPACE}/CAP/target/test-classes/testLogs -Dregion=US -Dservice=Carrier -DdestDir="${WORKSPACE}/target" -Djavax.xml.validation.SchemaFactory:http://www.w3.org/2001/XMLSchema=com.saxonica.jaxp.SchemaFactoryImpl -Dcas.db.user=BST_CAPC_ONL03 -DcvsRootPath=${WORKSPACE}  -Dcas.db.url=jdbc:oracle:thin:@ldap://p01imap01.na.bestbuy.com:3060/d01bstcas01,cn=OracleContext,dc=world -DtestCategory=com.bestbuy.bbym.beast.cap.UsCarrierTests -Djavax.xml.transform.TransformerFactory=net.sf.saxon.TransformerFactoryImpl -Dcap.db.url=jdbc:oracle:thin:@ldap://pdl01t48ap02a.na.bestbuy.com:3060/d02bstcap01,cn=OracleContext,dc=world -DskipInitially=false -Dexec.mainClass=com.bestbuy.bbym.buildutils.BuildUtils -Dcap.db.user=BST_CAP_ONL02 -DBUILD_VERSION=`echo ${SCM_TAG} | sed s/^[A-Za-z0-9]*_//g`'
						}
						stepResponse TRANSACTION_ID: "$TRANSACTION_ID", REQUEST_ID: "$REQUEST_ID", STEP1: 'BUILD', STEP2: 'SONAR', RESPONSE_URL: '$RESPONSE_URL', JOB_URL1: "$BUILD_URL", JOB_URL2: "${sonar_url}"
					}
				}
			}
		}
      	stage('Checkmarx Scan')
       	{
      		steps
			{
         		script
				{
                  	if (params.REQUEST_TYPE == 'BUILD')
                  	{
                  		checkmarks comment: "\'{\"version\":\"$SCM_TAG\"}\'", projectName: "cdc-cap-legacy", excludeFolders: ".settings, batchJobs, build, capprocessor, lib, Routing, sql"
                    }
                }
            }
       }
		stage('Sonar') 
		{
			steps
			{
				script
				{
					if (params.REQUEST_TYPE == 'BUILD')
					{
						withMaven(maven: 'maven-3.5.0')
						{
							executeSonar releaseName: '${SCM_BRANCH}'
						}
						stepResponse TRANSACTION_ID: "$TRANSACTION_ID", REQUEST_ID: "$REQUEST_ID", STEP1: 'SONAR', STEP2: 'ARTIFACTORY', RESPONSE_URL: '$RESPONSE_URL', JOB_URL1: "$sonar_url", JOB_URL2: "${env.artifactory_URL}"
					}
				}
			}
		} 
		stage('Artifactory')
		{
			when{ expression { params.TRANSACTION_ID != '' } }
			steps
			{
				script
				{
					if (params.REQUEST_TYPE == 'BUILD')
					{
						artifactory pattern: 'target/$warname', target: "${env.artifactory_path}"
						artifactory pattern: 'target/repository.zip', target: '${artifactory_path}'
					}
                  	else
                    {
                      	downloadArtifacts pattern: "${env.artifactory_path}${warname}", target: "${WORKSPACE}/${warname}"
						downloadArtifacts pattern: "${env.artifactory_path}repository.zip", target: "${WORKSPACE}/repository.zip"
                    }
				}
              	stepResponse TRANSACTION_ID: "$TRANSACTION_ID", REQUEST_ID: "$REQUEST_ID", STEP1: 'ARTIFACTORY', STEP2: 'DEPLOY', RESPONSE_URL: '$RESPONSE_URL', JOB_URL1: "${env.artifactory_URL}", JOB_URL2: "$BUILD_URL"
			}
		}
		stage('deploy')
		{
			when{ expression { params.TRANSACTION_ID != '' } }
			steps
			{
				script
				{
					if(params.ENVIRONMENT == 'DEV')
					{
                        sh "ssh -q cimaster@dtl01capap01c \"[ -d /opt/appserver/deployments/squad-${SWIMLN}/stl01-${env.JOB_BASE_NAME}-${SWIMLN}-s01 ] || mkdir -p /opt/appserver/deployments/squad-${SWIMLN}/stl01-${env.JOB_BASE_NAME}-${SWIMLN}-s01\""
						sh "scp target/repository.zip dtl01capap01c:/opt/appserver/deployments/squad-${SWIMLN}/stl01-${env.JOB_BASE_NAME}-${SWIMLN}-s01"
						
						deployWrapper source: 'target/$warname', server: 'dtl01capap01c', instance: 'stl01-${JOB_BASE_NAME}-${SWIMLN}-s01', artifact: '$warname', target: '/opt/appserver/deployments/squad-${SWIMLN}/stl01-${JOB_BASE_NAME}-${SWIMLN}-s01'						
						
                      	tagBuild SCM_TAG: "$SCM_TAG"
					}
					else if (params.ENVIRONMENT == 'SQUADTEST')
					{
                      	sh "ssh -q cimaster@dtl01capap02c \"[ -d /opt/appserver/deployments/squad-${SWIMLN}/stl02-${env.JOB_BASE_NAME}-${SWIMLN}-s01 ] || mkdir -p /opt/appserver/deployments/squad-${SWIMLN}/stl02-${env.JOB_BASE_NAME}-${SWIMLN}-s01\""
						sh "scp target/repository.zip dtl01capap02c:/opt/appserver/deployments/squad-${SWIMLN}/stl02-${env.JOB_BASE_NAME}-${SWIMLN}-s01"
              
						deployWrapper source: 'target/$warname', server: 'dtl01capap02c', instance: 'stl02-${JOB_BASE_NAME}-${SWIMLN}-s01', artifact: '$warname', target: '/opt/appserver/deployments/squad-${SWIMLN}/stl02-${JOB_BASE_NAME}-${SWIMLN}-s01'
						
                      	tagBuild SCM_TAG: "$SCM_TAG"
					}
					else if (params.ENVIRONMENT == 'PRODTEST')
					{

						if (params.REQUEST_TYPE == 'BUILD') 
                      	{
                          	sh "ssh -q cimaster@ptl01capap01c \"[ -d /opt/appserver/deployments/ptl01-${JOB_BASE_NAME}${SWIMLN}-s01 ] || mkdir -p /opt/appserver/deployments/ptl01-${JOB_BASE_NAME}${SWIMLN}-s01\""
							sh "scp target/repository.zip ptl01capap01c:/opt/appserver/deployments/ptl01-${JOB_BASE_NAME}${SWIMLN}-s01"
                      		deployPTWrapper appName: '${JOB_BASE_NAME}${SWIMLN}', source: 'target/$warname', server: 'ptl01capap01c', artifact: '$warname', target: '/opt/appserver/deployments/ptl01-${JOB_BASE_NAME}${SWIMLN}-s01'
                      		tagBuild SCM_TAG: "$SCM_TAG"
                        }
                      	else
                        {
                          	sh "ssh -q cimaster@ptl01capap01c \"[ -d /opt/appserver/deployments/ptl01-${JOB_BASE_NAME}${SWIMLN}-s01 ] || mkdir -p /opt/appserver/deployments/ptl01-${JOB_BASE_NAME}${SWIMLN}-s01\""
							sh "scp com/bestbuy/bbym/${JOB_BASE_NAME}/$SCM_TAG/repository.zip ptl01capap01c:/opt/appserver/deployments/ptl01-${JOB_BASE_NAME}${SWIMLN}-s01"
                      
                      		deployPTWrapper appName: '${JOB_BASE_NAME}${SWIMLN}', source: 'com/bestbuy/bbym/${JOB_BASE_NAME}/$SCM_TAG/$warname', server: 'ptl01capap01c', artifact: '$warname', target: '/opt/appserver/deployments/ptl01-${JOB_BASE_NAME}${SWIMLN}-s01'
                        }
                      
                      	sh '''scp -q ptl01capwb01c:/opt/webserver/commonurls/cap${SWIMLN}.url.conf .; new_version=`echo $SCM_TAG |\\
                               awk -F\'_\' \'{print $2}\' | awk -F\'-\' \'{print $1}\'`; version=`cat cap${SWIMLN}.url.conf |\\
                               grep \'Location /CAP\' | awk -F\'_\' \'{print $3}\' |\\
                               sed "s/\\\\/services>//g"`; \\
                               if [[ ! $new_version =~ ^R[0-9][0-9]$ ]] || [[ ! $version =~ ^R[0-9][0-9]$ ]]; then \\
                               echo "New version not set or Not able to read the existing version -- Skipping step to update Common URLS. Please update manually"; exit 0; fi; \\
                               if [ "$new_version" = "$version" ]; then echo exit 0; \\
                               else sed -i "s/$version/$new_version/g" cap${SWIMLN}.url.conf ; \\
                               scp -q cap${SWIMLN}.url.conf ptl01capwb01c:/opt/webserver/commonurls; \\
                               scp -q cap${SWIMLN}.url.conf ptl01capwb02c:/opt/webserver/commonurls; \\
                               ssh -q ptl01capwb01c sudo -u wasuser1 /opt/appserver/JBOSS/scripts/autodeploy/start_jbcs.sh stop cap${SWIMLN}; \\
                               ssh -q ptl01capwb02c sudo -u wasuser1 /opt/appserver/JBOSS/scripts/autodeploy/start_jbcs.sh stop cap${SWIMLN}; \\
                               sleep 10; ssh -q ptl01capwb01c sudo -u wasuser1 /opt/appserver/JBOSS/scripts/autodeploy/start_jbcs.sh start cap${SWIMLN}; \\
                               ssh -q ptl01capwb02c sudo -u wasuser1 /opt/appserver/JBOSS/scripts/autodeploy/start_jbcs.sh start cap${SWIMLN}; fi; '''
                          	
					}
					else if (params.ENVIRONMENT == 'PERF')
					{
                      				sh "ssh -q cimaster@drl02capap09d \"[ -d /opt/appserver/deployments/prf01-${JOB_BASE_NAME}01-s01 ] || mkdir -p /opt/appserver/deployments/prf01-${JOB_BASE_NAME}01-s01\""
						sh "scp com/bestbuy/bbym/${JOB_BASE_NAME}/$SCM_TAG/repository.zip drl02capap09d:/opt/appserver/deployments/prf01-${JOB_BASE_NAME}01-s01"
                      
                      				deployPTWrapper appName: '${JOB_BASE_NAME}01', source: 'com/bestbuy/bbym/${JOB_BASE_NAME}/$SCM_TAG/$warname', server: 'drl02capap09d', artifact: '$warname', target: '/opt/appserver/deployments/prf01-${JOB_BASE_NAME}01-s01'
					}
					else if (params.ENVIRONMENT == 'PRODLIKE')
					{
						sh "ssh -q cimaster@drl02capap09d \"[ -d /opt/appserver/deployments/drl01-${JOB_BASE_NAME}01-s01 ] || mkdir -p /opt/appserver/deployments/drl01-${JOB_BASE_NAME}01-s01\""
                                                sh "scp com/bestbuy/bbym/${JOB_BASE_NAME}/$SCM_TAG/repository.zip drl02capap09d:/opt/appserver/deployments/drl01-${JOB_BASE_NAME}01-s01"

                        			deployPLWrapper appName: '${JOB_BASE_NAME}01', source: 'com/bestbuy/bbym/${JOB_BASE_NAME}/$SCM_TAG/$warname', server: 'drl02capap09d', artifact: '$warname', target: '/opt/appserver/deployments/drl01-${JOB_BASE_NAME}01-s01'

                      
					}
				}
              	stepResponse TRANSACTION_ID: "$TRANSACTION_ID", REQUEST_ID: "$REQUEST_ID", STEP1: 'DEPLOY', STEP2: 'NULL', RESPONSE_URL: '$RESPONSE_URL', JOB_URL1: "$BUILD_URL", JOB_URL2: "NULL"
			}
		}
	}
	post{
		success{
          	script{
		        if(params.TRANSACTION_ID != '' ){
					finalResponse TRANSACTION_ID: "$TRANSACTION_ID", REQUEST_ID: "$REQUEST_ID", SCM_TAG: "$SCM_TAG", STATUS: 'SUCCESS', RESPONSE_URL: '$RESPONSE_URL', EntityName: "${ENTITY_NAME}"
                }
            }
		}
	   
		failure{
          	script{
		        if(params.TRANSACTION_ID != '' ){
					finalResponse TRANSACTION_ID: "$TRANSACTION_ID", REQUEST_ID: "$REQUEST_ID", SCM_TAG: "$SCM_TAG", STATUS: 'FAILURE', RESPONSE_URL: '$RESPONSE_URL', EntityName: "${ENTITY_NAME}"
                }
            }
		}
	}
}












def call(Map config)
{

  withCredentials([string(credentialsId: 'cap.db.pass', variable: 'cappass'), string(credentialsId: 'cas.db.pass', variable: 'caspass'), string(credentialsId: 'javax.net.ssl.trustStorePassword', variable: 'trust')])
  {
		sh "mvn ${config?.pom ?: ''} clean install -Dcap.db.pass=$cappass -Dcas.db.pass=$caspass -Djavax.net.ssl.trustStorePassword=$trust ${config?.args ?: ''}"
  }
//test comment

}


def call(Map config)
{
 def server = Artifactory.server 'EnterpriseArtifactory'
 
 def uploadSpec = """{
  "files": [
    {
      "pattern": "${config?.pattern ?: ''}",
      "target": "${config?.target ?: ''}"
    }
 ]
}"""
server.upload(uploadSpec)
}
