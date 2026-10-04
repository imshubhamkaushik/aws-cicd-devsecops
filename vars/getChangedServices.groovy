// jenkins/shared-library/vars/getChangedServices.groovy
//
// Returns the subset of `services` whose source changed since the last commit that
// this job BUILT SUCCESSFULLY. Used to skip the expensive test stage for services
// no one touched — NOT used to skip Docker build/scan/push (see Jenkinsfile
// header comment on why every image still gets built+pushed every run).
//
// Baseline = env.GIT_PREVIOUS_SUCCESSFUL_COMMIT (set by the Jenkins Git plugin), not HEAD~1:
// diffing against the last green build means a multi-commit push, a shared-file change in an
// earlier commit, or a previously failed build is never skipped.
//
// Fails SAFE: with no usable baseline (first build on a branch, baseline missing
// from this clone because of a shallow fetch / force-push / gc, or a malformed
// value) every service is treated as changed.
//
// Safety net: if the parent pom.xml, any Jenkinsfile, shared-library code under vars/,
// or the Helm chart changed anywhere in the range, every service is treated as changed.
// Shared build logic can affect all modules even when their own source did not move.
//
// Usage:
//   def changed = getChangedServices(BACKEND_SERVICES, [prefix: 'backend/'])
//   def changedFrontend = getChangedServices(['frontend'], [prefix: ''])
// Optional: [baseCommit: '<sha>'] overrides the baseline (used by tests).
//
// Tests: groovy vars/test/GetChangedServicesTest.groovy

def call(List<String> services, Map opts = [:]) {
    String prefix = opts.prefix ?: ''
    String base = resolveBaseline(opts.baseCommit ?: env.GIT_PREVIOUS_SUCCESSFUL_COMMIT)

    if (!base) {
        echo "getChangedServices: no verified baseline commit (first build, or baseline not in this clone) — treating all as changed."
        return services
    }

    def diffOutput = sh(
        script: "git diff --name-only ${base} HEAD",
        returnStdout: true
    ).trim()

    def changedFiles = diffOutput ? diffOutput.split('\n') as List : []

    def sharedFilesTouched = changedFiles.any {
        it == 'pom.xml' ||
        it.startsWith('Jenkinsfile') ||
        it.startsWith('helm/') ||
        it.startsWith('vars/')
    }

    if (sharedFilesTouched) {
        echo "getChangedServices: shared file (pom.xml / Jenkinsfile / vars/ / helm/) changed since ${base.take(8)} — treating all as changed."
        return services
    }

    def changed = services.findAll { svc ->
        def svcPrefix = "${prefix}${svc}/"
        changedFiles.any { it.startsWith(svcPrefix) }
    }

    echo "getChangedServices: since ${base.take(8)}: ${changed.isEmpty() ? '(none)' : changed.join(', ')}"
    return changed
}

// Returns the baseline sha if it is well-formed AND exists in this clone, else ''.
// The format check keeps an odd env value from ever being interpolated into a shell command.
def resolveBaseline(Object candidate) {
    String sha = (candidate ?: '').toString().trim()
    if (!(sha ==~ /^[0-9a-fA-F]{7,64}$/)) {
        return ''
    }
    def exists = sh(
        script: "git cat-file -e ${sha}^{commit} > /dev/null 2>&1 && echo yes || echo no",
        returnStdout: true
    ).trim()
    return exists == 'yes' ? sha : ''
}
