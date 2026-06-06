# Agent Prompt: K8s Nginx Entry Point (K8S-06)

## Project Context
You are adding Kubernetes support to an analytics-driven URL shortener.
Working directory: project root. K8S-01 through K8S-05 are done. This is the final manifest — the public entry point.

## Your Task
Create `k8s/nginx.yaml` — a Deployment that mounts the `nginx-config` ConfigMap (from K8S-02), with initContainers that wait for both `backend` and `frontend` to be ready, and a LoadBalancer Service on port 80.

## Files to Create

### `k8s/nginx.yaml`
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: nginx
  namespace: avivly
spec:
  replicas: 1
  selector:
    matchLabels:
      app: nginx
  template:
    metadata:
      labels:
        app: nginx
    spec:
      initContainers:
      - name: wait-for-backend
        image: busybox:1.36
        command:
        - sh
        - -c
        - until nc -z backend 8080; do echo "waiting for backend..."; sleep 2; done
      - name: wait-for-frontend
        image: busybox:1.36
        command:
        - sh
        - -c
        - until nc -z frontend 80; do echo "waiting for frontend..."; sleep 2; done
      containers:
      - name: nginx
        image: nginx:1.27-alpine
        ports:
        - containerPort: 80
        volumeMounts:
        - name: nginx-config
          mountPath: /etc/nginx/nginx.conf
          subPath: nginx.conf
      volumes:
      - name: nginx-config
        configMap:
          name: nginx-config
---
apiVersion: v1
kind: Service
metadata:
  name: nginx
  namespace: avivly
spec:
  type: LoadBalancer
  selector:
    app: nginx
  ports:
  - port: 80
    targetPort: 80
```

## Notes
- `subPath: nginx.conf` mounts only the single key from the ConfigMap at `/etc/nginx/nginx.conf`, replacing the file without touching other files in `/etc/nginx/`.
- **minikube users**: `LoadBalancer` services get an `<pending>` external IP until you run `minikube tunnel` in a separate terminal. Alternatively change `type: LoadBalancer` to `type: NodePort` and access via `minikube service nginx -n avivly`.
- **Cloud (EKS/GKE/AKS)**: `LoadBalancer` automatically provisions a cloud load balancer and assigns an external IP. Use `kubectl -n avivly get svc nginx` to watch for it.

## After Applying — Full Smoke Test
```bash
# Get the external IP
kubectl -n avivly get svc nginx

# Test the app
curl http://<EXTERNAL-IP>/api/links          # should return JSON array
curl -I http://<EXTERNAL-IP>/                # should return 200 from frontend
curl -I http://<EXTERNAL-IP>/someShortCode   # should return 302 or redirect to /link-expired
```

## Acceptance Criteria
- `k8s/nginx.yaml` exists
- `kubectl apply -f k8s/nginx.yaml` exits 0
- `kubectl -n avivly get deployment nginx` shows `1/1` READY
- `kubectl -n avivly get svc nginx` shows an external IP (or NodePort)
- `curl http://<IP>/api/links` returns a valid JSON response
- `curl http://<IP>/` serves the React app HTML
