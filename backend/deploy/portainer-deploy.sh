#!/usr/bin/env bash
# Redeploys one Portainer stack with a new image tag, then waits until the new version answers.
#
#   portainer-deploy.sh backend  <stack-id> <compose-file> <image-tag> <api-base-url>  <expected-commit>
#   portainer-deploy.sh keycloak <stack-id> <compose-file> <image-tag> <auth-base-url> <realm>
#
# Needs PORTAINER_URL, PORTAINER_TOKEN (an access token) and PORTAINER_ENDPOINT_ID in the environment,
# plus curl and jq. DEPLOY_TIMEOUT_SECONDS (default 300) bounds the wait for the new version.
#
# Portainer's stack update replaces *all* stack variables with the ones sent (stack.Env = payload.Env),
# so the script reads them first and sends them back with only IMAGE_TAG changed. The variables hold
# secrets: nothing here prints them or a Portainer response body that contains them. Do not add set -x.
set -euo pipefail

usage() {
  sed -n '2,6p' "$0" >&2
  exit 2
}

[[ $# -eq 6 ]] || usage
mode=$1 stack_id=$2 compose_file=$3 image_tag=$4 base_url=${5%/} check_arg=$6
[[ $mode == backend || $mode == keycloak ]] || usage
[[ -f $compose_file ]] || { echo "::error::Compose file $compose_file not found" >&2; exit 2; }
: "${PORTAINER_URL:?PORTAINER_URL must be set}"
: "${PORTAINER_TOKEN:?PORTAINER_TOKEN must be set}"
: "${PORTAINER_ENDPOINT_ID:?PORTAINER_ENDPOINT_ID must be set}"

portainer=${PORTAINER_URL%/}
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

# Prints only Portainer's error message, never a stack (which would carry the variables)
fail_with() {
  local what=$1 status=$2 body=$3
  local reason
  reason=$(jq -r '[.message, .details] | map(select(. != null and . != "")) | join(": ")' "$body" 2>/dev/null || true)
  echo "::error::$what failed with HTTP $status${reason:+: $reason}" >&2
  exit 1
}

echo "Reading stack $stack_id"
status=$(curl --silent --show-error --max-time 30 \
  --output "$work/stack.json" --write-out '%{http_code}' \
  --header "X-API-Key: $PORTAINER_TOKEN" \
  "$portainer/api/stacks/$stack_id")
[[ $status == 200 ]] || fail_with "Reading stack $stack_id" "$status" "$work/stack.json"

# Keep every variable, replace IMAGE_TAG (or add it), send the compose file of this commit
jq --arg tag "$image_tag" --rawfile compose "$compose_file" '{
  StackFileContent: $compose,
  Env: (((.Env // []) | map(select(.name != "IMAGE_TAG"))) + [{name: "IMAGE_TAG", value: $tag}]),
  Prune: true,
  RepullImageAndRedeploy: true
}' "$work/stack.json" > "$work/payload.json"

echo "Deploying image tag $image_tag to stack $stack_id"
status=$(curl --silent --show-error --max-time 600 \
  --request PUT \
  --output "$work/response.json" --write-out '%{http_code}' \
  --header "X-API-Key: $PORTAINER_TOKEN" \
  --header 'Content-Type: application/json' \
  --data-binary "@$work/payload.json" \
  "$portainer/api/stacks/$stack_id?endpointId=$PORTAINER_ENDPOINT_ID")
[[ $status == 200 ]] || fail_with "Updating stack $stack_id" "$status" "$work/response.json"

timeout=${DEPLOY_TIMEOUT_SECONDS:-300}
deadline=$((SECONDS + timeout))

# Since Portainer 2.45 the update only starts the deployment and answers at once; the outcome lands
# on the stack later (Status 3 deploying, 1 active, 4 error, with a message in DeploymentStatus).
echo "Waiting for Portainer to finish the deployment"
while true; do
  status=$(curl --silent --show-error --max-time 30 \
    --output "$work/stack.json" --write-out '%{http_code}' \
    --header "X-API-Key: $PORTAINER_TOKEN" \
    "$portainer/api/stacks/$stack_id")
  [[ $status == 200 ]] || fail_with "Reading stack $stack_id" "$status" "$work/stack.json"
  stack_status=$(jq -r '.Status' "$work/stack.json")
  case $stack_status in
    1) echo "Portainer deployed the stack"; break ;;
    4)
      message=$(jq -r '[.DeploymentStatus[]? | select(.Message != null and .Message != "") | .Message] | last // "no message"' "$work/stack.json")
      echo "::error::Portainer failed to deploy stack $stack_id: $message" >&2
      exit 1
      ;;
  esac
  if ((SECONDS >= deadline)); then
    echo "::error::Portainer was still deploying stack $stack_id after $timeout seconds (status $stack_status)" >&2
    exit 1
  fi
  sleep 5
done

# The new container needs a while to start; Flyway migrates the database on the way
deadline=$((SECONDS + timeout))
case $mode in
  backend)
    echo "Waiting for $base_url to report commit $check_arg and status UP"
    while ((SECONDS < deadline)); do
      commit=$(curl --silent --max-time 10 "$base_url/actuator/info" | jq -r '.build.commit // empty' 2>/dev/null || true)
      health=$(curl --silent --max-time 10 "$base_url/actuator/health" | jq -r '.status // empty' 2>/dev/null || true)
      if [[ $commit == "$check_arg" && $health == UP ]]; then
        echo "Deployed: commit $commit is UP"
        exit 0
      fi
      echo "  commit=${commit:-?} health=${health:-?}"
      sleep 5
    done
    ;;
  keycloak)
    url="$base_url/realms/$check_arg/.well-known/openid-configuration"
    echo "Waiting for $url"
    while ((SECONDS < deadline)); do
      if curl --silent --fail --max-time 10 --output /dev/null "$url"; then
        echo "Deployed: realm $check_arg answers"
        exit 0
      fi
      sleep 5
    done
    ;;
esac

echo "::error::The new version did not become healthy within $timeout seconds. The stack is left as it is (no rollback)." >&2
exit 1
