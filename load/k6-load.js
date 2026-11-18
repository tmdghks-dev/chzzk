// 부하 스크립트: 가상 사용자 50명이 2분 동안 방송 목록을 계속 요청한다
import http from 'k6/http';

export const options = { vus: 50, duration: '2m' };

export default function () {
  http.get('http://chzzk/broadcasts');
}
