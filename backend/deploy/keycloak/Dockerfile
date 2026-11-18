# Keycloak for the deployed environments: one server, one realm per environment.
# `kc.sh build` bakes the build-time options in, so the container starts with `start --optimized`.
FROM quay.io/keycloak/keycloak:26.7.5

ENV KC_DB=postgres
ENV KC_HEALTH_ENABLED=true

# Imported on start only if the realm does not exist yet: later changes go through the admin console
COPY realms/ /opt/keycloak/data/import/

RUN /opt/keycloak/bin/kc.sh build
