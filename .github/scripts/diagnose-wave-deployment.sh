#!/usr/bin/env bash
# Read-only diagnostics. Do not print environment variables or Secret contents.
set -u
namespace="${1:-jambarpay}"

kubectl -n "$namespace" get deployment wave-service -o wide
kubectl -n "$namespace" get deployment wave-service -o jsonpath='{range .status.conditions[*]}{.type}={.status}: {.reason} {.message}{"\n"}{end}'
kubectl -n "$namespace" get svc wave-service -o wide
kubectl -n "$namespace" get replicasets -l app=wave-service -o wide
kubectl -n "$namespace" get pods -l app=wave-service -o wide

for pod in $(kubectl -n "$namespace" get pods -l app=wave-service -o name); do
  printf '\nPod %s\n' "$pod"
  kubectl -n "$namespace" get "$pod" -o jsonpath='{range .status.containerStatuses[*]}container={.name} ready={.ready} restarts={.restartCount} waiting={.state.waiting.reason} terminated={.state.terminated.reason} exit={.state.terminated.exitCode} previous={.lastState.terminated.reason} previousExit={.lastState.terminated.exitCode}{"\n"}{end}'
  printf 'Current logs\n'
  kubectl -n "$namespace" logs "$pod" --prefix --tail=150 || true
  printf 'Previous container logs (may be absent)\n'
  kubectl -n "$namespace" logs "$pod" --previous --prefix --tail=100 || true
  kubectl -n "$namespace" get events --field-selector "involvedObject.name=${pod#pod/}" --sort-by=.metadata.creationTimestamp
done

kubectl -n "$namespace" get events --field-selector type=Warning --sort-by=.metadata.creationTimestamp | tail -n 30
