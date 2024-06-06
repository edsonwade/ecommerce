# Kubernetes Commands Cheat Sheet

## Basics

- `kubectl version`: Display the Kubernetes client and server version information.
- `kubectl cluster-info`: Display cluster info.
- `kubectl get nodes`: List all nodes in the cluster.
- `kubectl get pods`: List all pods in the default namespace.
- `kubectl get pods --all-namespaces`: List all pods in all namespaces.
- `kubectl describe pod pod_name`: Show detailed information about a pod.
- `kubectl logs pod_name`: View logs for a pod.
- `kubectl exec -it pod_name -- /bin/bash`: Start an interactive shell inside a pod.

## Deployments

- `kubectl get deployments`: List all deployments.
- `kubectl describe deployment deployment_name`: Show detailed information about a deployment.
- `kubectl scale deployment deployment_name --replicas=3`: Scale a deployment to the specified number of replicas.
- `kubectl rollout status deployment/deployment_name`: Monitor the status of a deployment rollout.
- `kubectl rollout history deployment/deployment_name`: View rollout history of a deployment.
- `kubectl rollout undo deployment/deployment_name`: Rollback a deployment to the previous version.

## Services

- `kubectl get services`: List all services.
- `kubectl describe service service_name`: Show detailed information about a service.
- `kubectl expose deployment deployment_name --type=LoadBalancer --port=8080`: Expose a deployment as a service.
- `kubectl port-forward pod_name local_port:pod_port`: Forward a local port to a port on a pod.
- `kubectl delete service service_name`: Delete a service.

## Configmaps and Secrets

- `kubectl create configmap configmap_name --from-literal=key1=value1 --from-literal=key2=value2`: Create a configmap from literal values.
- `kubectl create secret generic secret_name --from-literal=key1=value1 --from-literal=key2=value2`: Create a secret from literal values.
- `kubectl get configmaps`: List all configmaps.
- `kubectl get secrets`: List all secrets.
- `kubectl describe configmap configmap_name`: Show detailed information about a configmap.
- `kubectl describe secret secret_name`: Show detailed information about a secret.

## Namespaces

- `kubectl create namespace namespace_name`: Create a new namespace.
- `kubectl get namespaces`: List all namespaces.
- `kubectl describe namespace namespace_name`: Show detailed information about a namespace.
- `kubectl delete namespace namespace_name`: Delete a namespace and all resources in it.

## Persistent Volumes and Persistent Volume Claims

- `kubectl get pv`: List all persistent volumes.
- `kubectl get pvc`: List all persistent volume claims.
- `kubectl describe pv pv_name`: Show detailed information about a persistent volume.
- `kubectl describe pvc pvc_name`: Show detailed information about a persistent volume claim.

## Other Operations

- `kubectl apply -f filename.yaml`: Apply configuration from a YAML file.
- `kubectl delete -f filename.yaml`: Delete resources defined in a YAML file.
- `kubectl edit resource_type resource_name`: Edit a resource using the default editor.
- `kubectl label pods pod_name app=example`: Add a label to a pod.
- `kubectl get pods -l app=example`: List pods with a specific label.
- `kubectl delete pods -l app=example`: Delete pods with a specific label.

## Kubernetes Dashboard

- `kubectl apply -f https://raw.githubusercontent.com/kubernetes/dashboard/v2.3.1/aio/deploy/recommended.yaml`: Install the Kubernetes Dashboard.
- `kubectl proxy`: Start a proxy to the Kubernetes API server.
- Access the Dashboard at http://localhost:8001/api/v1/namespaces/kubernetes-dashboard/services/https:kubernetes-dashboard:/proxy/.
