# Agent Prompt: K8s Postgres StatefulSet (K8S-03)

## Project Context
You are adding Kubernetes support to an analytics-driven URL shortener.
Working directory: project root. K8S-01 and K8S-02 are done (`namespace`, `secret`, `configmap` exist).

## Your Task
Create `k8s/postgres.yaml` — a StatefulSet (for stable persistent storage), a PersistentVolumeClaim via `volumeClaimTemplates`, and a ClusterIP Service.

## Files to Create

### `k8s/postgres.yaml`
```yaml
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: postgres
  namespace: avivly
spec:
  serviceName: postgres
  replicas: 1
  selector:
    matchLabels:
      app: postgres
  template:
    metadata:
      labels:
        app: postgres
    spec:
      containers:
      - name: postgres
        image: postgres:15-alpine
        ports:
        - containerPort: 5432
        env:
        - name: POSTGRES_DB
          valueFrom:
            secretKeyRef:
              name: postgres-secret
              key: POSTGRES_DB
        - name: POSTGRES_USER
          valueFrom:
            secretKeyRef:
              name: postgres-secret
              key: POSTGRES_USER
        - name: POSTGRES_PASSWORD
          valueFrom:
            secretKeyRef:
              name: postgres-secret
              key: POSTGRES_PASSWORD
        volumeMounts:
        - name: postgres-data
          mountPath: /var/lib/postgresql/data
        readinessProbe:
          exec:
            command:
            - sh
            - -c
            - pg_isready -U $POSTGRES_USER -d $POSTGRES_DB
          initialDelaySeconds: 5
          periodSeconds: 5
          failureThreshold: 10
  volumeClaimTemplates:
  - metadata:
      name: postgres-data
    spec:
      accessModes: ["ReadWriteOnce"]
      resources:
        requests:
          storage: 5Gi
---
apiVersion: v1
kind: Service
metadata:
  name: postgres
  namespace: avivly
spec:
  selector:
    app: postgres
  ports:
  - port: 5432
    targetPort: 5432
```

## Notes
- StatefulSet is used instead of Deployment because it guarantees stable storage binding across pod restarts.
- The readinessProbe uses shell env vars (`$POSTGRES_USER`, `$POSTGRES_DB`) which the container itself expands — this is not K8s env substitution.
- The default StorageClass is used for the PVC. On cloud providers this auto-provisions a disk. On minikube the `standard` StorageClass handles it automatically.

## Acceptance Criteria
- `k8s/postgres.yaml` exists
- `kubectl apply -f k8s/postgres.yaml` exits 0
- `kubectl -n avivly get statefulset postgres` shows `1/1` READY
- `kubectl -n avivly get svc postgres` shows a ClusterIP service on port 5432
- `kubectl -n avivly get pvc` shows a Bound PVC
