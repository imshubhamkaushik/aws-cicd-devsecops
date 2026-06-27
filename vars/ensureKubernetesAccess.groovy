// jenkins/shared-library/vars/ensureKubernetesAccess.groovy
//
// Single implementation shared by Jenkinsfile.app-cicd and Jenkinsfile.platform-infra.
// Previously copy-pasted 40-line blocks existed in both Jenkinsfiles with a comment
// "if you change this here, change it there too" — a known maintenance trap.
//
// Usage in Jenkinsfile (after @Library('catalogix-shared-library') _):
//   ensureKubernetesAccess(env.AWS_REGION, env.CLUSTER_NAME, env.KUBECONFIG)

def call(String awsRegion, String clusterName, String kubeconfigPath) {
    sh """
    set -e

    mkdir -p \$(dirname ${kubeconfigPath})

    echo "=== Checking Kubernetes access ==="

    if ! kubectl get nodes --request-timeout=10s > /dev/null 2>&1; then
        echo "kubectl failed — regenerating kubeconfig..."

        echo "=== AWS Identity ==="
        aws sts get-caller-identity || true

        echo "=== Regenerating kubeconfig ==="
        # Retry up to 5 times with 10s backoff — transient EKS API
        # hiccups are common immediately after cluster operations.
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
            echo "ERROR: Failed to generate kubeconfig after 5 attempts"
            exit 1
        fi

        echo "=== Retrying kubectl ==="
        kubectl get nodes --request-timeout=10s
    fi

    chmod 600 ${kubeconfigPath}
    echo "Kubernetes access verified."
    """
}
