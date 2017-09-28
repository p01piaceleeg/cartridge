// Folders
def workspaceFolderName = "${WORKSPACE_NAME}"
def projectFolderName = "${PROJECT_NAME}"

// Jobs
def buildMavenJob = freeStyleJob(projectFolderName + "/cartridge_package_build_maven")
def buildSonarJob = freeStyleJob(projectFolderName + "/cartridge_code_analysis")
def buildNexusSnapsJob = freeStyleJob(projectFolderName + "/cartridge_snapshots")
def buildAnsibleJob = freeStyleJob(projectFolderName + "/cartridge_recreate_tomcat")
def deployAppJob = freeStyleJob(projectFolderName + "/cartridge_deployApp_tomcat")
def buildSeleniumJob = freeStyleJob(projectFolderName + "/cartridge_test_func")
def buildNexusReleasesJob = freeStyleJob(projectFolderName + "/cartridge_releases")


// Create Environment
buildMavenJob.with{

properties{
    
    copyArtifactPermissionProperty {
      projectNames(' cartridge_deployApp_tomcat ')
    } 
    
  }
  scm {
         git {
            remote {
                credentials('teamoneID')
                url('git@gitlab:adop_teamone/projectSimulation')
            }
            branch('*/master')
        }
    }
  
  wrappers {
        preBuildCleanup()
    }
  
  triggers {
        gitlabPush {
            buildOnMergeRequestEvents(true)
            buildOnPushEvents(true)
            enableCiSkip(true)
            setBuildDescription(false)
            rebuildOpenMergeRequest('never')
        }
      }

  steps {
    
      maven {
        mavenInstallation('ADOP Maven')
        goals('package')
        }
  }
  
      //goals('package')
 
  publishers {
        archiveArtifacts('**/*.war')
  }
    
  publishers {
        downstream('cartridge_code_analysis', 'SUCCESS')
    }

}

// SonarQube


buildSonarJob.with{

     scm {
        git {
            remote {
                credentials('teamoneID')
                url('git@gitlab:adop_teamone/projectSimulation')
            }           
            branch('*/master')
    }
  
configure { project ->
    project / 'builders' / 'hudson.plugins.sonar.SonarRunnerBuilder' {
            properties('''sonar.projectKey=devopsTeamone
                          sonar.projectName=simulationHangman
                          sonar.projectVersion=1
                          sonar.sources=./src/main/webapp''')
            javaOpts()
            jdk('(Inherit From Job)')
            task()
    }
  }     
   publishers {
        downstream('cartridge_snapshots', 'SUCCESS')    
   }
}
}

// Nexus Snapshots

buildNexusSnapsJob.with {
  
  steps { 
      copyArtifacts('cartridge_package_build_maven') {
            includePatterns('target/*.war')
            buildSelector {
                latestSuccessful(true)
            } 
        fingerprintArtifacts(true)
        }

      nexusArtifactUploader {
            nexusVersion('nexus2') 
            protocol('HTTP')
            nexusUrl('10.10.1.230/nexus')
            groupId('teamOne')
            version('1.0-SNAPSHOT')
            repository('snapshots')
            credentialsId('teamoneID')
            artifact {
                artifactId('hangman')
                type('war')
                file('target/hangman.war')
            }
        }
  }
  
     publishers {
        downstream('cartridge_recreate_tomcat', 'SUCCESS')    
   }  
}

// Ansible_Recreate Tomcat

 buildAnsibleJob.with {
  
   label('ansible')
  
     scm {
        git {
            remote {
                credentials('adopID')
                url('http://34.232.107.102/gitlab/adop_teamone/Ansible.git')
            }           
            branch('*/master')
          }
        }
    
   wrappers {
        sshAgent('ec2-keypair')
    }
   
   steps {
        shell('ansible-playbook master.yml -i hosts -e "hosts_env=team1_host" -u ec2-user')
    }
   publishers {
        downstream('cartridge_deployApp_tomcat', 'SUCCESS')    
   }   
 }

 // Tomcat_Deployment of Project to Tomcat
  
  deployAppJob.with {
  
   label('docker')
    
   steps { 
      copyArtifacts('cartridge_package_build_maven') {
            includePatterns('target/*.war')
            buildSelector {
                latestSuccessful(true)
            } 
        fingerprintArtifacts(true)
        }
     
        shell('docker cp target/hangman.war tomcat:/usr/local/tomcat/webapps/')
     }  
    publishers {
        downstream('cartridge_test_func', 'SUCCESS')    
   }     
  }

//Selenium_Test Functionality

buildSeleniumJob.with {
  
  scm {
        git {
            remote {
                url('https://github.com/plamencop/Selenium-Test.git')
            }
            branch('*/master')
          }
        }
    
  steps {
      maven {
        mavenInstallation('ADOP Maven')
        goals('test')
        }
  }
  
  publishers {
        downstream('cartridge_releases', 'SUCCESS')    
   }     
}

//Nexus_Releases

buildNexusReleasesJob.with {
  
  steps { 
      copyArtifacts('cartridge_package_build_maven') {
            includePatterns('target/*.war')
            buildSelector {
                latestSuccessful(true)
            } 
        fingerprintArtifacts(true)
        }

      nexusArtifactUploader {
            nexusVersion('nexus2') 
            protocol('HTTP')
            nexusUrl('10.10.1.230/nexus')
            groupId('teamOne')
            version('${BUILD_NUMBER}')
            repository('releases')
            credentialsId('teamoneID')
            artifact {
                artifactId('hangman')
                type('war')
                file('target/hangman.war')
            }
        }
  }
}   