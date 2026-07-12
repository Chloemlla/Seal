#!/usr/bin/env bash
# Print every Android Lint text/HTML report found under the workspace.
# Intended for CI: always run after lint so the log contains all findings
# even when the lint task fails.
set -u

echo "=== Android Lint report dump ==="
echo "cwd=$(pwd)"
echo "date_utc=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
echo

mapfile -t reports < <(
  find . \
    \( -path './.git' -o -path './.gradle' -o -path '*/.gradle/*' -o -path '*/build/tmp/*' \) -prune -o \
    \( \
      -path '*/build/reports/lint-results*.txt' -o \
      -path '*/build/reports/lint-results*.html' -o \
      -path '*/build/reports/lint-results*.xml' -o \
      -path '*/build/intermediates/lint_intermediate_text_report/*/lint-results-*.txt' -o \
      -path '*/build/intermediates/lint_intermediate_text_report/*/*/lint-results-*.txt' -o \
      -name 'lint-results-*.txt' -o \
      -name 'lint-results-*.html' -o \
      -name 'lint-results-*.xml' \
    \) -type f -print 2>/dev/null | sort -u
)

if [[ ${#reports[@]} -eq 0 ]]; then
  echo "No Android Lint report files found."
  echo "Searched: **/build/reports/lint-results-*.{txt,html,xml}"
  echo "          **/build/intermediates/lint_intermediate_text_report/**/lint-results-*.txt"
  exit 0
fi

echo "Found ${#reports[@]} lint report file(s):"
printf '  - %s\n' "${reports[@]}"
echo

for report in "${reports[@]}"; do
  echo "================================================================"
  echo "FILE: $report"
  echo "SIZE: $(wc -c < "$report" | tr -d ' ') bytes"
  echo "================================================================"
  case "$report" in
    *.txt|*.xml)
      # Cap extremely large dumps while still covering typical full lint output.
      if [[ $(wc -l < "$report") -gt 5000 ]]; then
        echo "(file has >5000 lines; printing first 2500 and last 500)"
        head -n 2500 "$report"
        echo
        echo "... [middle omitted] ..."
        echo
        tail -n 500 "$report"
      else
        cat "$report"
      fi
      ;;
    *.html)
      echo "(HTML report — path only in log; download the artifact for full HTML)"
      # Still extract issue lines if present for quick scanning.
      if grep -Eoq 'id="|Severity|Error|Warning|Error:' "$report" 2>/dev/null; then
        echo "--- HTML issue snippets (best-effort) ---"
        grep -E 'id="|Severity:|Error:|Warning:|class="severity"' "$report" | head -n 200 || true
      fi
      ;;
    *)
      echo "(unknown extension; printing as text, truncated to 2000 lines)"
      head -n 2000 "$report"
      ;;
  esac
  echo
done

echo "=== end Android Lint report dump ==="
