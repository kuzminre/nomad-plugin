release: maven-release-plugin_prepare maven-release-plugin_perform

all: release hpi

maven-release-plugin_prepare:
	@mvn org.apache.maven.plugins:maven-release-plugin:prepare

maven-release-plugin_perform:
	@mvn org.apache.maven.plugins:maven-release-plugin:perform

hpi:
	@mvn org.jenkins-ci.tools:maven-hpi-plugin:hpi

.PHONY: all release

.EXPORT_ALL_VARIABLES:
JAVA_HOME = /Library/Java/JavaVirtualMachines/adoptopenjdk-8.jdk/Contents/Home/
