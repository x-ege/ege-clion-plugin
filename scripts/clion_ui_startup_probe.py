#!/usr/bin/env python3
"""Observe a fresh Linux CLion sandbox; default operation never sends UI input."""
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


class WindowInventoryUnavailable(RuntimeError):
    pass


class WindowInventory:
    """Allow only known early EWMH/display readiness failures, for at most 30 seconds."""
    def __init__(self, log_path, retry_seconds=30):
        self.log_path = log_path
        self.retry_seconds = retry_seconds
        self.unready_since = None

    def record(self, record):
        with self.log_path.open('a') as log:
            log.write(json.dumps(record) + '\n')

    def observe(self, env=None, timeout=5, now=None):
        now = time.monotonic() if now is None else now
        try:
            result = subprocess.run(['wmctrl', '-lG'], env=env, capture_output=True,
                                    text=True, check=False, timeout=timeout)
        except (OSError, subprocess.TimeoutExpired) as error:
            self.record({'time': now, 'execution_error': repr(error)})
            raise
        self.record({'time': now, 'returncode': result.returncode,
                     'stdout': result.stdout, 'stderr': result.stderr})
        if result.returncode == 0:
            self.unready_since = None
            return parse_windows(result.stdout)  # An empty successful list is a valid observation.
        readiness_error = re.fullmatch(
            r'\s*(?:Cannot open display\.|Cannot get client list properties\.\s*'
            r'\(_NET_CLIENT_LIST or _WIN_CLIENT_LIST\))?\s*', result.stderr)
        if result.returncode != 1 or result.stdout.strip() or not readiness_error:
            raise subprocess.CalledProcessError(result.returncode, result.args,
                                                output=result.stdout, stderr=result.stderr)
        if self.unready_since is None:
            self.unready_since = now
        if now - self.unready_since >= self.retry_seconds:
            raise WindowInventoryUnavailable(
                f'wmctrl remained unavailable for {self.retry_seconds}s; see {self.log_path}')
        return None  # Not an empty window list, and never evidence of GUI success.


def parse_windows(listing):
    """Read wmctrl's EWMH window inventory without changing focus or geometry."""
    windows = []
    for line in listing.splitlines():
        fields = line.split(None, 7)
        if len(fields) < 8 or not re.fullmatch(r'0x[0-9a-fA-F]+', fields[0]):
            raise ValueError(f'Malformed X11 window inventory: {line!r}')
        desktop, x, y, width, height = map(int, fields[1:6])
        if width <= 0 or height <= 0:
            raise ValueError('Invalid X11 window geometry')
        windows.append({'id': fields[0], 'title': fields[7], 'desktop': desktop,
                        'x': x, 'y': y, 'width': width, 'height': height})
    return windows


def ocr_screenshot(screenshot, env=None, timeout=15):
    """Keep the raw evidence; trim desktop padding and normalize dark dialogs for OCR."""
    normalized = screenshot.with_suffix('.ocr.png')
    subprocess.run(['convert', str(screenshot), '-trim', '+repage', '-colorspace', 'Gray',
                    '-negate', '-resize', '200%', str(normalized)], env=env,
                   capture_output=True, text=True, check=True, timeout=timeout)
    result = subprocess.run(['tesseract', str(normalized), 'stdout', '-l', 'eng', '--psm', '11'],
                            env=env, capture_output=True, text=True, check=True, timeout=timeout)
    return result.stdout


