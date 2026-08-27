#!/usr/bin/env bash
set -euo pipefail

grep_args=(-n -I -i -E)
if [[ "${1:-}" == "--cached" ]]; then
    grep_args+=(--cached)
elif [[ $# -ne 0 ]]; then
    echo "usage: $0 [--cached]" >&2
    exit 2
fi

secret_name='(password|passwd|pwd|secret|token|api[_-]?key)'
quoted_literal="${secret_name}[[:space:]]*[:=][[:space:]]*[\"'][^\"'$]{8,}[\"']"
yaml_literal="${secret_name}[[:space:]]*:[[:space:]]*[[:alnum:]_./+=-]{8,}[[:space:]]*$"
environment_default='\$\{[A-Z][A-Z0-9_]*(PASSWORD|PASSWD|PWD|SECRET|TOKEN|KEY):[^?}][^}]*\}'
sql_literal="LOGIN[[:space:]]+PASSWORD[[:space:]]+[\"'][^\"']+[\"']"
operational_pattern="(${quoted_literal}|${yaml_literal}|${environment_default}|${sql_literal})"

operational_paths=(.github app build-logic docker gradle modules scripts shared build.gradle.kts compose.yaml settings.gradle.kts)

if git grep "${grep_args[@]}" -- "$operational_pattern" -- "${operational_paths[@]}"; then
    echo "Hardcoded credential candidate detected in tracked operational files." >&2
    echo "Use runtime configuration or an ephemeral test value instead." >&2
    exit 1
fi

high_confidence_pattern='(AKIA[0-9A-Z]{16}|github_pat_[[:alnum:]_]{20,}|gh[pousr]_[[:alnum:]]{20,}|-----BEGIN ([A-Z ]+)?PRIVATE KEY-----|eyJ[[:alnum:]_-]{10,}\.[[:alnum:]_-]{10,}\.[[:alnum:]_-]{10,})'

if git grep "${grep_args[@]}" -- "$high_confidence_pattern"; then
    echo "High-confidence credential material detected in tracked files." >&2
    exit 1
fi

sensitive_file_pattern='(^|/)(\.env($|\.)|secrets?\.|credentials?\.)|\.(pem|key|p12|jks)$'
tracked_sensitive_files=$(git ls-files | grep -E -i "$sensitive_file_pattern" | grep -v -E '(^|/)\.env\.example$' || true)
if [[ -n "$tracked_sensitive_files" ]]; then
    echo "$tracked_sensitive_files"
    echo "Sensitive file candidate is tracked." >&2
    exit 1
fi

tracked_artifact_pattern='(^|/)(\.gradle|build)/|\.(log|class)$'
tracked_artifacts=$(git ls-files | grep -E "$tracked_artifact_pattern" || true)
if [[ -n "$tracked_artifacts" ]]; then
    echo "$tracked_artifacts"
    echo "Build artifact is tracked." >&2
    exit 1
fi

echo "Secret scan passed."
