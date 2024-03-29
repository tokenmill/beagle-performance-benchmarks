run-dev-env:
	docker-compose -f dockerfiles/docker-compose.yml up

run-beagle-bench:
	mkdir vals || true
	docker-compose -f dockerfiles/docker-compose.beagle.yml build
	docker-compose -f dockerfiles/docker-compose.beagle.yml up \
	--abort-on-container-exit --exit-code-from bench
	clojure -m bench.view vals/vals.json

run-es-bench:
	mkdir vals || true
	docker-compose -f dockerfiles/docker-compose.es.yml build
	docker-compose -f dockerfiles/docker-compose.es.yml up \
	--abort-on-container-exit --exit-code-from bench
	clojure -m bench.view vals/vals.json

run-fake-percolator-bench:
	mkdir vals || true
	docker-compose -f dockerfiles/docker-compose.fake-percolator.yml build
	docker-compose -f dockerfiles/docker-compose.fake-percolator.yml up \
	--abort-on-container-exit --exit-code-from bench
	clojure -m bench.view vals/vals.json
