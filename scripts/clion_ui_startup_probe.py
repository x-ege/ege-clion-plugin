#!/usr/bin/env python3
"""Observe a fresh Linux CLion sandbox. Never click, accept dialogs or enter input."""
import argparse
import hashlib
import html
import io
import json
import os
from pathlib import Path
import re
import shutil
import signal
import subprocess
import sys
import time
import urllib.request
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]
ROBOT_VERSION = '0.11.23'


def classify(text, robot_observed=False):
    visible = re.sub(r'\s+', ' ', html.unescape(text)).lower()
    if re.search(r'user agreement|license agreement|privacy policy|data sharing|'
                 r'activate clion|activation|start trial|license server|evaluation license|'
                 r'log in to jetbrains|jetbrains account', visible):
        return 'blocked_agreement_or_activation'
    if robot_observed and re.search(r'welcomeframe|welcome to clion', visible):
        return 'welcome_observed'
    return 'starting_or_unknown_window'


def preflight():
    archives = list((ROOT / 'build/distributions').glob('*.zip'))
    if len(archives) != 1:
        raise RuntimeError('Expected exactly one formal plugin ZIP')
    delivered = []
    with zipfile.ZipFile(archives[0]) as outer:
        for name in outer.namelist():
            if name.endswith('.jar'):
                data = outer.read(name)
                with zipfile.ZipFile(io.BytesIO(data)) as jar:
                    if 'org/xege/project/EgeResourceCopier.class' in jar.namelist():
                        delivered.append(data)
    installed = []
    robot = []
    for path in (ROOT / 'build/idea-sandbox/plugins-uiTest').rglob('*.jar'):
        with zipfile.ZipFile(path) as jar:
            if 'org/xege/project/EgeResourceCopier.class' in jar.namelist():
                installed.append(path.read_bytes())
            if 'META-INF/plugin.xml' in jar.namelist():
                descriptor = ET.fromstring(jar.read('META-INF/plugin.xml'))
                if descriptor.findtext('id') == 'com.jetbrains.test.robot-server-plugin':
                    robot.append(descriptor.findtext('version'))
    if len(delivered) != 1 or installed != delivered:
        raise RuntimeError('UI sandbox does not contain the exact formal plugin JAR')
    if robot != [ROBOT_VERSION]:
        raise RuntimeError(f'Unexpected Robot server versions: {robot}')
    runtime = ROOT / 'build/gui-probe-runtime'
    if not (runtime / f'remote-robot-{ROBOT_VERSION}.jar').is_file():
        raise RuntimeError('Matching fixed-version RemoteRobot client is absent')
    return hashlib.sha256(archives[0].read_bytes()).hexdigest(), runtime


def stop(process):
    if process is None:
        return
    try:
        os.killpg(process.pid, signal.SIGTERM)
        process.wait(timeout=5)
    except (ProcessLookupError, subprocess.TimeoutExpired):
        try:
            os.killpg(process.pid, signal.SIGKILL)
        except ProcessLookupError:
            pass


