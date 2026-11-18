// 라이브 시그널링 서버 부하 테스트
// 휴대폰 대신 가짜 방송자 1명 + 가짜 시청자 다수가 /ws/live 에 접속해서
// viewer-joined → offer → answer/ice 쪽지가 서버를 거쳐 오가는지, 몇 명까지 버티는지 잰다.
// 영상은 오가지 않는다 (서버만 테스트).
import { WebSocket } from 'k6/websockets';
import { setTimeout } from 'k6/timers';
import { Trend, Rate, Counter } from 'k6/metrics';

const BASE = __ENV.BASE || 'ws://localhost:8091';
const LIVE_ID = __ENV.LIVE_ID;
const KEY = __ENV.KEY;
const HOLD_MS = 20000; // 시청자 한 명이 머무는 시간

const offerLatency = new Trend('offer_latency', true); // 접속 → offer 받기까지
const gotOffer = new Rate('got_offer');                 // offer 를 받은 시청자 비율
const relayed = new Counter('broadcaster_msgs');        // 방송자가 받은 쪽지 수

const STAGES = [
    { duration: '15s', target: 200 },
    { duration: '20s', target: 200 },
    { duration: '15s', target: 500 },
    { duration: '20s', target: 500 },
    { duration: '15s', target: 1000 },
    { duration: '20s', target: 1000 },
    { duration: '15s', target: 2000 },
    { duration: '20s', target: 2000 },
    { duration: '15s', target: 3000 },
    { duration: '20s', target: 3000 },
    { duration: '10s', target: 0 },
];
const TOTAL_S = 185;

export const options = {
    scenarios: {
        broadcaster: { executor: 'per-vu-iterations', exec: 'broadcaster', vus: 1, iterations: 1, maxDuration: `${TOTAL_S + 30}s` },
        viewers: { executor: 'ramping-vus', exec: 'viewer', startTime: '2s', startVUs: 0, stages: STAGES, gracefulRampDown: '25s' },
    },
    thresholds: {
        got_offer: ['rate>0.99'],
        offer_latency: ['p(95)<1000'],
    },
};

export function broadcaster() {
    const ws = new WebSocket(`${BASE}/ws/live?liveId=${LIVE_ID}&role=broadcaster&key=${KEY}`);
    ws.onmessage = (e) => {
        relayed.add(1);
        const msg = JSON.parse(e.data);
        if (msg.type === 'viewer-joined') {
            ws.send(JSON.stringify({ type: 'offer', to: msg.viewerId, sdp: { type: 'offer', sdp: 'fake-offer' } }));
            ws.send(JSON.stringify({ type: 'ice', to: msg.viewerId, candidate: { candidate: 'fake', sdpMid: '0' } }));
        }
    };
    ws.onerror = (e) => console.error('broadcaster error', e.error);
    setTimeout(() => ws.close(), (TOTAL_S + 25) * 1000);
}

export function viewer() {
    const start = Date.now();
    let received = false;
    const ws = new WebSocket(`${BASE}/ws/live?liveId=${LIVE_ID}&role=viewer`);
    ws.onmessage = (e) => {
        const msg = JSON.parse(e.data);
        if (msg.type === 'offer' && !received) {
            received = true;
            offerLatency.add(Date.now() - start);
            ws.send(JSON.stringify({ type: 'answer', sdp: { type: 'answer', sdp: 'fake-answer' } }));
            ws.send(JSON.stringify({ type: 'ice', candidate: { candidate: 'fake', sdpMid: '0' } }));
        }
    };
    ws.onclose = () => gotOffer.add(received);
    setTimeout(() => ws.close(), HOLD_MS);
}