def classify(text, robot_observed=False, window_titles=()):
    text += '\n' + '\n'.join(window_titles)
    visible = re.sub(r'\s+', ' ', html.unescape(text)).lower()
    if re.search(r'user agreement|license agreement|privacy policy|data sharing|'
                 r'activate clion|activation|start trial|license server|evaluation license|'
                 r'log in to jetbrains|jetbrains account', visible):
        return 'blocked_agreement_or_activation'
    # Unknown windows must never be mistaken for a successful welcome screen.
    for title in window_titles:
        if not re.fullmatch(r'CLion(?: \d{4}\.\d+(?:\.\d+)?)?|Welcome to CLion', title.strip(), re.I):
            return 'unknown_window_detected'
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
    parser.add_argument('--agreement-authorization', default='',
                        help='Explicit expiring authorization for one manual User Agreement 1.4 test')
    args = parser.parse_args()
    if sys.platform != 'linux' or not os.environ.get('DISPLAY') or not 1 <= args.timeout <= 600:
        parser.error('Use Linux under Xvfb with a timeout of 1..600 seconds')
    for tool in ['openbox', 'wmctrl', 'import', 'convert', 'tesseract', 'java']:
        if not shutil.which(tool):
            raise RuntimeError(f'Missing observation tool: {tool}')
    checksum, runtime = preflight()
    output = ROOT / 'build/gui-startup-probe'
    output.mkdir(parents=True, exist_ok=True)
    agreement = None
    if args.agreement_authorization:
        from clion_ui_agreement import AgreementUI
        if not shutil.which('xdotool') or not shutil.which('identify'):
            raise RuntimeError('Missing authorized UI test tools')
        agreement = AgreementUI(ROOT, output, os.environ, args.agreement_authorization)
    # The server URL and client constructor are hard-coded loopback; no tunnels or public binding.
    env = dict(os.environ, LC_ALL='C.UTF-8')
    wm = ide = None
    outcome = 'startup_timeout'
    observed = False
    started = time.monotonic()
    deadline = started + args.timeout
    count = 0
    titles = []
    inventory_reader = WindowInventory(output / 'wmctrl-observations.jsonl')
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
                windows = inventory_reader.observe(env, min(5, max(1, deadline-time.monotonic())))
                if windows is None:
                    titles = []
                    outcome = 'window_inventory_timeout'
                    print(f'Observation {count}: X11 inventory not ready; bounded retry', flush=True)
                    if wm.poll() is not None:
                        raise WindowInventoryUnavailable('Openbox exited before X11 inventory became ready')
                    if ide.poll() is not None:
                        outcome = 'ide_launcher_exited'
                        break
                    time.sleep(min(1, max(0, deadline-time.monotonic())))
                    continue
                (output / f'windows-{count:03}.json').write_text(json.dumps(windows, indent=2) + '\n')
                # X11 titles work before RemoteRobot's appFrameCreated callback starts its server.
                titles = [window['title'] for window in windows]
                try:
                    text = ocr_screenshot(screenshot, env, min(15, max(1, deadline-time.monotonic())))
                except (OSError, subprocess.SubprocessError) as error:
                    # Title detection must still stop at early dialogs if OCR fails.
                    text = ''
                    with (output / 'ocr-errors.log').open('a') as log:
                        log.write(f'Observation {count}: {error}\n')
                (output / f'screen-{count:03}.txt').write_text(text)
                # Save each window at its actual ID, without guessed coordinates or UI actions.
                for window in windows:
                    subprocess.run(['import', '-silent', '-window', window['id'],
                                    str(output / f'window-{count:03}-{window["id"]}.png')],
                                   env=env, check=True, timeout=5)
                outcome = classify(text, observed, titles)
                if outcome in {'blocked_agreement_or_activation', 'unknown_window_detected'}:
                    if (agreement is not None and not agreement.attempted
                            and titles == ['CLion User Agreement']):
                        agreement.accept(windows)
                        print('Approved User Agreement 1.4 Continue clicked; observing next window', flush=True)
                        time.sleep(1)
                        continue
                    if (agreement is not None and agreement.telemetry_attempted and not agreement.trial_option_attempted
                            and 'Licenses' in titles):
                        agreement.inspect_trial_options(windows)
                        print('Start trial radio option selected; saved actual trial page without login or activation', flush=True)
                        time.sleep(1)
                        continue
                    if (agreement is not None and agreement.attempted and not agreement.telemetry_attempted
                            and titles == ['Data Sharing']):
                        agreement.decline_telemetry(windows)
                        print("Audited optional Data Sharing: Don't Send clicked; observing next window", flush=True)
                        time.sleep(1)
                        continue
                    print(f'Observation {count}: {outcome}; robot_observed={observed}', flush=True)
                    break
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
                outcome = classify(text, observed, titles)
                print(f'Observation {count}: {outcome}; robot_observed={observed}', flush=True)
                if outcome in {'blocked_agreement_or_activation', 'unknown_window_detected', 'welcome_observed'}:
                    break
                if ide.poll() is not None:
                    outcome = 'ide_launcher_exited'
                    break
                outcome = 'startup_timeout'
                time.sleep(min(5, max(0, deadline-time.monotonic())))
        except WindowInventoryUnavailable:
            outcome = 'window_inventory_failed'
            raise
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
                      'sandbox_jar_matches_formal_zip': True,
                      'ui_actions_performed': agreement.clicks if agreement is not None else 0,
                      'agreement_1_4_attempted': agreement.attempted if agreement is not None else False,
                      'optional_telemetry_declined': (agreement.telemetry_attempted and titles != ['Data Sharing'])
                                                    if agreement is not None else False,
                      'trial_option_selection_attempted': agreement.trial_option_attempted if agreement is not None else False,
                      'last_x11_window_titles': titles,
                      'wizard_build_run_verified': False}
            (output / 'report.json').write_text(json.dumps(report, indent=2) + '\n')
            print(json.dumps(report, indent=2), flush=True)
    return 0 if outcome == 'welcome_observed' else 20


if __name__ == '__main__':
    sys.exit(main())
