# Agent Prompt: K8s Frontend Deployment (K8S-05)

## Project Context
You are adding Kubernetes support to an analytics-driven URL shortener (React + Vite, served by nginx on port 80).
Working directory: project root. K8S-01 through K8S-04 are done.

## Your Task
Create `k8s/frontend.yaml` — a Deployment and a ClusterIP Service.

## Before Starting
Read `frontend/Dockerfile` to confirm the image serves on port 80.

## Files to Create

### `k8s/frontend.yaml`
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: frontend
  namespace: avivly
spec:
  replicas: 1
  selector:
    matchLabels:
      app: frontend
  template:
    metadata:
      labels:
        app: frontend
    spec:
      containers:
      - name: frontend
        image: avivly/frontend:latest
        imagePullPolicy: IfNotPresent
        ports:
        - containerPort: 80
        readinessProbe:
          httpGet:
            path: /
            port: 80
          initialDelaySeconds: 5
          periodSeconds: 5
---
apiVersion: v1
kind: Service
metadata:
  name: frontend
  namespace: avivly
spec:
  selector:
    app: frontend
  ports:
  - port: 80
    targetPort: 80
```

## Notes
- The frontend image already contains `frontend/nginx.conf` baked in (see `frontend/Dockerfile`). No ConfigMap is needed.
- `frontend/nginx.conf` has an `/api/` proxy to `http://backend:8080` — this resolves correctly within the cluster via the `backend` K8s Service.
- `imagePullPolicy: IfNotPresent` is correct for local minikube builds. Change to `Always` for a remote registry.
- To replace `avivly/frontend:latest` with your real registry image: edit the `image:` field.

## Build the image (minikube)
```bash
eval $(minikube docker-env)
docker build -t avivly/frontend:latest ./frontend
```

## Build the image (remote registry)
```bash
docker build -t your-registry/avivly-frontend:latest ./frontend
docker push your-registry/avivly-frontend:latest
# then update image: field in k8s/frontend.yaml
```

## Acceptance Criteria
- `k8s/frontend.yaml` exists
- Image is built and available to the cluster
- `kubectl apply -f k8s/frontend.yaml` exits 0
- `kubectl -n avivly get deployment frontend` shows `1/1` READY
- `kubectl -n avivly get svc frontend` shows a ClusterIP service on port 80
