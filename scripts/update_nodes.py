#!/usr/bin/env python3
"""Publish an unmodified public VPN Gate CSV with a verifiable retrieval timestamp."""
import argparse,time,urllib.request
from pathlib import Path
parser=argparse.ArgumentParser();parser.add_argument('--bundle',action='store_true');args=parser.parse_args()
url='https://www.vpngate.net/api/iphone/'
request=urllib.request.Request(url,headers={'User-Agent':'AeroVPN-directory-updater/1.2'})
with urllib.request.urlopen(request,timeout=30) as response:
    if response.status!=200:raise SystemExit('Node source did not return HTTP 200')
    content=response.read(12_000_001)
if len(content)>12_000_000 or b'#HostName,' not in content or b'OpenVPN_ConfigData_Base64' not in content:raise SystemExit('Invalid node directory; keeping existing files')
content=b'#AeroFetchedAt='+str(int(time.time()*1000)).encode()+b'\n'+content
root=Path(__file__).resolve().parents[1]
(root/'nodes/vpngate.csv').write_bytes(content)
if args.bundle:(root/'app/src/main/assets/vpngate.csv').write_bytes(content)
print('Saved public directory:',len(content),'bytes')
