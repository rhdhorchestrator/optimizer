# Optimizer

## Overview

Optimizer is an application that helps us to apply RedHat Insights performance recommendations for OCP (OpenShift cluster platform). It makes use of OSL (OpenShift Serverless Logic, a.k.a. SonataFlow). We deploy a workflow that is responsible for the actual optimization of the OCP workloads. The application is meant to be used with OCP clusters. That means that it should be deployed within a cluster or configured with cluster access details.  
The flow is quite simple:
- Read configuration from ConfigMap 
- fetch recommendations from RedHat Insights 
- look for performance recommendations 
- filter based on cluster names, terms etc 
- Call the OSL workflow for patching the OCP workload
Optimizer is written in Java and uses Maven as a build infrastructure. Logging is done with log4j2

## Configuration and setup

The application uses a Kubernetes client for reading its configuration. Either deploy it in a cluster or make sure to configure the client (~/.kube/config file, KUBECONFIG env var etc).  
The namespace and name of the configMap containing application configuration is configured via env vars: OPTIMIZER_CM_NAMESPACE, OPTIMIZER_CM_NAME. The class Config is a reflection of the ConfigMap. There's a sample ConfigMap YAML file: optimizer-configmap.yaml.  
Logging is configured in log4j2.xml file or the ConfigMap that contains it.

## Building the application

We use Maven. 'mvn package' will create a JAR file with all dependencies for you to execute.  
The file optimizer.dockerfile can be used to build a container image.

## Starting the application

### Create configuration

Create a ConfigMap containing the configuration. Use optimizer-configmap.yaml as an example. Create a service account with permissions to list ConfigMaps.

### Outside a cluster

- Prepare the kube client configuration
- See 'optmizer.dockerfile'. It shows which env vars to configure and the execution command

### Inside a cluster
- Create a deployment with optimizer-deployment.yaml.
- Logging configuration. Follow optimizer-deployment.yaml logging section for creating a ConfigMap with logging configuration
