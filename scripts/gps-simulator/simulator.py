#!/usr/bin/env python3
"""Small configurable GPS load/replay simulator for provisioned tracking devices."""
import argparse, json, math, os, random, time, uuid
from datetime import datetime, timezone
from urllib.request import Request, urlopen

def route_points(route, count):
    if len(route) < 2 or count < 1:
        raise ValueError("A route needs at least two coordinates and one output point.")
    points=[]
    for i in range(count):
        progress=(i%count)*len(route)/count
        segment=int(progress)
        a,b=route[segment],route[(segment+1)%len(route)]
        f=progress-segment
        points.append((a[0]+(b[0]-a[0])*f,a[1]+(b[1]-a[1])*f))
    return points

def main():
    ap=argparse.ArgumentParser(); ap.add_argument('--url',default='http://localhost:8080/api/v1/tracking/positions'); ap.add_argument('--devices',type=int,default=1); ap.add_argument('--interval',type=float,default=5); ap.add_argument('--duration',type=int,default=60); ap.add_argument('--batch-size',type=int,default=1); ap.add_argument('--duplicate-rate',type=float,default=0); ap.add_argument('--interrupt-every',type=int,default=0); ap.add_argument('--interrupt-seconds',type=int,default=5); ap.add_argument('--load',action='store_true'); ap.add_argument('--route',default='40.7128,-74.0060;40.7306,-73.9352;40.6892,-74.0445'); a=ap.parse_args()
    route=[tuple(map(float,p.split(','))) for p in a.route.split(';')]; credentials=json.loads(os.environ.get('GPS_SIMULATOR_DEVICES','[]'))
    if len(credentials)<a.devices: raise SystemExit('Set GPS_SIMULATOR_DEVICES to a JSON array of {id,token} entries created by an operator.')
    start=time.monotonic(); sent=0; tick=0; pending=[]
    while time.monotonic()-start<a.duration:
        if a.interrupt_every and tick and tick%a.interrupt_every==0: time.sleep(a.interrupt_seconds)
        for d in credentials[:a.devices]:
            batch=[]
            replay=route_points(route,50)
            for offset in range(a.batch_size):
                lat,lon=replay[(tick*a.batch_size+offset)%len(replay)]; event=str(uuid.uuid4()); point={'eventId':event,'recordedAt':datetime.now(timezone.utc).isoformat(),'latitude':lat,'longitude':lon,'speedKph':round(random.uniform(15,78),1),'heading':round(random.uniform(0,359),1),'accuracyM':round(random.uniform(3,16),1),'batteryPercent':random.randint(40,100),'source':'SIMULATOR'}
                batch.append(point)
                if random.random()<a.duplicate_rate: batch.append(point)
            body=json.dumps({'positions':batch}).encode(); req=Request(a.url,data=body,headers={'Content-Type':'application/json','X-Device-Id':d['id'],'X-Device-Token':d['token']})
            try:
                with urlopen(req,timeout=5) as res: sent+=len(batch); print(res.status, d['id'], len(batch))
            except Exception as ex: print('request failed:',ex)
        tick+=1
        if not a.load: time.sleep(max(0,a.interval))
    elapsed=max(.001,time.monotonic()-start); print(json.dumps({'devices':a.devices,'durationSeconds':round(elapsed,2),'acceptedPositions':sent,'offeredPositionsPerSecond':round(sent/elapsed,2),'note':'Offered rate is simulator-side; verify processed throughput from backend metrics/database.'}))
if __name__=='__main__': main()
