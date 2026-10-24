"""Heuristic pre-publication scan. Reports locations/categories, never matched values.

This is not a guarantee of no secrets, ownership, or runtime security. Review the
staged file list and every finding before publishing. Generated outputs are skipped.
"""
from pathlib import Path
import json
import re
import subprocess

ROOT = Path(__file__).resolve().parent.parent
SKIP = {'.git', '.gradle', '.kotlin', 'build', '.local', '__pycache__'}
PATTERNS = {
    'private-key': r'-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----',
    'provider-token': r'(?:gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,}|sk-[A-Za-z0-9_-]{20,}|AKIA[A-Z0-9]{16})',
    'personal-machine-path': r'(?:C:[/\\]Users[/\\]|G:[/\\]AI Agent|E:[/\\])',
    'literal-session-cookie': r'(?:pt_key|cookie2|PASS_ID|PDDAccessToken|sessionid|sessionid_ss|sid_guard|msToken|ttwid)=[A-Za-z0-9_%+/.=-]{24,}',
    'long-sensitive-literal': r'(?i)(?:cookie|token|password|api_key|secret|authorization)[\w]*[\"\']?\s*[:=]\s*[\"\'][A-Za-z0-9_+/=. -]{16,}[\"\']',
}
DISALLOWED_SUFFIXES = {'.apk', '.aab', '.jks', '.keystore', '.p12', '.pfx', '.hprof', '.log', '.zip'}
REVIEWED_DOC_IMAGES = {f'docs/images/{name}-mockup.png' for name in ['home','detail','identity','my']}
findings = []
reviewed = []
tracked = subprocess.run(['git', 'ls-files', '--cached', '--others', '--exclude-standard', '-z'], cwd=ROOT, capture_output=True)
paths = [ROOT / p for p in tracked.stdout.decode('utf-8').split('\0') if p] if tracked.returncode == 0 and tracked.stdout else list(ROOT.rglob('*'))
for path in sorted(paths):
    rel = path.relative_to(ROOT)
    if not path.is_file() or SKIP.intersection(rel.parts):
        continue
    if path.suffix.lower() in DISALLOWED_SUFFIXES or path.name == 'local.properties':
        findings.append({'path': rel.as_posix(), 'category': 'excluded-artifact-type'})
        continue
    if path.suffix.lower() in {'.jar', '.png'}:
        if rel.as_posix() not in {'gradle/wrapper/gradle-wrapper.jar', 'app/src/main/res/drawable-nodpi/rice_paper.png'} | REVIEWED_DOC_IMAGES:
            findings.append({'path': rel.as_posix(), 'category': 'unexpected-binary'})
        continue
    try:
        content = path.read_text(encoding='utf-8-sig')
    except UnicodeError:
        findings.append({'path': rel.as_posix(), 'category': 'non-utf8-needs-review'})
        continue
    for category, pattern in PATTERNS.items():
        for match in re.finditer(pattern, content):
            result = {'path': rel.as_posix(), 'line': content.count('\n', 0, match.start()) + 1, 'category': category}
            if category == 'long-sensitive-literal' and rel.as_posix() == 'app/src/test/java/com/halo/expressassistant/hub/PddDiagnosticPayloadTest.kt' and 'SYNTHETIC_DIAGNOSTIC_REDACTION_SENTINEL' in match.group():
                result['review'] = 'Explicit synthetic sentinel tests that diagnostics remove secret fields; not an actual credential.'
                reviewed.append(result)
            else:
                findings.append(result)
    if rel.as_posix().startswith('app/'):
        for match in re.finditer(r'(?<![0-9])1[3-9][0-9]{9}(?![0-9])', content):
            result = {'path': rel.as_posix(), 'line': content.count('\n', 0, match.start()) + 1, 'category': 'phone-shaped-literal'}
            if rel.as_posix().startswith('app/src/test/') and match.group() == '19900000000' and 'Synthetic phone-shaped rejection fixtures' in content:
                result['review'] = 'Explicit parser rejection fixture; not imported account data; never dialed/submitted.'
                reviewed.append(result)
            else:
                findings.append(result)
print(json.dumps({'unresolved_findings': findings, 'reviewed_synthetic_fixtures': reviewed, 'scope': 'Heuristic text and file-type review only; hashes are not phone numbers.'}, ensure_ascii=False, indent=2))
raise SystemExit(1 if findings else 0)
