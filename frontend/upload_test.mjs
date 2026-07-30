// Huawei Cloud OBS 直传测试脚本（预签名 URL 方式）
// 用法：
//   node upload_test.mjs
// 环境变量：
//   BASE_URL - 后端地址，默认 http://localhost:8080/api
//   USERNAME - 用户名（必需）
//   PASSWORD - 密码（必需）
//   ORIGIN   - 前端地址，默认 http://localhost:3000

const baseUrl = process.env.BASE_URL || 'http://localhost:8080/api';
const username = process.env.USERNAME;
const password = process.env.PASSWORD;
const origin = process.env.ORIGIN || 'http://localhost:3000';

if (!username || !password) {
  console.error('Please set USERNAME and PASSWORD env vars.');
  process.exit(1);
}

const loginRes = await fetch(`${baseUrl}/auth/login`, {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ username, password })
});
const loginJson = await loginRes.json();

if (!loginJson?.success || !loginJson?.data?.token) {
  console.error('Login failed:', loginJson);
  process.exit(2);
}

const token = loginJson.data.token;
console.log('LOGIN_OK');

// 获取 OBS 预签名上传 URL
const sigRes = await fetch(`${baseUrl}/obs/upload-signature?fileExtension=txt&folder=video`, {
  method: 'GET',
  headers: {
    Authorization: `Bearer ${token}`,
    Origin: origin
  }
});
const sigJson = await sigRes.json();

if (!sigJson?.success || !sigJson?.data) {
  console.error('SIGNATURE_FAIL:', JSON.stringify(sigJson, null, 2));
  process.exit(3);
}

const s = sigJson.data;
console.log('SIGNATURE_OK', { bucket: s.bucket, endpoint: s.endpoint, objectName: s.objectName });

const content = Buffer.from('terminal upload check via OBS pre-signed URL');

try {
  const putRes = await fetch(s.signedUrl, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/octet-stream' },
    body: content
  });
  if (putRes.ok) {
    console.log('UPLOAD_OK');
    console.log('objectName:', s.objectName);
    console.log('obsUrl:', s.obsUrl);
  } else {
    console.error('UPLOAD_FAIL', putRes.status, putRes.statusText);
    process.exit(4);
  }
} catch (e) {
  console.error('UPLOAD_FAIL');
  console.error(e);
  process.exit(4);
}
