.PHONY: dev deploy-k8s down port-forward

dev:
	docker-compose up --build

down:
	docker-compose down -v

deploy-k8s:
	kubectl apply -f k8s/namespace.yaml
	kubectl create secret generic postgres-secret \
		--from-env-file=.env \
		--namespace=avivly \
		--dry-run=client -o yaml | kubectl apply -f -
	kubectl apply -f k8s/

port-forward:
	kubectl -n avivly port-forward svc/nginx 8080:80 --address 0.0.0.0
