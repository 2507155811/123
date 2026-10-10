#!/usr/bin/env python3
"""Repeatable Android build toolchain installation with verified official archives."""
import concurrent.futures
import hashlib
import os
from pathlib import Path
import stat
import subprocess
import tarfile
import urllib.request
import zipfile
from urllib.parse import urlsplit
ROOT=Path('/workspace/toolchains')
SDK=ROOT/'android-sdk'
ROOT.mkdir(exist_ok=True);SDK.mkdir(exist_ok=True)
def download(url,path,sha,algorithm):
    urllib.request.urlretrieve(url,path)
    digest=hashlib.new(algorithm)
    with open(path,'rb') as file:
        for block in iter(lambda:file.read(1024*1024),b''):digest.update(block)
    if digest.hexdigest()!=sha:
        path.unlink();raise RuntimeError('Archive checksum mismatch: '+url)
def install_zip(url,sha,directory,prefix,marker,algorithm='sha1'):
    if (directory/marker).exists():return
    archive=ROOT/(directory.name+'.download.zip')
    download(url,archive,sha,algorithm)
    directory.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(archive) as z:
        for info in z.infolist():
            rel=info.filename[len(prefix):] if prefix and info.filename.startswith(prefix) else info.filename
            if not rel:continue
            out=directory/rel
            if not out.resolve().is_relative_to(directory.resolve()):raise RuntimeError('Unsafe archive entry')
            if info.is_dir():out.mkdir(parents=True,exist_ok=True);continue
            out.parent.mkdir(parents=True,exist_ok=True)
            mode=info.external_attr>>16
            if out.exists() or out.is_symlink():out.unlink()
            if stat.S_ISLNK(mode):out.symlink_to(z.read(info).decode())
            else:
                out.write_bytes(z.read(info))
                if mode:out.chmod(mode & 0o777)
    archive.unlink()
    if not (directory/marker).exists():raise RuntimeError('Archive layout mismatch')
    print('Installed verified '+str(directory),flush=True)
items=[
 ('https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip','d313adb7aedccf6cf0cfca51ec180f0059f5f8f8',SDK/'cmdline-tools/latest','cmdline-tools/','bin/sdkmanager'),
 ('https://dl.google.com/android/repository/platform-36_r02.zip','2c1a80dd4d9f7d0e6dd336ec603d9b5c55a6f576',SDK/'platforms/android-36','android-36/','android.jar'),
 ('https://dl.google.com/android/repository/build-tools_r35_linux.zip','2cfaa0bbb2336e9ec18ed3ecea84fa2e2af607bc',SDK/'build-tools/35.0.0','android-15/','aapt2'),
 ('https://dl.google.com/android/repository/android-ndk-r29-linux.zip','87e2bb7e9be5d6a1c6cdf5ec40dd4e0c6d07c30b',SDK/'ndk/29.0.14206865','android-ndk-r29/','toolchains/llvm/prebuilt/linux-x86_64/bin/clang-21'),
 ('https://dl.google.com/android/repository/cmake-3.22.1-linux.zip','fd0a48b4a758310df8c7aa51f59840ed48fe7ed8',SDK/'cmake/3.22.1','','bin/cmake'),
 ('https://services.gradle.org/distributions/gradle-8.11.1-bin.zip','f397b287023acdba1e9f6fc5ea72d22dd63669d59ed4a289a29b1a76eee151c6',ROOT/'gradle-8.11.1','gradle-8.11.1/','bin/gradle','sha256')]
with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:list(pool.map(lambda x:install_zip(*x),items))
jdk=ROOT/'jdk-21.0.12.1'
if not (jdk/'bin/javac').exists():
    archive=ROOT/'jdk.download.tar.gz'
    download('https://download.oracle.com/java/21/latest/jdk-21_linux-x64_bin.tar.gz',archive,'12f870b21301b42292558a3f872ce543affa2b86cb6458591c78388c41ddb111','sha256')
    with tarfile.open(archive) as t:t.extractall(ROOT,filter='data')
    archive.unlink()
    if not (jdk/'bin/javac').exists():raise RuntimeError('JDK version mismatch')
# Accept the Android toolchain agreements in the existing user-authorized setup.
env=os.environ.copy();env['JAVA_HOME']=str(jdk)
env['JAVA_TOOL_OPTIONS']='-Duser.home=/workspace/toolchains/java-home -Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts'
args=[str(SDK/'cmdline-tools/latest/bin/sdkmanager'),'--sdk_root='+str(SDK),'--licenses']
p=urlsplit(env.get('HTTPS_PROXY',''))
if p.hostname:args+=['--proxy=http','--proxy_host='+p.hostname,'--proxy_port='+str(p.port or 80)]
with open(ROOT/'sdk-license-acceptance.log','w') as log:
    subprocess.run(args,input='y\n'*30,text=True,env=env,stdout=log,stderr=subprocess.STDOUT,check=True)
repo=Path(__file__).resolve().parents[1]
(repo/'local.properties').write_text('sdk.dir='+str(SDK)+'\n')
print('Android cloud toolchain ready')
