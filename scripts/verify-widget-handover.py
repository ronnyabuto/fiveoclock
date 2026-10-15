#!/usr/bin/env python3
"""Exercise a pinned widget's real alarm delivery on a dedicated emulator."""
import argparse
import json
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('serial', help='Dedicated emulator serial, for example emulator-5556')
parser.add_argument('--idle', action='store_true', help='Force Doze before the handover')
parser.add_argument('--kill-process', action='store_true', help='Kill the background app process before delivery')
parser.add_argument('--output', default='.local/handover-evidence')
args = parser.parse_args()
if not args.serial.startswith('emulator-'):
    parser.error('Use a dedicated emulator; this test temporarily changes its clock and idle state.')
output = Path(args.output)
output.mkdir(parents=True, exist_ok=True)


def adb(*parts):
    return subprocess.check_output(['adb', '-s', args.serial, *parts], text=True).strip()


def shell(*parts):
    return adb('shell', *parts)


def diagnostics():
    xml = shell('run-as', 'com.fivepm.clock', 'cat', 'shared_prefs/widget_diagnostics.xml')
    return {node.attrib['name']: node.attrib.get('value', node.text) for node in ET.fromstring(xml)}


def events():
    return shell('logcat', '-d', '-s', 'FivePmClock:I', '*:S')


def city(log):
    matches = re.findall(r'city=([^\r\n]+)', log)
    return matches[-1] if matches else None


original_time = int(shell('date', '+%s')) * 1000
started = time.monotonic()
auto_time = shell('settings', 'get', 'global', 'auto_time')
deep_idle = shell('dumpsys', 'deviceidle', 'enabled', 'deep')
result = {'idle': args.idle, 'serial': args.serial}
try:
    widgets = shell('dumpsys', 'appwidget')
    if not re.search(r'provider=ProviderId\{[^\n]*com\.fivepm\.clock/', widgets):
        raise RuntimeError('Pin a 5PM Clock widget before running this test.')
    shell('settings', 'put', 'global', 'auto_time', '0')
    shell('input', 'keyevent', 'WAKEUP')
    shell('wm', 'dismiss-keyguard')
    shell('am', 'start', '-W', '-n', 'com.fivepm.clock/.MainActivity')
    shell('input', 'keyevent', 'HOME')
    state = diagnostics()
    boundary = int(state['nextEvent'])
    if boundary <= 0:
        raise RuntimeError(f'No handover scheduled: {state}')
    shell('cmd', 'alarm', 'set-time', str(boundary - 20_000))
    # Wait for the system TIME_SET broadcast to recompute state before entering idle.
    for _ in range(40):
        before = diagnostics()
        if abs(int(before['updated']) - (boundary - 20_000)) < 10_000:
            break
        time.sleep(0.25)
    else:
        raise RuntimeError('TIME_SET did not update widget diagnostics.')
    result['before'] = before
    result['beforeCity'] = city(events())
    if args.kill_process:
        shell('am', 'kill', 'com.fivepm.clock')
        for _ in range(20):
            running = subprocess.run(['adb', '-s', args.serial, 'shell', 'pidof', 'com.fivepm.clock'],
                                     capture_output=True, text=True).stdout.strip()
            if not running:
                result['processKilled'] = True
                break
            time.sleep(0.25)
        else:
            raise RuntimeError('The app process did not exit; process-death delivery was not exercised.')
    alarm_state = shell('dumpsys', 'alarm')
    (output / 'before-alarm.txt').write_text(alarm_state)
    result['recoveryRegistered'] = re.search(
        r'tag=[^\n]*com\.fivepm\.clock\.RECOVERY\n[^\n]*repeatInterval=1800000', alarm_state) is not None
    if args.idle:
        shell('dumpsys', 'deviceidle', 'enable', 'deep')
        shell('dumpsys', 'battery', 'unplug')
        shell('input', 'keyevent', 'SLEEP')
        result['forceIdle'] = shell('dumpsys', 'deviceidle', 'force-idle')
        if 'forced' not in result['forceIdle'].lower() or 'unable' in result['forceIdle'].lower():
            raise RuntimeError(f"Could not enter idle: {result['forceIdle']}")
    deadline = time.monotonic() + 45
    while time.monotonic() < deadline:
        after = diagnostics()
        if after.get('reason') == 'com.fivepm.clock.ALARM' and int(after['updated']) >= boundary:
            break
        time.sleep(1)
    result['after'] = after
    log = events()
    (output / 'events.txt').write_text(log)
    (output / 'after-alarm.txt').write_text(shell('dumpsys', 'alarm'))
    result['afterCity'] = city(log)
    result['passed'] = (result['recoveryRegistered'] and after.get('reason') == 'com.fivepm.clock.ALARM'
                        and int(after['updated']) >= boundary
                        and int(after['nextEvent']) > int(after['updated'])
                        and result['beforeCity'] is not None
                        and result['beforeCity'] != result['afterCity'])
    if not result['passed']:
        raise AssertionError(f'Handover or recovery registration failed: {result}')
    print(json.dumps(result, indent=2))
finally:
    (output / 'result.json').write_text(json.dumps(result, indent=2) + '\n')
    shell('dumpsys', 'deviceidle', 'unforce')
    if deep_idle == '0':
        shell('dumpsys', 'deviceidle', 'disable', 'deep')
    shell('dumpsys', 'battery', 'reset')
    shell('cmd', 'alarm', 'set-time', str(original_time + int((time.monotonic() - started) * 1000)))
    if auto_time == 'null':
        shell('settings', 'delete', 'global', 'auto_time')
    else:
        shell('settings', 'put', 'global', 'auto_time', auto_time)
    shell('input', 'keyevent', 'WAKEUP')