def stop_sandbox_ide(sig=signal.SIGTERM):
    # Gradle may detach its single-use daemon. Match only this probe's isolated IDE config.
    expected = '-Didea.config.path=' + str(ROOT / 'build/idea-sandbox/config-uiTest')
    listing = subprocess.check_output(['ps', '-eo', 'pid,args'], text=True)
    for line in listing.splitlines():
        pid, _, args = line.strip().partition(' ')
        if expected in args and 'com.intellij.idea.Main' in args:
            try:
                os.kill(int(pid), sig)
            except ProcessLookupError:
                pass


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--timeout', type=int, default=600)
    args = parser.parse_args()
    if sys.platform != 'linux' or not os.environ.get('DISPLAY') or not 1 <= args.timeout <= 600:
        parser.error('Use Linux under Xvfb with a timeout of 1..600 seconds')
    for tool in ['openbox', 'import', 'tesseract', 'java']:
        if not shutil.which(tool):
            raise RuntimeError(f'Missing observation tool: {tool}')
    checksum, runtime = preflight()
    output = ROOT / 'build/gui-startup-probe'
    output.mkdir(parents=True, exist_ok=True)
    # The server URL and client constructor are hard-coded loopback; no tunnels or public binding.
    env = dict(os.environ, LC_ALL='C.UTF-8')
    wm = ide = None
    outcome = 'startup_timeout'
    observed = False
    started = time.monotonic()
    deadline = started + args.timeout
    count = 0
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    with (output / 'window-manager.log').open('w') as wm_log, (output / 'clion-launch.log').open('w') as ide_log:
        try:
            wm = subprocess.Popen(['openbox', '--sm-disable'], env=env, stdout=wm_log,
                                  stderr=subprocess.STDOUT, start_new_session=True)
            ide = subprocess.Popen(['./gradlew', '--offline', '--no-daemon', 'runIdeForUiTests', '--console=plain'],
                                   cwd=ROOT, env=env, stdout=ide_log, stderr=subprocess.STDOUT, start_new_session=True)
            while time.monotonic() < deadline:
                count += 1
                screenshot = output / f'screen-{count:03}.png'
                subprocess.run(['import', '-silent', '-window', 'root', str(screenshot)],
                               env=env, check=True, timeout=min(10, max(1, deadline-time.monotonic())))
                ocr = subprocess.run(['tesseract', str(screenshot), 'stdout', '-l', 'eng'], env=env,
                                     capture_output=True, text=True, check=True,
                                     timeout=min(15, max(1, deadline-time.monotonic())))
                (output / f'screen-{count:03}.txt').write_text(ocr.stdout)
                text = ocr.stdout
                # Try the loopback tree even when an early modal prevents the plugin starting.
                try:
                    with opener.open('http://127.0.0.1:8082/', timeout=3) as response:
                        tree = response.read().decode()
                    (output / 'http-hierarchy.html').write_text(tree)
                    text += '\n' + tree
                    if not observed:
                        classpath = os.pathsep.join(str(p) for p in sorted(runtime.glob('*.jar')))
                        with (output / 'robot-client.log').open('w') as log:
                            subprocess.run(['java', '-cp', classpath, 'org.xege.probe.StartupObserver', str(output)],
                                           stdout=log, stderr=subprocess.STDOUT, check=True,
                                           timeout=min(35, max(1, deadline-time.monotonic())))
                        observed = True
                except (OSError, subprocess.SubprocessError) as error:
                    with (output / 'robot-connection.log').open('a') as log:
                        log.write(f'Observation {count}: {error}\n')
                outcome = classify(text, observed)
                print(f'Observation {count}: {outcome}; robot_observed={observed}', flush=True)
                if outcome in {'blocked_agreement_or_activation', 'welcome_observed'}:
                    break
                if ide.poll() is not None:
                    outcome = 'ide_launcher_exited'
                    break
                outcome = 'startup_timeout'
                time.sleep(min(5, max(0, deadline-time.monotonic())))
        except Exception:
            outcome = 'observation_failed'
            raise
        finally:
            stop_sandbox_ide()
            stop(ide)
            stop(wm)
            stop_sandbox_ide(signal.SIGKILL)
            report = {'outcome': outcome, 'elapsed_seconds': round(time.monotonic()-started, 1),
                      'startup_limit_seconds': args.timeout, 'robot_version': ROBOT_VERSION,
                      'robot_hierarchy_and_screenshot_observed': observed, 'zip_sha256': checksum,
                      'sandbox_jar_matches_formal_zip': True, 'ui_actions_performed': 0,
                      'wizard_build_run_verified': False}
            (output / 'report.json').write_text(json.dumps(report, indent=2) + '\n')
            print(json.dumps(report, indent=2), flush=True)
    return 0 if outcome == 'welcome_observed' else 20


if __name__ == '__main__':
    sys.exit(main())
