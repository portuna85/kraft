import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

/**
 * `static/js/vendor/bootstrap.min.js`는 npm으로 받지 않고 직접 커밋한 외부 배포 자산이다
 * (F06) — `package-lock.json`이 고정하는 `node_modules/bootstrap` 버전과 별개로 관리되므로,
 * `npm update`로 lockfile의 버전을 올려도 이 파일은 조용히 뒤처질 수 있다. 벤더 파일 첫 줄의
 * 배너 주석(`/*! Bootstrap v5.3.8 ...`)에 적힌 버전과 설치된 패키지 버전을 비교해 어긋나면
 * 실패시킨다.
 *
 * 벤더 파일을 교체하는 절차: `node_modules/bootstrap/dist/js/bootstrap.min.js`(Popper가
 * 필요한 dropdown·tooltip·popover는 이 앱이 쓰지 않으므로 bundle 빌드가 아니다 —
 * `bootstrap-ui.js`가 감싸는 것은 Modal·Toast뿐이다)를 그대로 복사해 이 경로에 덮어쓰되,
 * 마지막 줄의 `//# sourceMappingURL=...` 주석은 소스맵 파일을 함께 커밋하지 않으므로
 * 지운다. 그 다음 이 스크립트를 다시 실행해 버전이 맞는지 확인한다.
 */
const __dirname = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(__dirname, '..');

const installedVersion = JSON.parse(
    readFileSync(join(repoRoot, 'node_modules/bootstrap/package.json'), 'utf8'),
).version;

const vendorPath = join(repoRoot, 'src/main/resources/static/js/vendor/bootstrap.min.js');
const vendorHeader = readFileSync(vendorPath, 'utf8').slice(0, 200);
const vendorVersionMatch = vendorHeader.match(/Bootstrap v(\d+\.\d+\.\d+)/);

if (!vendorVersionMatch) {
    console.error(`${vendorPath}: 첫 줄 배너 주석에서 버전을 찾을 수 없다.`);
    process.exit(1);
}

const vendorVersion = vendorVersionMatch[1];
if (vendorVersion !== installedVersion) {
    console.error(
        `${vendorPath}는 Bootstrap ${vendorVersion}이지만 package-lock.json은 `
        + `${installedVersion}을 고정한다. node_modules/bootstrap/dist/js/bootstrap.min.js로 `
        + '다시 복사한다.',
    );
    process.exit(1);
}

console.log(`vendor/bootstrap.min.js가 설치된 버전(${installedVersion})과 일치한다.`);
