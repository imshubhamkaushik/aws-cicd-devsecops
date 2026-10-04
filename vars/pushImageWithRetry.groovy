// jenkins/shared-library/vars/pushImageWithRetry.groovy
//
// Pushes a single image reference to ECR, retrying on transient failures
// (throttling, brief auth-token expiry). Kept as its own step so the retry
// policy lives in one place instead of being copy-pasted into every push loop.
//
// Usage: pushImageWithRetry("${ECR_REGISTRY}/catalog-svc:${IMAGE_TAG}")

def call(String imageRef, int retries = 3) {
    retry(retries) {
        sh "docker push ${imageRef}"
    }
}
