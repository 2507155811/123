#!/usr/bin/env python3
"""Fetch the pinned GPL OpenVPN engine; use its minimal skeleton, OpenVPN 2 only."""
from pathlib import Path
import subprocess
root = Path(__file__).resolve().parents[1]
engine = root / '.engine'
pin = '9aee9b529a67cbc03dc10f9484d915b3440a2e81'
def git(*args): subprocess.run(['git', '-C', str(engine), *args], check=True)
if engine.exists() and not (engine / '.git').exists():
    marker = engine / 'AERO_SOURCE_REVISION'
    if marker.exists() and marker.read_text().strip() == pin:
        print('Using complete corresponding engine sources; preserving local files')
        raise SystemExit(0)
    raise SystemExit('Engine cache has no Git metadata; refusing to overwrite local files')
if not engine.exists():
    subprocess.run(['git', 'clone', '--depth', '1', '--branch', 'v0.7.62',
                    'https://github.com/schwabe/ics-openvpn.git', str(engine)], check=True)
actual = subprocess.check_output(['git', '-C', str(engine), 'rev-parse', 'HEAD'], text=True).strip()
if actual != pin: raise SystemExit('Unexpected engine revision; refusing to replace local sources')
git('submodule', 'update', '--init', '--depth', '1', 'main/src/main/cpp/openvpn',
    'main/src/main/cpp/openssl', 'main/src/main/cpp/lz4', 'main/src/main/cpp/fmt')
# Always derive the sole build override from the pinned original; idempotent.
original = subprocess.check_output(['git', '-C', str(engine), 'show', 'HEAD:main/src/main/cpp/CMakeLists.txt'], text=True)
condition = 'if (NOT ${CMAKE_LIBRARY_OUTPUT_DIRECTORY} MATCHES "build/intermediates/cmake/.*skeleton.*/")'
if original.count(condition) != 2: raise SystemExit('Unexpected native build file')
# Upstream skeleton excludes OpenVPN3/SWIG and the speed-test library. AGP's newer
# output directory no longer matches its old regex; choose that supported mode explicitly.
def override(relative, transform):
    source = subprocess.check_output(['git', '-C', str(engine), 'show', 'HEAD:' + relative], text=True)
    path = engine / relative
    expected = transform(source)
    if path.read_text() not in (source, expected):
        raise SystemExit('Local engine changes found; refusing to overwrite ' + relative)
    path.write_text(expected)
override('main/src/main/cpp/CMakeLists.txt', lambda source: source.replace(condition,
         'if (FALSE) # Aero: upstream skeleton mode, OpenVPN 2 only') +
         '\n# Android API 26 provides basename; omit the pre-API-23 fallback.\ntarget_compile_definitions(openvpn PRIVATE HAVE_BASENAME=1)\n')
override('main/src/main/java/de/blinkt/openvpn/core/OpenVPNService.java', lambda source: source.replace(
         '    private void installRoutesExcluded(', '    @androidx.annotation.RequiresApi(33)\n    private void installRoutesExcluded('))
override('main/src/main/java/de/blinkt/openvpn/core/StatusListener.java', lambda source: source.replace(
         '    private void logExitNotification(', '    @androidx.annotation.RequiresApi(30)\n    private void logExitNotification('))
override('main/src/main/java/de/blinkt/openvpn/core/OrbotHelper.java', lambda source: source.replace(
         'c.getApplicationContext().registerReceiver(orbotStatusReceiver,\n                    new IntentFilter(OrbotHelper.ACTION_STATUS));',
         'androidx.core.content.ContextCompat.registerReceiver(c.getApplicationContext(), orbotStatusReceiver,\n                    new IntentFilter(OrbotHelper.ACTION_STATUS), androidx.core.content.ContextCompat.RECEIVER_EXPORTED);'))
override('main/src/main/res/values/strings.xml', lambda source: source.replace(
         'Skipping address %1$s:%2$d for DNS server entry %1$d as uses a non-default port: %2$s',
         'Skipping address %1$s:%2$d for DNS server entry as it uses a non-default port'))
(engine / 'AERO_SOURCE_REVISION').write_text(pin + '\n')
print('Pinned embedded OpenVPN engine prepared')
