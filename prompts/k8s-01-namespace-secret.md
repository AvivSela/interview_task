# Agent Prompt: K8s Namespace & Secret (K8S-01)

## Project Context
You are adding Kubernetes support to an analytics-driven URL shortener (Spring Boot + React + PostgreSQL).
Working directory: project root (where `k8s/` will live).
Docker Compose is already working. You are NOT modifying any existing files — only creating new ones under `k8s/`.

## Your Task
Create `k8s/namespace.yaml`. The secret is generated from `.env` at deploy time — do NOT create a static `k8s/secret.yaml`.

## Files to Create

### `k8s/namespace.yaml`
```yaml
apiVersion: v1
kind: Namespace
metadata:
  name: avivly
```

## How the secret is managed
Credentials live in `.env` (gitignored). The K8s secret is created via `make deploy-k8s`, which runs:
```bash
kubectl create secret generic postgres-secret \
  --from-env-file=.env \
  --namespace=avivly \
  --dry-run=client -o yaml | kubectl apply -f -
```

`k8s/secret.yaml.example` documents the expected keys but holds no values.

## Acceptance Criteria
- `k8s/namespace.yaml` exists
- `kubectl apply -f k8s/namespace.yaml` exits 0
- `make deploy-k8s` creates the secret from `.env`
- `kubectl -n avivly get secret postgres-secret` shows the secret
