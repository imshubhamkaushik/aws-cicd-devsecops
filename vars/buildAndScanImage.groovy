// jenkins/shared-library/vars/buildAndScanImage.groovy
//
// Builds a Docker image and fails the build on HIGH/CRITICAL CVEs, honoring
// .trivyignore (entries there carry expiry dates). One function is used for all 9 application images (7 backend +
// frontend + gateway) instead of a duplicated block per service — this
// is the piece that keeps the Jenkinsfile's line count from scaling with
// service count.
//
// Usage:
//   buildAndScanImage(
//       imageRef: "${ECR_REGISTRY}/user-svc:${IMAGE_TAG}",
//       dockerfile: 'backend/user-svc/Dockerfile',
//       context: '.',
//       target: 'app',              // optional — omit for single-stage builds (frontend)
//       trivyImage: env.TRIVY_IMAGE
//   )

def call(Map args) {
    String imageRef   = args.imageRef
    String dockerfile = args.dockerfile
    String context     = args.context ?: '.'
    String target       = args.target ?: ''
    String trivyImage = args.trivyImage

    if (!imageRef || !dockerfile || !trivyImage) {
        error "buildAndScanImage: imageRef, dockerfile, and trivyImage are required"
    }

    String targetFlag = target ? "--target ${target}" : ''

    sh """
        docker build ${targetFlag} -f ${dockerfile} -t ${imageRef} ${context}
    """

    sh """
        docker run --rm \
          -v /var/run/docker.sock:/var/run/docker.sock \
          -v \$(pwd)/.trivyignore:/.trivyignore \
          ${trivyImage} image \
          --severity HIGH,CRITICAL \
          --exit-code 1 \
          --ignore-unfixed \
          --ignorefile /.trivyignore \
          ${imageRef}
    """
}
