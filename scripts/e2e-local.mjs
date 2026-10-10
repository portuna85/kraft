// 로컬 e2e: 낡은 jar로 도는 것을 막으려고 jar를 먼저 다시 만들고 chromium 프로젝트만 실행한다.
// 8081 포트에 남은 java 프로세스가 있으면 기동에 실패하니 먼저 종료해 둔다.
import { spawnSync } from 'node:child_process';

const windows = process.platform === 'win32';
const gradlew = windows ? 'gradlew.bat' : './gradlew';
const extraArgs = process.argv.slice(2);

function run(command, args) {
  const result = spawnSync(command, args, { stdio: 'inherit', shell: windows });
  if (result.status !== 0) {
    process.exit(result.status ?? 1);
  }
}

run(gradlew, ['bootE2eJar']);
run('npx', ['playwright', 'test', '--project=chromium', ...extraArgs]);
