// jenkins/shared-library/vars/ensureKubernetesAccess.groovy
//
// Single implementation shared by Jenkinsfile.app-cicd and Jenkinsfile.platform-infra.
//
// Usage in Jenkinsfile (after @Library('catalogix-shared-library') _):
//   ensureKubernetesAccess(env.AWS_REGION, env.CLUSTER_NAME, env.KUBECONFIG)
//
// The kubeconfig is (re)written for the requested cluster on EVERY run and then
// verified to actually point at that cluster. The previous version only checked
// that `kubectl get nodes` worked against whatever the existing kubeconfig
// pointed at — so a run for one environment could silently deploy into the
// other environment's cluster (kubectl "worked", nothing was regenerated).
// Pair this with a kubeconfig path that is unique per cluster
// (see KUBECONFIG in the Jenkinsfiles) so concurrent runs for different
// environments can't overwrite each other's current-context either.

def call(String awsRegion, String clusterName, String kubeconfigPath) {
    sh """
    set -e

    mkdir -p \$(dirname ${kubeconfigPath})

    echo "=== AWS Identity ==="
    aws sts get-caller-identity

    echo "=== Writing kubeconfig for cluster '${clusterName}' ==="
    # Retry up to 5 times with 10s backoff — transient EKS API hiccups are
    # common immediately after cluster operations.
    SUCCESS=0
    for i in 1 2 3 4 5; do
        if aws eks update-kubeconfig \\
            --region ${awsRegion} \\
            --name ${clusterName} \\
            --kubeconfig ${kubeconfigPath}; then
            SUCCESS=1
            break
        fi
        echo "Attempt \$i failed — retrying in 10s..."
        sleep 10
    done

    if [ "\$SUCCESS" -ne 1 ]; then
        echo "ERROR: Failed to generate kubeconfig for '${clusterName}' after 5 attempts"
        exit 1
    fi
    chmod 600 ${kubeconfigPath}

    export KUBECONFIG=${kubeconfigPath}

    echo "=== Verifying the active context targets '${clusterName}' ==="
    EXPECTED_ENDPOINT=\$(aws eks describe-cluster --region ${awsRegion} --name ${clusterName} \\
        --query cluster.endpoint --output text)
    ACTUAL_ENDPOINT=\$(kubectl config view --minify -o jsonpath='{.clusters[0].cluster.server}')
    if [ -z "\$EXPECTED_ENDPOINT" ] || [ "\$EXPECTED_ENDPOINT" != "\$ACTUAL_ENDPOINT" ]; then
        echo "ERROR: kubectl context points at '\$ACTUAL_ENDPOINT' but cluster '${clusterName}' is '\$EXPECTED_ENDPOINT'. Refusing to continue."
        exit 1
    fi

    echo "=== Checking API reachability ==="
    REACHABLE=0
    for i in 1 2 3 4 5; do
        if kubectl get nodes --request-timeout=10s > /dev/null 2>&1; then
            REACHABLE=1
            break
        fi
        echo "kubectl attempt \$i failed — retrying in 10s..."
        sleep 10
    done
    if [ "\$REACHABLE" -ne 1 ]; then
        echo "ERROR: cluster '${clusterName}' API is not reachable from this agent."
        kubectl get nodes --request-timeout=10s
        exit 1
    fi

    echo "Kubernetes access verified for '${clusterName}'."
    """
}
