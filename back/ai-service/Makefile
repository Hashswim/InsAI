PYTHON ?= .venv/bin/python

.PHONY: install run lint format typecheck test check build smoke evaluate cross-service

install:
	$(PYTHON) -m pip install -e '.[test,dev]'

run:
	$(PYTHON) -m uvicorn app.main:app --host 127.0.0.1 --port 8085 --no-access-log

lint:
	$(PYTHON) -m ruff check .

format:
	$(PYTHON) -m ruff format .

typecheck:
	$(PYTHON) -m mypy

test:
	$(PYTHON) -m pytest -q

check: lint typecheck test
	$(PYTHON) -m ruff format --check .

build:
	$(PYTHON) -m build

smoke:
	$(PYTHON) scripts/smoke.py

evaluate:
	$(PYTHON) -m scripts.evaluate

cross-service:
	$(PYTHON) -m scripts.cross_service
