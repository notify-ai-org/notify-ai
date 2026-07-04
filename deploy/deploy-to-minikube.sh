#!/bin/bash
echo "Waiting for Minikube cluster Control Plane to be ready..."
while ! minikube status > /dev/null 2>&1; do sleep 5; done

echo "Building the Docker image..."
docker build -t gcr.io/newagent-rnun/vocab-agent:latest .

echo "Minikube is ready. Injecting the built Docker image into the Minikube VM..."
minikube image load gcr.io/newagent-rnun/vocab-agent:latest

echo "Applying ConfigMap..."
kubectl apply -f k8s/configmap.yaml

echo "Applying Deployment and Service manifests..."
kubectl apply -f k8s/deployment.yaml

echo "Minikube deployment initiated! Pods will start crashing until the secrets are applied."
