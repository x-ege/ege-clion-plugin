"""One manually authorized, expiring acceptance of the pinned User Agreement 1.4 UI.

This module can claim one explicitly authorized official trial, never reset it or enter account data.
Coordinates come from current OCR or exact visual templates; the unchecked checkbox is matched as
a visual prerequisite, and its checkmark is verified before clicking Continue.
"""
import csv
from datetime import datetime, timezone
import hashlib
import io
import json
from pathlib import Path
import re
import subprocess
import time
import zipfile

AUTHORIZATION_PREFIX = 'ege14-'
EXPIRES = datetime(2026, 10, 9, tzinfo=timezone.utc)
AGREEMENT_SHA256 = '2653cfd53ae4dc1100ba9cddbbc030ef19bd54f22031862eef9c4c15c58d9891'
TITLE = 'CLion User Agreement'
TELEMETRY_TITLE = 'Data Sharing'
TELEMETRY_BODY_SHA256 = '40b4002317c89d205f29160ab089f9b879ac9a75da9c7a7e13aa2adca7154cbe'
LICENSES_PIXELS_SHA256 = 'f3dbeaee6093ce504f8059e9536e370fa7d34f24dd72a54f031c97b6d07bb959'
TRIAL_PAGE_PIXELS_SHA256 = '8c119cdf6c3c80005e966a9e0865d01f7c7c7dfc9162d1e054b736f222fad4a5'


def check_authorization(reference, env, now=None):
    now = now or datetime.now(timezone.utc)
    if not re.fullmatch(AUTHORIZATION_PREFIX + r'[0-9a-f]{40}', reference) or now >= EXPIRES:
        raise RuntimeError('Missing or expired authorization for this isolated agreement test')
    if env.get('GITHUB_EVENT_NAME') != 'pull_request' or env.get('GITHUB_RUN_ATTEMPT') != '1':
        raise RuntimeError('Agreement authorization is only valid for one first-run PR label event')
    event = json.loads(Path(env['GITHUB_EVENT_PATH']).read_text())
    head = event['pull_request']['head']
    if (event.get('action') != 'labeled' or event.get('number') != 8
            or event['repository']['full_name'] != 'x-ege/ege-clion-plugin'
            or event['sender']['login'] != 'wysaid'
            or head['ref'] != 'fix/clion-new-project-wizard'
            or reference != AUTHORIZATION_PREFIX + head['sha']
            or event['label']['name'] != reference):
        raise RuntimeError('Authorization event does not match the exact approved PR head')
    checked_out = subprocess.check_output(['git', 'rev-parse', 'HEAD'], text=True, timeout=5).strip()
    if checked_out != head['sha']:
        raise RuntimeError('Checked-out commit differs from the one-time authorization')


def verify_bundled_agreement(cache):
    candidates = list(cache.glob('*/clion-2023.3/lib/app-client.jar'))
    if len(candidates) != 1:
        raise RuntimeError('Expected one CLion 2023.3 distribution with the approved agreement')
    with zipfile.ZipFile(candidates[0]) as jar:
        agreement = jar.read('eua.html')
    if hashlib.sha256(agreement).hexdigest() != AGREEMENT_SHA256:
        raise RuntimeError('Bundled agreement fingerprint differs from approved User Agreement 1.4')
    return AGREEMENT_SHA256


def pixels(image):
    size = subprocess.check_output(['identify', '-format', '%w %h', str(image)], text=True, timeout=5)
    width, height = map(int, size.split())
    data = subprocess.check_output(['convert', str(image), '-depth', '8', 'rgb:-'], timeout=5)
    if len(data) != width * height * 3:
        raise RuntimeError('Unexpected screenshot pixel format')
    return width, height, data


def region(image, x, y, width=16, height=16):
    iw, ih, data = image
    if min(x, y) < 0 or x + width > iw or y + height > ih:
        raise RuntimeError('Control region lies outside current screenshot')
    return b''.join(data[((y+r)*iw+x)*3:((y+r)*iw+x+width)*3] for r in range(height))


def words(image):
    normalized = image.with_suffix('.controls.png')
    # Do not trim here: TSV positions must retain their exact window-relative origin.
    subprocess.run(['convert', str(image), '-colorspace', 'Gray', '-negate', '-resize', '200%',
                    str(normalized)], check=True, capture_output=True, timeout=5)
    result = subprocess.run(['tesseract', str(normalized), 'stdout', '-l', 'eng', '--psm', '11', 'tsv'],
                            check=True, capture_output=True, text=True, timeout=10)
    normalized.with_suffix('.tsv').write_text(result.stdout)
    return [w for w in csv.DictReader(io.StringIO(result.stdout), delimiter='\t') if w['text'].strip()]


