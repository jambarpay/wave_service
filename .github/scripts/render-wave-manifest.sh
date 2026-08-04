#!/usr/bin/env bash
set -euo pipefail

: "${IMAGE_REPOSITORY:?IMAGE_REPOSITORY is required}"
: "${IMAGE_TAG:?IMAGE_TAG is required}"

K8S_NAMESPACE="${K8S_NAMESPACE:-jambarpay}"

cat <<YAML
apiVersion: v1
kind: Service
metadata:
  name: wave-service
  namespace: ${K8S_NAMESPACE}
  labels:
    app: wave-service
spec:
  selector:
    app: wave-service
  type: ClusterIP
  ports:
    - name: http
      port: 8088
      targetPort: 8088
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: wave-service
  namespace: ${K8S_NAMESPACE}
  labels:
    app: wave-service
spec:
  replicas: 1
  revisionHistoryLimit: 5
  selector:
    matchLabels:
      app: wave-service
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxUnavailable: 0
      maxSurge: 1
  template:
    metadata:
      labels:
        app: wave-service
    spec:
      imagePullSecrets:
        - name: ghcr-auth
      containers:
        - name: wave-service
          image: ${IMAGE_REPOSITORY}:${IMAGE_TAG}
          imagePullPolicy: Always
          ports:
            - containerPort: 8088
              name: http
          env:
            - name: WAVE_SERVICE_NAME
              value: wave-service
            - name: WAVE_SERVICE_PORT
              value: "8088"
            - name: EUREKA_SERVER_URL
              value: http://eureka-server:8761/eureka/
            - name: EUREKA_REGISTER
              value: "true"
            - name: EUREKA_FETCH
              value: "true"
          envFrom:
            - secretRef:
                name: wave-service-env
          startupProbe:
            tcpSocket:
              port: 8088
            initialDelaySeconds: 20
            periodSeconds: 10
            timeoutSeconds: 3
            failureThreshold: 60
          readinessProbe:
            tcpSocket:
              port: 8088
            initialDelaySeconds: 10
            periodSeconds: 10
            timeoutSeconds: 3
            failureThreshold: 18
          livenessProbe:
            tcpSocket:
              port: 8088
            initialDelaySeconds: 60
            periodSeconds: 20
            timeoutSeconds: 3
            failureThreshold: 6
          resources:
            requests:
              cpu: 100m
              memory: 256Mi
            limits:
              cpu: 500m
              memory: 768Mi
YAML
