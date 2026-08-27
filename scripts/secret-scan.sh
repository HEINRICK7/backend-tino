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
environment_default='\$\{[A-Z][A-Z0-9_]*(PASSWORD|PASSWD|PWD|SECRET|TOKEN|KEY):[^}]+\}'
pattern="(${quoted_literal}|${yaml_literal}|${environment_default})"

scan_paths=(app modules shared .github)

if git grep "${grep_args[@]}" -- "$pattern" -- "${scan_paths[@]}"; then
    echo "Hardcoded credential candidate detected in tracked application sources." >&2
    echo "Use runtime configuration or an ephemeral test value instead." >&2
    exit 1
fi

echo "Secret scan passed."
