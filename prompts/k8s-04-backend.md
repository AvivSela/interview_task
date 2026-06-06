# Agent Prompt: K8s Backend Deployment (K8S-04)

## Project Context
You are adding Kubernetes support to an analytics-driven URL shortener (Spring Boot on port 8080, PostgreSQL).
Working directory: project root. K8S-01 through K8S-03 are done.

## Your Task
Create `k8s/backend.yaml` — a Deployment with an initContainer that waits for postgres, and a ClusterIP Service.

## Before Starting
Read `backend/Dockerfile` to confirm the image builds correctly and exposes port 8080.

## Files to Create

### `k8s/backend.yaml`
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: backend
  namespace: avivly
spec:
  replicas: 1
  selector:
    matchLabels:
      app: backend
  template:
    metadata:
      labels:
        app: backend
    spec:
      initContainers:
      - name: wait-for-postgres
        image: busybox:1.36
        command:
        - sh
        - -c
        - until nc -z postgres 5432; do echo "waiting for postgres..."; sleep 2; done
      containers:
      - name: backend
        image: avivly/backend:latest
        imagePullPolicy: IfNotPresent
        ports:
        - containerPort: 8080
        env:
        - name: POSTGRES_DB
          valueFrom:
            secretKeyRef:
              name: postgres-secret
              key: POSTGRES_DB
        - name: SPRING_DATASOURCE_URL
          value: "jdbc:postgresql://postgres:5432/$(POSTGRES_DB)"
        - name: SPRING_DATASOURCE_USERNAME
          valueFrom:
            secretKeyRef:
              name: postgres-secret
              key: POSTGRES_USER
        - name: SPRING_DATASOURCE_PASSWORD
          valueFrom:
            secretKeyRef:
              name: postgres-secret
              key: POSTGRES_PASSWORD
        - name: SPRING_JPA_HIBERNATE_DDL_AUTO
          value: validate
        readinessProbe:
          httpGet:
            path: /api/links
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 10
          failureThreshold: 10
        livenessProbe:
          httpGet:
            path: /api/links
            port: 8080
          initialDelaySeconds: 60
          periodSeconds: 30
---
apiVersion: v1
kind: Service
metadata:
  name: backend
  namespace: avivly
spec:
  selector:
    app: backend
  ports:
  - port: 8080
    targetPort: 8080
```

## Notes
- `$(POSTGRES_DB)` in the `SPRING_DATASOURCE_URL` value is K8s env-variable substitution — it resolves from the `POSTGRES_DB` env entry defined immediately above it in the same list.
- `imagePullPolicy: IfNotPresent` is correct for local minikube builds. Change to `Always` when pushing to a remote registry.
- To replace `avivly/backend:latest` with your real registry image: edit the `image:` field to `your-registry/avivly-backend:latest`.
- **GeoIP is disabled** by default (no `GEO_DB_PATH` env var). To enable it, add a PVC containing the `GeoLite2-City.mmdb` file and mount it, then add `- name: GEO_DB_PATH` / `value: /data/GeoLite2-City.mmdb` to the env block.

## Build the image (minikube)
```bash
eval $(minikube docker-env)
docker build -t avivly/backend:latest ./backend
```

## Build the image (remote registry)
```bash
docker build -t your-registry/avivly-backend:latest ./backend
docker push your-registry/avivly-backend:latest
# then update image: field in k8s/backend.yaml
```

## Acceptance Criteria
- `k8s/backend.yaml` exists
- Image is built and available to the cluster
- `kubectl apply -f k8s/backend.yaml` exits 0
- `kubectl -n avivly get deployment backend` shows `1/1` READY
- `kubectl -n avivly logs deployment/backend` shows Spring Boot started successfully (no DB connection errors)
- `kubectl -n avivly get svc backend` shows a ClusterIP service on port 8080
