# Secure Secrets Handling for Helm (catalogix-hc)

## ❌ Never commit real secrets to values.yaml

Correct workflow:

---

## ✅ Step 1 — Store DB_PASSWORD in Jenkins Credentials
Add a Jenkins Secret Text credential called `DB_PASSWORD`.

---

## ✅ Step 2 — Before Helm deploy, create/update k8s secret:

```bash
kubectl create secret generic catalogix-secrets \
  --from-literal=DB_PASSWORD="${DB_PASSWORD}" \
  -n ${K8S_NAMESPACE} \
  --dry-run=client -o yaml | kubectl apply -f -

```

## ✅ Step 3 — Reference the secret inside Deployment template:
env:
  - name: SPRING_DATASOURCE_PASSWORD
    valueFrom:
      secretKeyRef:
        name: catalogix-secrets
        key: DB_PASSWORD