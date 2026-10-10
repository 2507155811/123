#!/usr/bin/env python3
"""Run Gradle in a restricted cloud workspace without changing HOME or TLS trust."""
import os
import subprocess
import sys
from pathlib import Path
from urllib.parse import urlsplit
root=Path(__file__).resolve().parents[1]
tools=Path('/workspace/toolchains')
env=os.environ.copy()
env.update(JAVA_HOME=str(tools/'jdk-21.0.12.1'), GRADLE_USER_HOME=str(tools/'gradle-cache'),
           ANDROID_HOME=str(tools/'android-sdk'), ANDROID_USER_HOME=str(tools/'android-user'))
env['PATH']=env['JAVA_HOME']+'/bin:'+env.get('PATH','')
args=[str(tools/'gradle-8.11.1/bin/gradle'),'--no-daemon','--max-workers=4',
      '-Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts',
      '-Dorg.gradle.jvmargs=-Xmx4g -Dfile.encoding=UTF-8 -Duser.home=/workspace/toolchains/java-home -Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts']
proxy=urlsplit(env.get('HTTPS_PROXY',''))
if proxy.hostname:
    if proxy.username or proxy.password: raise SystemExit('Authenticated proxy requires supported platform configuration')
    args.extend(['-Dhttps.proxyHost='+proxy.hostname,'-Dhttps.proxyPort='+str(proxy.port or 80),
                 '-Dhttp.proxyHost='+proxy.hostname,'-Dhttp.proxyPort='+str(proxy.port or 80)])
sys.exit(subprocess.call(args+sys.argv[1:],cwd=root,env=env))