def box(word):
    return tuple(int(word[key]) // 2 for key in ['left', 'top', 'width', 'height'])


def acceptance_label(ocr):
    text = ' '.join(w['text'] for w in ocr)
    if not re.search(r'JETBRAINS USER AGREEMENT', text) or not re.search(
            r'Version 1\.4, effective as of September 22, 2021', text):
        raise RuntimeError('Actual dialog version/date/title does not match approved agreement')
    lines = {}
    for word in ocr:
        key = tuple(word[k] for k in ['block_num', 'par_num', 'line_num'])
        lines.setdefault(key, []).append(word)
    labels = [line for line in lines.values() if re.search(
        r'confirm that .*have read and accept the terms of this User Agreement',
        ' '.join(w['text'] for w in line))]
    if len(labels) != 1:
        raise RuntimeError('Cannot uniquely identify the acceptance checkbox label')
    label = labels[0]
    confirm = [w for w in label if w['text'].lower() == 'confirm' and float(w['conf']) >= 85]
    if len(confirm) != 1:
        raise RuntimeError('Acceptance label OCR is ambiguous')
    return label, confirm[0]


def unchecked_box(image, label, template):
    left = min(box(w)[0] for w in label)
    center = sum(box(w)[1] + box(w)[3] // 2 for w in label) // len(label)
    matches = []
    for y in range(max(0, center-14), min(image[1]-16, center+6)+1):
        for x in range(max(0, left-40), max(0, left-16)+1):
            candidate = region(image, x, y)
            if max(abs(a-b) for a, b in zip(candidate, template)) <= 3:
                matches.append((x, y))
    if len(matches) != 1:
        raise RuntimeError('Actual unchecked checkbox does not uniquely match the observed fixture')
    return matches[0]


def checked_box(data):
    # A new bright checkmark inside the verified 16px checkbox is required.
    return sum(min(data[(y*16+x)*3:(y*16+x+1)*3]) > 150
               for y in range(3, 13) for x in range(3, 13)) >= 5


def telemetry_decline_button(image, ocr):
    width, height, data = image
    # Exact pixels of the audited optional telemetry description, excluding button hover states.
    # Any new text, terms, size, or appearance must stop before input is sent.
    if (width, height) != (598, 435) or hashlib.sha256(data[:width*(height-60)*3]).hexdigest() != TELEMETRY_BODY_SHA256:
        raise RuntimeError('Data Sharing content differs from the audited optional statistics prompt')
    candidates = []
    for first in ocr:
        if first['text'] != "Don't" or float(first['conf']) < 85:
            continue
        x, y, w, h = box(first)
        for second in ocr:
            sx, sy, sw, sh = box(second)
            if (second['text'] == 'Send' and float(second['conf']) >= 85
                    and 0 <= sx-(x+w) <= 12 and abs(sy-y) <= 3 and y > height-60):
                candidates.append(first)
    if len(candidates) != 1:
        raise RuntimeError("Cannot uniquely locate Don't Send in the actual telemetry dialog")
    return candidates[0]


def trial_option(image, ocr=None):
    width, height, data = image
    if (width, height) != (814, 455) or hashlib.sha256(data).hexdigest() != LICENSES_PIXELS_SHA256:
        raise RuntimeError('License window differs from the audited unactivated CLion trial selector')
    fixture = pixels(Path(__file__).resolve().parents[1] / 'tests/fixtures/clion-ui/licenses-before-trial.png')
    if hashlib.sha256(fixture[2]).hexdigest() != LICENSES_PIXELS_SHA256:
        raise RuntimeError('Audited trial selector fixture changed')
    # These bounds select text from the immutable evidence, never the runtime click location.
    # Runner Tesseract 5.3 entirely misses this radio label; match its exact pixels instead.
    needle = region(fixture, 414, 14, 64, 20)
    rows = [needle[r*64*3:(r+1)*64*3] for r in range(20)]
    matches = []
    for y in range(31):
        for x in range(width-64+1):
            if all(data[((y+r)*width+x)*3:((y+r)*width+x+64)*3] == row
                   for r, row in enumerate(rows)):
                matches.append((x, y))
    if len(matches) != 1:
        raise RuntimeError('Start trial visual label is not uniquely present in the actual window')
    x, y = matches[0]
    # Match the OCR box interface: its coordinates are normalized at 2x capture scale.
    return {'text': 'Start trial', 'left': str(x*2), 'top': str(y*2),
            'width': '128', 'height': '40'}


def trial_start_button(image):
    width, height, data = image
    if (width, height) != (814, 455) or hashlib.sha256(data).hexdigest() != TRIAL_PAGE_PIXELS_SHA256:
        raise RuntimeError('Trial claim page differs from the audited no-login, no-payment prompt')
    fixture = pixels(Path(__file__).resolve().parents[1] / 'tests/fixtures/clion-ui/trial-page-before-claim.png')
    if hashlib.sha256(fixture[2]).hexdigest() != TRIAL_PAGE_PIXELS_SHA256:
        raise RuntimeError('Audited trial claim fixture changed')
    needle = region(fixture, 274, 189, 92, 25)
    rows = [needle[r*92*3:(r+1)*92*3] for r in range(25)]
    matches = []
    for y in range(50, height-25+1):
        for x in range(width-92+1):
            if all(data[((y+r)*width+x)*3:((y+r)*width+x+92)*3] == row
                   for r, row in enumerate(rows)):
                matches.append((x, y))
    if len(matches) != 1:
        raise RuntimeError('Start Trial claim button is not uniquely present in the actual window')
    x, y = matches[0]
    return {'text': 'Start Trial', 'left': str(x*2), 'top': str(y*2), 'width': '184', 'height': '50'}


def unique_ui_label(ocr, tokens):
    """Locate one high-confidence adjacent rendered label in current window-relative OCR."""
    candidates = []
    for index, word in enumerate(ocr):
        group = ocr[index:index+len(tokens)]
        if len(group) != len(tokens) or any(w['text'].lower() != token.lower()
                or float(w.get('conf', 0)) < 85 for w, token in zip(group, tokens)):
            continue
        boxes = [box(w) for w in group]
        if any(abs(y-boxes[0][1]) > 3 for _, y, _, _ in boxes):
            continue
        if any(not 0 <= boxes[n+1][0]-(boxes[n][0]+boxes[n][2]) <= 12
               for n in range(len(boxes)-1)):
            continue
        x, y = boxes[0][:2]
        right = max(bx+bw for bx, _, bw, _ in boxes)
        bottom = max(by+bh for _, by, _, bh in boxes)
        candidates.append({'text': ' '.join(tokens), 'left': str(x*2), 'top': str(y*2),
                           'width': str((right-x)*2), 'height': str((bottom-y)*2)})
    if len(candidates) != 1:
        raise RuntimeError('Actual UI label is missing or ambiguous: ' + ' '.join(tokens))
    return candidates[0]


class AgreementUI:
    def __init__(self, root, output, env, reference):
        check_authorization(reference, env)
        cache = Path.home() / '.gradle/caches/modules-2/files-2.1/com.jetbrains.intellij.clion/clion/2023.3'
        self.fingerprint = verify_bundled_agreement(cache)
        self.output = output
        self.env = env
        self.attempted = False
        self.telemetry_attempted = False
        self.trial_option_attempted = False
        self.trial_start_attempted = False
        self.trial_start_time = None
        if (output / 'trial-start-once.json').exists():
            raise RuntimeError('Trial claim was already attempted in this run; refusing another attempt')
        self.clicks = 0
        fixture = root / 'tests/fixtures/clion-ui/user-agreement-1.4.png'
        if hashlib.sha256(fixture.read_bytes()).hexdigest() != '7975e25994d9974278be94e4dec47c8d22b06fd96ff9eaea15d575d3f3318c19':
            raise RuntimeError('Agreement screenshot fixture differs from the audited original')
        # This location is in the immutable original fixture, never a runtime click coordinate.
        self.unchecked_template = region(pixels(fixture), 373, 606)
        self.record('authorization_validated', fingerprint=self.fingerprint,
                    reference=reference, expires=EXPIRES.isoformat())

    def record(self, action, **details):
        with (self.output / 'agreement-actions.jsonl').open('a') as log:
            log.write(json.dumps({'action': action, **details}) + '\n')

    def capture(self, window_id, name):
        path = self.output / name
        subprocess.run(['import', '-silent', '-window', window_id, str(path)],
                       check=True, env=self.env, timeout=5)
        return path

    def click_word(self, window_id, word, label, expected_title=TITLE):
        title = subprocess.check_output(['xdotool', 'getwindowname', window_id],
                                        env=self.env, text=True, timeout=5).strip()
        if title != expected_title:
            raise RuntimeError('Window changed before authorized click')
        x, y, width, height = box(word)
        subprocess.run(['xdotool', 'windowactivate', '--sync', window_id],
                       env=self.env, check=True, timeout=5)
        if subprocess.check_output(['xdotool', 'getwindowname', window_id],
                                   env=self.env, text=True, timeout=5).strip() != expected_title:
            raise RuntimeError('Window changed during activation before authorized click')
        self.record('click_attempt', target=label, window_id=window_id,
                    x=x+width//2, y=y+height//2)
        self.clicks += 1
        subprocess.run(['xdotool', 'mousemove', '--window', window_id,
                        str(x+width//2), str(y+height//2), 'click', '1'],
                       env=self.env, check=True, timeout=5)

    def accept(self, windows):
        if self.attempted or len(windows) != 1 or windows[0]['title'] != TITLE:
            raise RuntimeError('Agreement UI requires exactly one approved dialog and one attempt')
        self.attempted = True
        window_id = windows[0]['id']
        before = self.capture(window_id, 'agreement-before.png')
        label, confirm = acceptance_label(words(before))
        checkbox = unchecked_box(pixels(before), label, self.unchecked_template)
        self.click_word(window_id, confirm, 'acceptance_checkbox_label')
        for attempt in range(5):
            time.sleep(0.4)
            after = self.capture(window_id, f'agreement-checkbox-{attempt}.png')
            if not checked_box(region(pixels(after), *checkbox)):
                continue
            ocr = words(after)
            acceptance_label(ocr)
            continue_words = [w for w in ocr if w['text'] == 'Continue' and float(w['conf']) >= 80
                              and box(w)[1] > box(confirm)[1]]
            if len(continue_words) == 1:
                self.record('checked_checkbox_and_continue_observed')
                self.click_word(window_id, continue_words[0], 'Continue')
                self.record('agreement_continue_clicked')
                return
        raise RuntimeError('Checkbox checkmark/enabled Continue could not be verified; no further action')

    def decline_telemetry(self, windows):
        if (not self.attempted or self.telemetry_attempted or len(windows) != 1
                or windows[0]['title'] != TELEMETRY_TITLE):
            raise RuntimeError('Optional telemetry refusal requires the approved test and one exact dialog')
        self.telemetry_attempted = True
        window_id = windows[0]['id']
        before = self.capture(window_id, 'data-sharing-before.png')
        target = telemetry_decline_button(pixels(before), words(before))
        self.record('optional_telemetry_prompt_verified', body_sha256=TELEMETRY_BODY_SHA256,
                    default_policy='decline')
        self.click_word(window_id, target, "Don't Send", TELEMETRY_TITLE)
        self.record('telemetry_dont_send_clicked')

    def inspect_trial_options(self, windows):
        licenses = [w for w in windows if w['title'] == 'Licenses']
        if (not self.telemetry_attempted or self.trial_option_attempted or len(licenses) != 1
                or any(w['title'] not in {'Welcome to CLion', 'Licenses'} for w in windows)):
            raise RuntimeError('Trial inspection requires the approved test and audited startup windows')
        self.trial_option_attempted = True
        window_id = licenses[0]['id']
        before = self.capture(window_id, 'licenses-before-trial.png')
        target = trial_option(pixels(before), words(before))
        # This selects the radio option only. No login, OAuth, Start Trial grant, or activation button.
        self.click_word(window_id, target, 'Start trial radio option', 'Licenses')
        self.record('trial_option_selected')
        time.sleep(1)
        after = self.capture(window_id, 'trial-page.png')
        text = ' '.join(w['text'] for w in words(after))
        (self.output / 'trial-page.txt').write_text(text + '\n')
        self.record('trial_page_saved_without_login_or_activation')

    def start_trial_once(self, windows):
        licenses = [w for w in windows if w['title'] == 'Licenses']
        if (not self.trial_option_attempted or self.trial_start_attempted or len(licenses) != 1
                or any(w['title'] not in {'Welcome to CLion', 'Licenses'} for w in windows)):
            raise RuntimeError('Trial claim requires the approved inspected page and one attempt')
        # Durable before input: timeouts, process errors and observation loops must never repeat the claim.
        with (self.output / 'trial-start-once.json').open('x') as marker:
            marker.write('{"trial_claim_click_limit":1}\n')
        self.trial_start_attempted = True
        self.trial_start_time = time.monotonic()
        window_id = licenses[0]['id']
        before = self.capture(window_id, 'trial-claim-before.png')
        target = trial_start_button(pixels(before))
        self.click_word(window_id, target, 'Start Trial claim button', 'Licenses')
        self.record('official_trial_claim_clicked_once')
