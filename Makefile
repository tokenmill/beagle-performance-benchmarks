run-dev-env:
	docker-compose -f dockerfiles/docker-compose.yml up

run-bench:
	docker-compose -f dockerfiles/docker-compose.es.yml build
	docker-compose -f dockerfiles/docker-compose.es.yml up \
	--abort-on-container-exit --exit-code-from bench
