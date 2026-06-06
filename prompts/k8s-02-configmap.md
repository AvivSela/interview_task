# Agent Prompt: K8s Nginx ConfigMap (K8S-02)

## Project Context
You are adding Kubernetes support to an analytics-driven URL shortener.
Working directory: project root. `k8s/namespace.yaml` and `k8s/secret.yaml.example` already exist (K8S-01 is done).

## Your Task
Create `k8s/configmap.yaml` that bakes the contents of `nginx/nginx.conf` into a ConfigMap.
This ConfigMap is later volume-mounted into the nginx pod (done in K8S-06).

## Files to Create

### `k8s/configmap.yaml`
```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: nginx-config
  namespace: avivly
data:
  nginx.conf: |
    events {}

    http {
        server_tokens off;

        limit_req_zone $binary_remote_addr zone=redirects:10m rate=30r/m;

        server {
            listen 80;

            proxy_set_header Host              $host;
            proxy_set_header X-Real-IP         $remote_addr;
            proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
            proxy_set_header X-Forwarded-Proto $scheme;

            location /api/ {
                proxy_pass http://backend:8080;
            }

            location ^~ /assets/ {
                proxy_pass http://frontend:80;
            }

            location ^~ /link-expired {
                proxy_pass http://frontend:80;
            }

            location ~ ^/[A-Za-z0-9_-]+ {
                limit_req zone=redirects burst=5 nodelay;
                proxy_pass http://backend:8080;
            }

            location / {
                proxy_pass http://frontend:80;
            }
        }
    }
```

## Notes
- `backend` and `frontend` resolve to K8s Services of the same name within the `avivly` namespace — no changes to the nginx config are needed.
- Do NOT modify `nginx/nginx.conf`. The ConfigMap is a copy for K8s use; the original file remains for Docker Compose.

## Acceptance Criteria
- `k8s/configmap.yaml` exists
- `kubectl apply -f k8s/configmap.yaml` exits 0
- `kubectl -n avivly get configmap nginx-config` shows the configmap
