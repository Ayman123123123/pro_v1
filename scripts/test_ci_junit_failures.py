#!/usr/bin/env python3
"""Offline regression checks for the JUnit failure check-summary extractor."""
import importlib.util
import tempfile
from pathlib import Path

spec = importlib.util.spec_from_file_location("ci_junit_failures", Path(__file__).with_name("ci-junit-failures.py"))
assert spec and spec.loader
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)
with tempfile.TemporaryDirectory() as temporary:
    directory = Path(temporary)
    assert "no test reports" in module.summarize(directory)
    (directory / "TEST-a.xml").write_text('''<testsuite tests="2">
      <testcase classname="RoomAdmissionTest" name="kicked user cannot rejoin">
        <failure message="expected FORBIDDEN but got OK"><![CDATA[sensitive system-output]]></failure>
      </testcase><testcase classname="RoomAdmissionTest" name="allowed user"/>
    </testsuite>''')
    (directory / "TEST-b.xml").write_text('''<testsuite tests="1"><testcase classname="MediaTest" name="ticket revoked">
      <error message="account still active"/></testcase></testsuite>''')
    text = module.summarize(directory, limit=1)
    assert "3 test(s), 2 failure(s)" in text
    assert "RoomAdmissionTest#kicked user cannot rejoin" in text
    assert "... and 1 more" in text
    assert "sensitive system-output" not in text
print("PASS: JUnit check summaries list failures without dumping test output")
