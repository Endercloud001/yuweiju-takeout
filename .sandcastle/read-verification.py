"""Read verifier evidence as UTF-8 and print a bounded JSON summary."""
import argparse
import json
from pathlib import Path
import re

FIELDS = ('commit', 'exitCode', 'stopped', 'independentVerification',
          'humanAcceptance', 'authMounted', 'modelCalled')
TESTS = re.compile(r'Tests run: (\d+), Failures: (\d+), Errors: (\d+), Skipped: (\d+)')


def summarize(path):
    path = Path(path).resolve()
    record = json.loads(path.read_text(encoding='utf-8-sig'))
    result = {'source': str(path), **{key: record.get(key) for key in FIELDS}}
    checks = record.get('checks', [])
    result['checkCount'] = len(checks)
    result['checks'] = [{key: check.get(key) for key in ('index', 'status', 'exitCode')}
                        for check in checks[:40]]
    result['checksOmitted'] = max(0, len(checks) - 40)
    logs = []
    # Follow verifier-generated check filenames only, never paths in input JSON.
    for check in checks[:40]:
        index = check.get('index')
        if type(index) is not int or index < 0:
            continue
        log = path.parent / f'check-{index}.log'
        if not log.is_file():
            continue
        matches = []
        with log.open(encoding='utf-8-sig') as stream:
            for line in stream:
                matches.extend(TESTS.findall(line))
                matches = matches[-3:]
        logs.append({'source': str(log), 'testSummaries': [dict(zip(
            ('tests', 'failures', 'errors', 'skipped'), map(int, values))) for values in matches]})
    result['logs'] = logs
    return result


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('resources', type=Path)
    args = parser.parse_args()
    try:
        print(json.dumps(summarize(args.resources), ensure_ascii=True))
    except (OSError, UnicodeError, ValueError, TypeError, AttributeError) as error:
        print(json.dumps({'source': str(args.resources.resolve()), 'errorType': type(error).__name__}))
        raise SystemExit(1)
